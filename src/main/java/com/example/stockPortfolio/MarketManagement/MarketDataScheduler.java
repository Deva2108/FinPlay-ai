package com.example.stockPortfolio.MarketManagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import com.example.stockPortfolio.AiManagement.ExplainRequestDTO;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

// @Lazy(false) forces eager instantiation so @Scheduled tasks register at startup
// despite spring.main.lazy-initialization=true in the prod profile.
@Lazy(false)
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

    // TwelveData free tier: 8 credits/min, 1 credit per symbol even in batch calls.
    // Keep cycle limit at 6 to leave headroom for urgent symbols without breaching quota.
    private static final int TWELVEDATA_CYCLE_LIMIT = 6;

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
        
        this.aiPrecomputationExecutor = new ThreadPoolExecutor(
                3, 5, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @jakarta.annotation.PreDestroy
    public void shutdownExecutors() {
        log.info("Shutting down MarketDataScheduler executors...");
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

    @Scheduled(fixedRate = 180000, initialDelay = 30000) // first run 30 s after startup; 180 s keeps TwelveData within 800/day free-tier quota
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
        // Keep rotation window at TWELVEDATA_CYCLE_LIMIT so that steady-state
        // refresh rate (symbols/min) stays within the TwelveData free-tier quota.
        int batchSize = TWELVEDATA_CYCLE_LIMIT;
        int remainingSlots = batchSize - targets.size();
        
        if (remainingSlots > 0 && !allSymbols.isEmpty()) {
            if (normalIndex >= allSymbols.size()) {
                normalIndex = 0;
            }
            
            int endIndex = Math.min(normalIndex + remainingSlots, allSymbols.size());
            targets.addAll(allSymbols.subList(normalIndex, endIndex));
            normalIndex = endIndex;
        }

        // Skip symbols whose Redis data is still within the 10-minute freshness window.
        // Urgent symbols are also skipped if updated within the last 60 seconds to
        // prevent burning TwelveData credits on data that is still effectively live.
        List<String> symbolsToFetch = targets.stream()
                .filter(s -> !marketGateway.isFresh(s))
                .collect(Collectors.toList());
        if (symbolsToFetch.isEmpty()) return;

        // Hard quota cap: only pass up to TWELVEDATA_CYCLE_LIMIT symbols to the TwelveData
        // batch. Urgent symbols are sorted first in symbolsToFetch, so they get priority
        // within the cap. Symbols beyond the cap are deferred to the next 60-second cycle
        // or served by Yahoo Finance via the individual fallback chain below.
        List<String> batchedSymbols = symbolsToFetch.size() > TWELVEDATA_CYCLE_LIMIT
                ? symbolsToFetch.subList(0, TWELVEDATA_CYCLE_LIMIT)
                : symbolsToFetch;

        log.info("Market hydration: {} stale symbols ({} capped for TwelveData batch, {} deferred).",
                symbolsToFetch.size(), batchedSymbols.size(), symbolsToFetch.size() - batchedSymbols.size());

        try {
            // 3. FETCH & UPDATE MIRROR via TwelveData batch (quota-capped list)
            Map<String, Map<String, Object>> quotes = externalMarketDataGateway.fetchTwelveDataBatch(batchedSymbols);

            // 4. FALLBACK: If batch returns nothing (429/outage), try individual Yahoo-first
            // chain for all stale symbols — Yahoo is free and does not consume TwelveData credits.
            if (quotes.isEmpty()) {
                log.info("Batch fetch returned 0 results. Falling back to individual provider chain for all {} stale symbols.", symbolsToFetch.size());
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
