package com.example.stockPortfolio.MarketManagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import com.example.stockPortfolio.AiManagement.ExplainRequestDTO;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MarketDataScheduler {

    private final FinnhubService finnhubService;
    private final NewsApiService newsApiService;
    private final MarketGateway marketGateway;
    private final SymbolNormalizer symbolNormalizer;
    private final com.example.stockPortfolio.HoldingsManagement.HoldingRepo holdingRepo;
    private final com.example.stockPortfolio.AlertManagement.AlertRepo alertRepo;

    private final com.example.stockPortfolio.AiManagement.service.AiService aiService;
    private final com.example.stockPortfolio.PortfolioManagement.PortfolioRepo portfolioRepo;
    private final com.example.stockPortfolio.UserManagement.UserRepo userRepo;
    
    private final ExecutorService marketHydrationExecutor;
    private final ExecutorService aiPrecomputationExecutor;
    
    private final ExternalMarketDataGateway externalMarketDataGateway;
    private final MarketStatusService marketStatusService;
    private final GoogleSheetsService googleSheetsService;
    private final StockUniverseRepo stockUniverseRepo;
    
    private int highPriorityIndex = 0;
    private int normalIndex = 0;
    private int chartCycleCounter = 0;
    private static final int CHART_UPDATE_FREQUENCY = 5;
    private static final int BATCH_SIZE = 12;

    public MarketDataScheduler(FinnhubService finnhubService, NewsApiService newsApiService, 
                               MarketGateway marketGateway, 
                               SymbolNormalizer symbolNormalizer, 
                               com.example.stockPortfolio.HoldingsManagement.HoldingRepo holdingRepo, 
                               com.example.stockPortfolio.AlertManagement.AlertRepo alertRepo, 
                               com.example.stockPortfolio.AiManagement.service.AiService aiService, 
                               com.example.stockPortfolio.PortfolioManagement.PortfolioRepo portfolioRepo, 
                               com.example.stockPortfolio.UserManagement.UserRepo userRepo,
                               ExternalMarketDataGateway externalMarketDataGateway,
                               MarketStatusService marketStatusService,
                               GoogleSheetsService googleSheetsService,
                               StockUniverseRepo stockUniverseRepo) {
        this.finnhubService = finnhubService;
        this.newsApiService = newsApiService;
        this.marketGateway = marketGateway;
        this.symbolNormalizer = symbolNormalizer;
        this.holdingRepo = holdingRepo;
        this.alertRepo = alertRepo;
        this.aiService = aiService;
        this.portfolioRepo = portfolioRepo;
        this.userRepo = userRepo;
        this.externalMarketDataGateway = externalMarketDataGateway;
        this.marketStatusService = marketStatusService;
        this.googleSheetsService = googleSheetsService;
        this.stockUniverseRepo = stockUniverseRepo;
        
        this.marketHydrationExecutor = new ThreadPoolExecutor(
                10, 20, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.aiPrecomputationExecutor = new ThreadPoolExecutor(
                3, 5, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @jakarta.annotation.PreDestroy
    public void shutdownExecutors() {
        log.info("Shutting down MarketDataScheduler executors...");
        marketHydrationExecutor.shutdown();
        aiPrecomputationExecutor.shutdown();
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ForexService forexService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.stockPortfolio.ContentManagement.ContentService contentService;

    @Scheduled(fixedRate = 1800000, initialDelay = 60000) // first run 60 s after startup
    public void precomputeAiInsights() {
        log.info("Starting AI Insight precomputation cycle...");
        for (String marketType : List.of("INDIA", "US")) {
            aiPrecomputationExecutor.submit(() -> {
                try {
                    String prompt = String.format(
                        "Return ONLY valid JSON matching this schema: {whatHappened, whyItMatters, globalImpact, indiaImpact, whatYouCanLearn, analogy, investorPerspective, action, confidence}. " +
                        "Act as a professional market analyst for the %s market.",
                        marketType
                    );
                    String vibe = aiService.generateRichInsight(prompt);
                    if (vibe != null && vibe.trim().startsWith("{")) {
                        marketGateway.updatePrecomputedInsight("market", "vibe:" + marketType, vibe);
                    }
                } catch (Exception e) {
                    log.error("Failed to precompute vibe for {}: {}", marketType, e.getMessage());
                }
            });
        }
        
        List<StockUniverse> indices = stockUniverseRepo.findByIsIndexTrue();
        for (StockUniverse idx : indices) {
            aiPrecomputationExecutor.submit(() -> {
                try {
                    ApiResponse<Map<String, Object>> quoteResp = marketGateway.getLatestQuote(idx.getSymbol());
                    if (quoteResp != null && quoteResp.getData() != null) {
                        Map<String, Object> quote = quoteResp.getData();
                        String prompt = String.format("Generate index insight for %s at %s.", idx.getName(), quote.get("price"));
                        String insight = aiService.generateRichInsight(prompt);
                        if (insight != null) marketGateway.updatePrecomputedInsight("index", idx.getSymbol(), insight);
                    }
                } catch (Exception e) {
                    log.error("Failed to precompute insight for {}: {}", idx.getSymbol(), e.getMessage());
                }
            });
        }
    }

    @Scheduled(fixedRate = 60000, initialDelay = 30000) // first run 30 s after startup
    public void hydrateMarketMirror() {
        Set<String> urgentSymbols = marketGateway.getPrioritySymbols();
        boolean marketOpen = marketStatusService.isAnyMarketOpen();

        if (!marketOpen && urgentSymbols.isEmpty()) return;

        // 1. COLLECT TARGETS (Priorities + Holdings + Universe)
        Set<String> targets = new LinkedHashSet<>();
        
        // Always include urgent symbols first
        urgentSymbols.forEach(s -> {
            String n = symbolNormalizer.normalize(s);
            if (n != null) targets.add(n);
        });

        // Collect all potential symbols
        List<String> allSymbols = new ArrayList<>();
        try {
            holdingRepo.findAllDistinctSymbols().forEach(s -> {
                String n = symbolNormalizer.normalize(s);
                if (n != null && !targets.contains(n)) allSymbols.add(n);
            });
        } catch (Exception e) {
            log.warn("Failed to fetch holdings: {}", e.getMessage());
        }
        
        List<String> universe = getUniverse();
        universe.forEach(s -> {
            if (!targets.contains(s) && !allSymbols.contains(s)) {
                allSymbols.add(s);
            }
        });

        // 2. APPLY BATCH ROTATION
        int batchSize = 10;
        int remainingSlots = batchSize - targets.size();
        
        if (remainingSlots > 0 && !allSymbols.isEmpty()) {
            if (normalIndex >= allSymbols.size()) {
                normalIndex = 0;
            }
            
            int endIndex = Math.min(normalIndex + remainingSlots, allSymbols.size());
            targets.addAll(allSymbols.subList(normalIndex, endIndex));
            normalIndex = endIndex;
        }

        List<String> symbolsToFetch = new ArrayList<>(targets);
        if (symbolsToFetch.isEmpty()) return;

        log.info("Starting batch market hydration for {} symbols...", symbolsToFetch.size());

        try {
            // 3. FETCH & UPDATE MIRROR
            Map<String, Map<String, Object>> quotes = externalMarketDataGateway.fetchTwelveDataBatch(symbolsToFetch);
            
            // 4. FALLBACK: If batch returns nothing (limits/outage), try individual fallback chain
            if (quotes.isEmpty()) {
                log.info("Batch fetch returned 0 results. Falling back to individual provider chain.");
                for (String s : symbolsToFetch) {
                    Map<String, Object> q = externalMarketDataGateway.fetchQuoteWithFallback(s);
                    if (q != null) marketGateway.updateMirror(s, q);
                }
            } else {
                quotes.forEach(marketGateway::updateMirror);
            }
            
            // 5. CLEANUP
            marketGateway.clearPrioritySymbols(symbolsToFetch);
            log.info("Successfully updated mirror for {} symbols.", Math.max(quotes.size(), 0));
            
        } catch (Exception e) {
            log.error("Batch hydration failed: {}", e.getMessage());
        }
    }

    private List<String> getUniverse() {
        return stockUniverseRepo.findAll().stream().map(StockUniverse::getSymbol).toList();
    }

    private void clearPrioritySymbols(Collection<String> symbols) {
        marketGateway.clearPrioritySymbols(symbols);
    }

    private void addNormalizedSymbol(Collection<String> target, String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized != null && !target.contains(normalized)) target.add(normalized);
    }

    private List<String> takeWindow(List<String> source, int start, int size) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        int actualSize = Math.min(size, source.size());
        List<String> window = new ArrayList<>(actualSize);
        for (int i = 0; i < actualSize; i++) {
            window.add(source.get((start + i) % source.size()));
        }
        return window;
    }
}
