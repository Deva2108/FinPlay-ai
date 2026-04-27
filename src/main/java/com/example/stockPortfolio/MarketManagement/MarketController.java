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

    private final FinnhubService finnhubService;
    private final YahooFinanceService yahooService;
    private final NewsApiService newsService;
    private final MarketAnalysisService marketAnalysisService;
    private final com.example.stockPortfolio.AiManagement.service.GeminiService geminiService;
    private final com.example.stockPortfolio.PortfolioManagement.PortfolioService portfolioService;
    private final com.example.stockPortfolio.UserManagement.UserService userService;

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
            Map<String, Object> quote = finnhubService.getStockQuote(symbol);
            return ResponseEntity.ok(ApiResponse.ok(mapToQuoteDTO(quote), "Quote fetched successfully"));
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
                        Map<String, Object> quote = finnhubService.getStockQuote(s);
                        return mapToQuoteDTO(quote);
                    } catch (Exception e) {
                        return StockQuoteDTO.builder()
                                .symbol(s.toUpperCase())
                                .price(java.math.BigDecimal.ZERO)
                                .changesPercentage(java.math.BigDecimal.ZERO)
                                .build();
                    }
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(results, "Quotes fetched successfully"));
    }

    @GetMapping("/news")
    public ResponseEntity<ApiResponse<List<StockNewsDTO>>> getNews(@RequestParam(defaultValue = "stock market") String query) {
        try {
            List<Map<String, Object>> news = newsService.getStockNews(query);
            List<StockNewsDTO> dtos = news.stream().map(this::mapToNewsDTO).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.ok(dtos, "News fetched successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(new ArrayList<>(), "News unavailable"));
        }
    }

    @GetMapping("/details")
    public ResponseEntity<ApiResponse<StockDetailsDTO>> getDetails(@RequestParam String symbol) {
        try {
            Map<String, Object> quote = finnhubService.getStockQuote(symbol);
            StockDetailsDTO details = StockDetailsDTO.builder()
                    .symbol((String) quote.get("symbol"))
                    .price(java.math.BigDecimal.valueOf(((Number) quote.get("price")).doubleValue()))
                    .changesPercentage(java.math.BigDecimal.valueOf(((Number) quote.get("changesPercentage")).doubleValue()))
                    .marketCap("2.85T")
                    .peRatio("32.4")
                    .dividendYield("0.65%")
                    .revenue("383.93B")
                    .high52(java.math.BigDecimal.valueOf(199.62))
                    .low52(java.math.BigDecimal.valueOf(164.08))
                    .build();
            
            return ResponseEntity.ok(ApiResponse.ok(details, "Stock details fetched successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Details unavailable"));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<StockSearchDTO>>> search(@RequestParam String q) {
        List<Map<String, Object>> results = finnhubService.searchStocks(q);
        List<StockSearchDTO> dtos = results.stream()
                .map(r -> StockSearchDTO.builder()
                        .symbol((String) r.get("symbol"))
                        .name((String) r.get("name"))
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, "Search results fetched successfully"));
    }

    @GetMapping("/chart")
    public ResponseEntity<ApiResponse<StockChartResponseDTO>> getChartData(@RequestParam String symbol) {
        try {
            List<Map<String, Object>> data = yahooService.getHistoricalData(symbol);
            StockChartResponseDTO response = StockChartResponseDTO.builder().chartData(data).build();
            return ResponseEntity.ok(ApiResponse.ok(response, "Chart data fetched successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(StockChartResponseDTO.builder().chartData(new ArrayList<>()).build(), "Chart data unavailable"));
        }
    }

    @GetMapping("/indices")
    public ResponseEntity<ApiResponse<List<StockQuoteDTO>>> getIndices(@RequestParam(defaultValue = "US") String marketType) {
        List<String> symbols = marketType.equalsIgnoreCase("INDIA") 
                ? List.of("^NSEI", "^BSESN", "^NSEBANK") 
                : List.of("SPY", "QQQ", "DIA");
        
        List<StockQuoteDTO> results = symbols.stream()
                .map(s -> {
                    try {
                        Map<String, Object> quote = finnhubService.getStockQuote(s);
                        StockQuoteDTO dto = mapToQuoteDTO(quote);
                        // Add some descriptive names for indices if missing
                        if (s.equals("^NSEI")) dto.setCompanyName("NIFTY 50");
                        if (s.equals("^BSESN")) dto.setCompanyName("SENSEX");
                        if (s.equals("^NSEBANK")) dto.setCompanyName("BANK NIFTY");
                        if (s.equals("SPY")) dto.setCompanyName("S&P 500");
                        if (s.equals("QQQ")) dto.setCompanyName("NASDAQ");
                        if (s.equals("DIA")) dto.setCompanyName("DOW JONES");
                        return dto;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(results, "Indices fetched successfully"));
    }

    @GetMapping("/index-insight")
    public ResponseEntity<ApiResponse<IndexInsightResponseDTO>> getIndexInsight(
            @RequestParam String symbol,
            @RequestParam String value,
            @RequestParam String change,
            @RequestParam(defaultValue = "US") String marketType) {
        
        String response = geminiService.getIndexInsight(symbol, value, change, marketType);
        
        String delimiter = "👀 Watch this:";
        String[] parts = response.split(delimiter);
        IndexInsightResponseDTO result = IndexInsightResponseDTO.builder()
                .explanation(parts[0].trim())
                .observation(parts.length > 1 ? parts[1].trim() : "Observe how major sectors react to this index level.")
                .build();
        
        return ResponseEntity.ok(ApiResponse.ok(result, "Index insight generated"));
    }

    @GetMapping("/vibe")
    public ResponseEntity<ApiResponse<MarketVibeResponseDTO>> getMarketVibe(@RequestParam(defaultValue = "INDIA") String marketType) {
        try {
            String vibe = geminiService.getMarketPulse(marketType);
            MarketVibeResponseDTO response = MarketVibeResponseDTO.builder()
                    .vibe(vibe != null ? vibe : "Market is finding direction.")
                    .build();
            return ResponseEntity.ok(ApiResponse.ok(response, "Market vibe generated"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(MarketVibeResponseDTO.builder().vibe("Market context currently syncing.").build(), "Vibe unavailable"));
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
        String insights = "Market volatility is moderate. Diversification is recommended.";
        List<StockNewsDTO> newsDTOs = new ArrayList<>();

        try {
            String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            com.example.stockPortfolio.UserManagement.User user = userService.getUserByEmail(email);
            
            if (portfolioId != null && portfolioId > 0) {
                try {
                    portfolioService.validatePortfolioOwnership(portfolioId, user.getUserId());
                    List<Map<String, Object>> holdings = portfolioService.getHoldingsByPortfolioId(portfolioId, user.getUserId());
                    List<String> symbols = holdings.stream().map(h -> (String) h.get("symbol")).collect(Collectors.toList());
                    List<Map<String, Object>> news = symbols.isEmpty() ? newsService.getStockNews("market") : newsService.getStockNews(symbols.get(0));
                    insights = geminiService.getMarketPulseInsights(symbols, news);
                    newsDTOs = news.stream().map(this::mapToNewsDTO).collect(Collectors.toList());
                } catch (Exception e) {
                    log.warn("Pulse failed for portfolio {}: {}", portfolioId, e.getMessage());
                }
            } else {
                List<Map<String, Object>> news = newsService.getStockNews("market");
                newsDTOs = news.stream().map(this::mapToNewsDTO).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Global pulse error: {}", e.getMessage());
        }
        
        MarketPulseResponseDTO response = MarketPulseResponseDTO.builder()
                .insights(insights)
                .news(newsDTOs)
                .build();
        return ResponseEntity.ok(ApiResponse.ok(response, "Market pulse returned with safety"));
    }

    private MarketDataDTO mapToMarketDataDTO(Map<String, Object> map) {
        return MarketDataDTO.builder()
                .symbol((String) map.get("symbol"))
                .price(java.math.BigDecimal.valueOf(((Number) map.get("price")).doubleValue()))
                .changesPercentage(java.math.BigDecimal.valueOf(((Number) map.get("changesPercentage")).doubleValue()))
                .sector((String) map.get("sector"))
                .marketCap((String) map.get("marketCap"))
                .build();
    }

    private StockQuoteDTO mapToQuoteDTO(Map<String, Object> map) {
        return StockQuoteDTO.builder()
                .symbol((String) map.get("symbol"))
                .price(java.math.BigDecimal.valueOf(((Number) map.get("price")).doubleValue()))
                .changesPercentage(java.math.BigDecimal.valueOf(((Number) map.get("changesPercentage")).doubleValue()))
                .companyName((String) map.getOrDefault("companyName", ""))
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
}
