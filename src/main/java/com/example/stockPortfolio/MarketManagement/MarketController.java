package com.example.stockPortfolio.MarketManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/market")
@Tag(name = "6. Market", description = "Real-time Market Data via Hybrid API System")
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class MarketController {

    private final MarketAnalysisService marketAnalysisService;
    private final com.example.stockPortfolio.PortfolioManagement.PortfolioService portfolioService;
    private final com.example.stockPortfolio.UserManagement.UserService userService;
    private final MarketGateway marketGateway;
    private final ExternalMarketDataGateway externalMarketDataGateway;
    private final com.example.stockPortfolio.AiManagement.service.AiService aiService;
    private final SymbolNormalizer symbolNormalizer;
    private final GoogleSheetsService googleSheetsService;
    private final StockUniverseRepo stockUniverseRepo;

    private final java.util.concurrent.atomic.AtomicLong lastLazyDiscoveryMs = new java.util.concurrent.atomic.AtomicLong(0);
    private static final long LAZY_DISCOVERY_COOLDOWN_MS = 30_000;

    @GetMapping("/gainers")
    public ResponseEntity<ApiResponse<List<MarketDataDTO>>> getGainers(@RequestParam(required = false) String cap, @RequestParam(required = false) String sector) {
        List<Map<String, Object>> data = marketAnalysisService.getGainers(cap, sector);
        List<MarketDataDTO> dtos = data.stream().map(this::mapToMarketDataDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, "Gainers fetched successfully"));
    }

    @GetMapping("/losers")
    public ResponseEntity<ApiResponse<List<MarketDataDTO>>> getLosers(@RequestParam(required = false) String cap, @RequestParam(required = false) String sector) {
        List<Map<String, Object>> data = marketAnalysisService.getLosers(cap, sector);
        List<MarketDataDTO> dtos = data.stream().map(this::mapToMarketDataDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, "Losers fetched successfully"));
    }

    @GetMapping("/sector")
    public ResponseEntity<ApiResponse<List<MarketDataDTO>>> getBySector(@RequestParam(required = false) String name) {
        List<Map<String, Object>> data = marketAnalysisService.getBySector(name);
        List<MarketDataDTO> dtos = data.stream().map(this::mapToMarketDataDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, "Sector data fetched successfully"));
    }

    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<MarketDataDTO>>> getTrending() {
        try {
            List<Map<String, Object>> data = marketAnalysisService.getTrending();
            List<MarketDataDTO> dtos = data.stream().map(this::mapToMarketDataDTO).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.ok(dtos, "Trending stocks fetched successfully"));
        } catch (Exception e) {
            log.error("Error fetching trending stocks: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(new ArrayList<>(), "Fall back to empty trending list"));
        }
    }

    @GetMapping("/quote")
    public ResponseEntity<ApiResponse<StockQuoteDTO>> getQuote(@RequestParam String symbol) {
        try {
            ApiResponse<Map<String, Object>> response = marketGateway.getLatestQuote(symbol);
            
            // Map raw data to DTO if available
            StockQuoteDTO data = response.getData() != null ? mapToQuoteDTO(response.getData()) : null;
            
            return ResponseEntity.ok(ApiResponse.<StockQuoteDTO>builder()
                    .success(true)
                    .data(data)
                    .meta(response.getMeta())
                    .build());
        } catch (Exception e) {
            log.error("Error fetching quote for {}: {}", symbol, e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("Quote unavailable"));
        }
    }

    @GetMapping("/quotes")
    public ResponseEntity<ApiResponse<List<StockQuoteDTO>>> getQuotes(@RequestParam List<String> symbols) {
        List<StockQuoteDTO> results = symbols.stream()
                .map(s -> {
                    try {
                        ApiResponse<Map<String, Object>> response = marketGateway.getLatestQuote(s);
                        return response.getData() != null ? mapToQuoteDTO(response.getData()) : null;
                    } catch (Exception e) {
                        log.warn("Skipping quote fetch for {}: {}", s, e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(results, "Quotes fetched from local mirror"));
    }

    @GetMapping("/news")
    public ResponseEntity<ApiResponse<List<StockNewsDTO>>> getNews(@RequestParam(defaultValue = "stock market") String query) {
        try {
            ApiResponse<List<Map<String, Object>>> response = marketGateway.getLatestNews(query);
            
            List<StockNewsDTO> dtos = response.getData().stream().map(this::mapToNewsDTO).collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.<List<StockNewsDTO>>builder()
                    .success(true)
                    .data(dtos)
                    .meta(response.getMeta())
                    .build());
        } catch (Exception e) {
            log.warn("News fetch failed for {}: {}", query, e.getMessage());
            return ResponseEntity.status(500).body(ApiResponse.error("News feed is currently unavailable."));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<StockSearchDTO>>> search(@RequestParam String q) {
        // 1. Use local search from mirror
        List<Map<String, Object>> results = marketGateway.searchLocalMirror(q);
        
        // 2. LAZY DISCOVERY: If empty, try external search
        if (results.isEmpty() && q.length() >= 2) {
            log.info("Lazy Discovery triggered for: {}", q);
            try {
                // Currently externalMarketDataGateway uses Finnhub for search
                List<Map<String, Object>> externalResults = externalMarketDataGateway.searchStocks(q);
                
                if (externalResults != null && !externalResults.isEmpty()) {
                    // Save the top result to our universe to track it permanently
                    Map<String, Object> topResult = externalResults.get(0);
                    String symbol = (String) topResult.get("symbol");
                    String name = (String) topResult.getOrDefault("name", symbol);
                    
                    // Basic heuristic for Market and Sector for newly discovered stocks
                    String market = symbol.endsWith(".NS") || symbol.endsWith(".BSE") ? "INDIA" : "US";
                    String sector = "Other"; 
                    String marketCap = "Unknown";
                    
                    if (stockUniverseRepo.findBySymbol(symbol).isEmpty()) {
                        StockUniverse newStock = StockUniverse.builder()
                            .symbol(symbol)
                            .name(name)
                            .sector(sector)
                            .market(market)
                            .marketCap(marketCap)
                            .isIndex(false)
                            .build();
                        stockUniverseRepo.save(newStock);
                        symbolNormalizer.refreshCache();
                        log.info("Added new stock to universe via Lazy Discovery: {}", symbol);
                        
                        // Mark as priority so the background scheduler fetches its price immediately
                        marketGateway.markAsPriority(symbol);
                    }
                    
                    // Serve the external results directly this time
                    results = externalResults;
                }
            } catch (Exception e) {
                log.warn("Lazy Discovery failed for {}: {}", q, e.getMessage());
            }
        }

        List<StockSearchDTO> dtos = results.stream()
                .map(r -> StockSearchDTO.builder()
                        .symbol((String) r.get("symbol"))
                        .name((String) r.getOrDefault("name", r.get("symbol")))
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, "Search results fetched"));
    }


    private final ChartService chartService;

    @GetMapping("/chart")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChartData(
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "1M") String timeframe) {
        try {
            Map<String, Object> chart = chartService.getChartData(symbol, timeframe);
            // Shallow copy so we never mutate the @Cacheable map ChartService returns.
            Map<String, Object> response = new HashMap<>(chart);

            // FIX 2 — optional live "currentPoint" so the chart's latest visible
            // point matches the header price. Read-only from the existing quote
            // mirror; NOT written to stock_history (no DB row mutation) and NOT
            // cached (lives only on this per-request copy). No provider/scheduler change.
            try {
                ApiResponse<Map<String, Object>> quote = marketGateway.getStockQuote(symbol);
                if (quote != null && quote.isSuccess() && quote.getData() != null
                        && quote.getData().get("price") instanceof Number) {
                    Object price = quote.getData().get("price");
                    Map<String, Object> currentPoint = new HashMap<>();
                    currentPoint.put("date", java.time.LocalDate.now().toString());
                    currentPoint.put("value", price);
                    currentPoint.put("close", price);
                    currentPoint.put("live", true);
                    response.put("currentPoint", currentPoint);
                }
            } catch (Exception ignore) {
                // currentPoint is optional — never fail the chart because a live quote missed.
            }

            return ResponseEntity.ok(ApiResponse.ok(response, "Historical data loaded"));
        } catch (Exception e) {
            log.error("Error fetching chart data for {} {}: {}", symbol, timeframe, e.getMessage());
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("symbol", symbol);
            errorResp.put("data", new ArrayList<>());
            return ResponseEntity.ok(ApiResponse.ok(errorResp, "Chart data unavailable"));
        }
    }

    @GetMapping("/details")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStockDetails(@RequestParam String symbol) {
        try {
            String normalized = symbolNormalizer.normalize(symbol);
            if (normalized == null) return ResponseEntity.badRequest().body(ApiResponse.error("Invalid symbol"));

            // 1. Get Live Price from Mirror
            ApiResponse<Map<String, Object>> quoteResp = marketGateway.getStockQuote(normalized);
            if (quoteResp == null || !quoteResp.isSuccess()) {
                return ResponseEntity.ok(quoteResp);
            }

            // 2. Get Financials from Redis (populated by Google Sheets)
            ApiResponse<Map<String, Object>> financialsResp = marketGateway.getFinancials(normalized);
            if (financialsResp == null || !financialsResp.isSuccess()) {
                return ResponseEntity.ok(financialsResp);
            }

            // Extract data after successful checks
            Map<String, Object> quote = quoteResp.getData();
            Map<String, Object> financials = financialsResp.getData();

            // 3. Aggregate
            Map<String, Object> details = new HashMap<>();
            details.put("symbol", normalized);
            details.put("price", quote != null ? quote.get("price") : 0.0);
            details.put("change", quote != null ? quote.get("changesPercentage") : 0.0);
            details.put("financials", financials);
            
            // 4. Mentor Line (Fallback or precomputed)
            String mentorLine = marketGateway.getPrecomputedInsight("mentor", normalized);
            if (mentorLine == null) {
                mentorLine = "Analyzing " + normalized + " based on its current market position and financial ratios.";
            }
            details.put("mentorLine", mentorLine);

            return ResponseEntity.ok(ApiResponse.ok(details, "Stock details fetched successfully"));
        } catch (Exception e) {
            log.error("Error in getStockDetails for {}: {}", symbol, e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("Failed to load details"));
        }
    }
    @GetMapping("/indices")
    public ResponseEntity<ApiResponse<List<StockQuoteDTO>>> getIndices(@RequestParam(defaultValue = "US") String marketType) {
        List<String> symbols = marketType.equalsIgnoreCase("INDIA") 
                ? List.of("^NSEI", "^BSESN", "^NSEBANK") 
                : List.of("SPY", "QQQ", "DIA");
        
        Map<String, Map<String, Object>> quotes = marketGateway.getBatchQuotes(symbols);

        List<StockQuoteDTO> results = symbols.stream()
                .map(s -> {
                    Map<String, Object> quote = quotes.get(s);
                    if (quote == null) return null;
                    
                    StockQuoteDTO dto = mapToQuoteDTO(quote);
                    // Add descriptive names for indices
                    if (s.equals("^NSEI")) dto.setCompanyName("NIFTY 50");
                    if (s.equals("^BSESN")) dto.setCompanyName("SENSEX");
                    if (s.equals("^NSEBANK")) dto.setCompanyName("BANK NIFTY");
                    if (s.equals("SPY")) dto.setCompanyName("S&P 500");
                    if (s.equals("QQQ")) dto.setCompanyName("NASDAQ");
                    if (s.equals("DIA")) dto.setCompanyName("DOW JONES");
                    return dto;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(results, "Indices fetched from local mirror", "cache"));
    }

    @GetMapping("/index-insight")
    public ResponseEntity<ApiResponse<IndexInsightResponseDTO>> getIndexInsight(
            @RequestParam String symbol,
            @RequestParam String value,
            @RequestParam String change,
            @RequestParam(defaultValue = "US") String marketType) {
        
        // READ PRECOMPUTED FROM MIRROR
        String responseText = marketGateway.getPrecomputedInsight("index", symbol);

        if (responseText == null || responseText.isBlank()) {
            com.example.stockPortfolio.AiManagement.RichInsightDTO richFallback = aiService.getDefaultRichInsight(symbol, change, "OBSERVE", "neutral");
            IndexInsightResponseDTO fallback = IndexInsightResponseDTO.builder()
                    .explanation(richFallback.getWhatHappened())
                    .observation(richFallback.getWhatYouCanLearn())
                    .richInsight(richFallback)
                    .build();
            return ResponseEntity.ok(ApiResponse.syncing(fallback, "Insight is being prepared...", "fallback"));
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.example.stockPortfolio.AiManagement.RichInsightDTO richInsight = mapper.readValue(responseText, com.example.stockPortfolio.AiManagement.RichInsightDTO.class);
            
            IndexInsightResponseDTO result = IndexInsightResponseDTO.builder()
                    .explanation(richInsight.getWhatHappened())
                    .observation(richInsight.getWhatYouCanLearn())
                    .richInsight(richInsight)
                    .build();
            
            return ResponseEntity.ok(ApiResponse.ok(result, "Rich index insight fetched from cache", "cache"));
        } catch (Exception e) {
            String delimiter = "👀 Watch this:";
            String[] parts = responseText.split(delimiter);
            IndexInsightResponseDTO result = IndexInsightResponseDTO.builder()
                    .explanation(parts[0].trim())
                    .observation(parts.length > 1 ? parts[1].trim() : "Observe how major sectors react to this index level.")
                    .build();
            
            return ResponseEntity.ok(ApiResponse.ok(result, "Index insight fetched from cache (legacy format)", "cache"));
        }
    }

    @GetMapping("/vibe")
    public ResponseEntity<ApiResponse<MarketVibeResponseDTO>> getMarketVibe(@RequestParam(defaultValue = "INDIA") String marketType) {
        try {
            // READ PRECOMPUTED FROM MIRROR
            String vibeText = marketGateway.getPrecomputedInsight("market", "vibe:" + marketType);
            
            if (vibeText == null || vibeText.isBlank()) {
                com.example.stockPortfolio.AiManagement.RichInsightDTO richFallback = aiService.getDefaultRichInsight(marketType + " Market", "stable", "OBSERVE", "neutral");
                MarketVibeResponseDTO fallback = MarketVibeResponseDTO.builder()
                        .simpleVibe(richFallback.getWhatHappened())
                        .richInsight(richFallback)
                        .build();
                return ResponseEntity.ok(ApiResponse.syncing(fallback, "Vibe is being prepared...", "fallback"));
            }

            MarketVibeResponseDTO.MarketVibeResponseDTOBuilder builder = MarketVibeResponseDTO.builder();
            
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.example.stockPortfolio.AiManagement.RichInsightDTO richInsight = mapper.readValue(vibeText, com.example.stockPortfolio.AiManagement.RichInsightDTO.class);
                builder.richInsight(richInsight).simpleVibe(richInsight.getWhatHappened());
            } catch (Exception e) {
                builder.simpleVibe(vibeText);
            }
            
            MarketVibeResponseDTO response = builder.build();

            return ResponseEntity.ok(ApiResponse.ok(response, "Market vibe fetched from cache", "cache"));
        } catch (Exception e) {
            log.warn("Failed to load market vibe for {}: {}", marketType, e.getMessage());
            com.example.stockPortfolio.AiManagement.RichInsightDTO richFallback = aiService.getDefaultRichInsight(marketType + " Market", "stable", "OBSERVE", "neutral");
            MarketVibeResponseDTO fallback = MarketVibeResponseDTO.builder()
                    .simpleVibe(richFallback.getWhatHappened())
                    .richInsight(richFallback)
                    .build();
            return ResponseEntity.ok(ApiResponse.syncing(fallback, "Vibe is being prepared...", "fallback"));
        }
    }

    @GetMapping("/insights/famous")
    public ResponseEntity<ApiResponse<List<InvestorInsightDTO>>> getFamousInsights(@RequestParam(required = false) String symbol) {
        List<Map<String, String>> data = marketAnalysisService.getFamousInsights(symbol);
        List<InvestorInsightDTO> dtos = data.stream().map(m -> InvestorInsightDTO.builder()
                .investor(m.get("investor"))
                .stock(m.get("stock"))
                .title(m.get("title"))
                .podcastUrl(m.get("podcastUrl"))
                .message(m.get("message"))
                .build()).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, "Famous insights fetched successfully"));
    }

    @GetMapping("/pulse")
    public ResponseEntity<ApiResponse<MarketPulseResponseDTO>> getMarketPulse(@RequestParam(required = false) Long portfolioId) {
        String insights = null;
        List<StockNewsDTO> newsDTOs = new ArrayList<>();

        try {
            String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            com.example.stockPortfolio.UserManagement.User user = userService.getUserByEmail(email);
            
            if (portfolioId != null && portfolioId > 0) {
                // READ PRECOMPUTED FROM GATEWAY
                insights = (String) marketGateway.getUserInsight(user.getUserId(), "pulse:" + portfolioId);
                
                // Fetch news from mirror
                List<Map<String, Object>> holdings = portfolioService.getHoldingsByPortfolioId(portfolioId, user.getUserId());
                String query = holdings.isEmpty() ? "market" : (String) holdings.get(0).get("symbol");
                ApiResponse<List<Map<String, Object>>> newsResponse = marketGateway.getLatestNews(query);
                if (newsResponse.getData() != null) {
                    newsDTOs = newsResponse.getData().stream().map(this::mapToNewsDTO).collect(Collectors.toList());
                }
            } else {
                ApiResponse<List<Map<String, Object>>> newsResponse = marketGateway.getLatestNews("market");
                if (newsResponse.getData() != null) {
                    newsDTOs = newsResponse.getData().stream().map(this::mapToNewsDTO).collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.error("Pulse error: {}", e.getMessage());
        }
        
        MarketPulseResponseDTO response = MarketPulseResponseDTO.builder()
                .insights(insights != null ? insights : "Market pulse is syncing with your portfolio.")
                .news(newsDTOs)
                .build();

        ApiResponse.Meta meta = ApiResponse.Meta.builder()
                .status(insights != null ? "OK" : "SYNCING")
                .source(insights != null ? "cache" : "fallback")
                .lastUpdated(java.time.LocalDateTime.now())
                .build();

        return ResponseEntity.ok(ApiResponse.<MarketPulseResponseDTO>builder()
                .success(true)
                .data(response)
                .meta(meta)
                .build());
    }

    private MarketDataDTO mapToMarketDataDTO(Map<String, Object> map) {
        String symbol = (String) map.get("symbol");
        return MarketDataDTO.builder()
                .symbol(symbol)
                .price(java.math.BigDecimal.valueOf(((Number) map.get("price")).doubleValue()))
                .changesPercentage(java.math.BigDecimal.valueOf(((Number) map.get("changesPercentage")).doubleValue()))
                .sector((String) map.get("sector"))
                .marketCap((String) map.get("marketCap"))
                .market(marketGateway.getSymbolNormalizer().isIndian(symbol) ? "IN" : "US")
                .build();
    }

    private StockQuoteDTO mapToQuoteDTO(Map<String, Object> map) {
        String symbol = (String) map.get("symbol");
        return StockQuoteDTO.builder()
                .symbol(symbol)
                .price(java.math.BigDecimal.valueOf(((Number) map.get("price")).doubleValue()))
                .changesPercentage(java.math.BigDecimal.valueOf(((Number) map.get("changesPercentage")).doubleValue()))
                .companyName((String) map.getOrDefault("companyName", ""))
                .market(marketGateway.getSymbolNormalizer().isIndian(symbol) ? "IN" : "US")
                .build();
    }

    private StockNewsDTO mapToNewsDTO(Map<String, Object> map) {
        return StockNewsDTO.builder()
                .headline((String) map.get("headline"))
                .source((String) map.get("source"))
                .datetime((String) map.get("datetime"))
                .url((String) map.get("url"))
                .build();
    }

    private IndexInsightResponseDTO buildIndexInsightFallback() {
        return IndexInsightResponseDTO.builder()
                .explanation("Index insight is syncing right now. Please check back shortly.")
                .observation("Observe how major sectors react to this index level.")
                .build();
    }

    private MarketVibeResponseDTO buildMarketVibeFallback() {
        return MarketVibeResponseDTO.builder()
                .simpleVibe("Market vibe is syncing right now. Please check back shortly.")
                .build();
    }
}
