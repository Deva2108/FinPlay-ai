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

// Removed @Lazy(false) to prevent blocking startup on low-CPU environments.
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
    private final com.example.stockPortfolio.PortfolioManagement.PortfolioHistoryService portfolioHistoryService;
    
    private final ExecutorService aiPrecomputationExecutor;
    
    private final ExternalMarketDataGateway externalMarketDataGateway;
    private final MarketStatusService marketStatusService;
    private final GoogleSheetsService googleSheetsService;
    private final StockUniverseRepo stockUniverseRepo;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    private final MarketAnalysisService marketAnalysisService;

    private int highPriorityIndex = 0;
    private int normalIndex = 0;
    private int afterHoursIndex = 0;
    private int chartCycleCounter = 0;
    private static final int CHART_UPDATE_FREQUENCY = 5;
    private static final int BATCH_SIZE = 12;

    // TwelveData free tier: 8 credits/min, 1 credit per symbol even in batch calls.
    // We utilize the full 8 credits/min headroom for high-priority user needs.
    private static final int TWELVEDATA_CYCLE_LIMIT = 8;
    private static final int AFTER_HOURS_BATCH_SIZE = 4;

    // Heavy services tagged @Lazy so the scheduler bean (and downstream
    // SecurityFilterChain → Tomcat port bind) can finish initializing without
    // waiting on Finnhub/News/AI/Google clients to warm up. The first
    // @Scheduled tick (initialDelay >= 30s) triggers their real instantiation,
    // by which time the actuator health endpoint is already serving traffic.
    public MarketDataScheduler(@Lazy FinnhubService finnhubService,
                               @Lazy NewsApiService newsApiService,
                               MarketGateway marketGateway,
                               SymbolNormalizer symbolNormalizer,
                               com.example.stockPortfolio.HoldingsManagement.HoldingRepo holdingRepo,
                               com.example.stockPortfolio.AlertManagement.AlertRepo alertRepo,
                               @Lazy com.example.stockPortfolio.AiManagement.service.AiService aiService,
                               com.example.stockPortfolio.PortfolioManagement.PortfolioRepo portfolioRepo,
                               com.example.stockPortfolio.UserManagement.UserRepo userRepo,
                               @Lazy ExternalMarketDataGateway externalMarketDataGateway,
                               MarketStatusService marketStatusService,
                               @Lazy GoogleSheetsService googleSheetsService,
                               StockUniverseRepo stockUniverseRepo,
                               org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate,
                               @Lazy MarketAnalysisService marketAnalysisService,
                               @Lazy com.example.stockPortfolio.PortfolioManagement.PortfolioHistoryService portfolioHistoryService) {
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
        this.redisTemplate = redisTemplate;
        this.marketAnalysisService = marketAnalysisService;
        this.portfolioHistoryService = portfolioHistoryService;
        
        // Java 21 virtual threads: each AI precomputation task gets its own lightweight
        // carrier thread. No fixed pool size needed — VTs block cheaply on the Groq HTTP
        // call without pinning OS threads, and JVM garbage-collects them when done.
        // Replaces the fixed ThreadPoolExecutor(3,5) which could bottleneck when all 5
        // threads were blocked waiting on slow Groq responses simultaneously.
        this.aiPrecomputationExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Scheduled(fixedRate = 60000, initialDelay = 120000)
    public void afterHoursHydration() {
        boolean indiaClosed = !marketStatusService.isIndianMarketOpen();
        boolean usClosed = !marketStatusService.isUsMarketOpen();

        // If both are open, the live hydration handles everything.
        if (!indiaClosed && !usClosed) return;

        log.debug("Starting market-aware after-hours hydration (India Closed: {}, US Closed: {})", indiaClosed, usClosed);
        
        List<StockUniverse> universe = stockUniverseRepo.findAll();
        if (universe.isEmpty()) return;

        // CONTINUOUS ROTATION: Slice the universe to ensure every stock gets updated eventually.
        if (afterHoursIndex >= universe.size()) afterHoursIndex = 0;
        int end = Math.min(afterHoursIndex + 20, universe.size()); // Take a 20-symbol window to scan
        List<StockUniverse> window = universe.subList(afterHoursIndex, end);
        afterHoursIndex = end;

        // Find symbols in closed markets that need hydration.
        // Uses the 60-minute after-hours freshness window (vs 10-min during live hours)
        // so we refresh each symbol at most once per hour after close — enough for
        // a reliable EOD price capture without burning TwelveData daily quota.
        List<String> targets = window.stream()
                .filter(s -> {
                    boolean isIndian = symbolNormalizer.isIndian(s.getSymbol());
                    if (isIndian && !indiaClosed) return false;
                    if (!isIndian && !usClosed) return false;
                    return !marketGateway.isFreshForAfterHours(s.getSymbol());
                })
                .map(StockUniverse::getSymbol)
                .limit(AFTER_HOURS_BATCH_SIZE)
                .toList();

        if (targets.isEmpty()) return;

        log.info("After-hours hydration (Continuous Rotation): Fetching {} symbols.", targets.size());
        try {
            Map<String, Map<String, Object>> quotes = externalMarketDataGateway.fetchTwelveDataBatch(targets);
            if (!quotes.isEmpty()) {
                quotes.forEach(marketGateway::updateMirror);
            } else {
                // Non-blocking pacing via CompletableFuture.delayedExecutor — frees the
                // scheduler thread immediately. Yahoo polls still execute at ~1 req/sec.
                for (int i = 0; i < targets.size(); i++) {
                    final String s = targets.get(i);
                    CompletableFuture.runAsync(() -> {
                        try {
                            Map<String, Object> q = externalMarketDataGateway.fetchQuoteWithFallback(s);
                            if (q != null) marketGateway.updateMirror(s, q);
                        } catch (Exception ex) {
                            log.warn("Fallback fetch failed for {}: {}", s, ex.getMessage());
                        }
                    }, CompletableFuture.delayedExecutor(i * 1000L, TimeUnit.MILLISECONDS, aiPrecomputationExecutor));
                }
            }
        } catch (Exception e) {
            log.error("Market-aware hydration cycle failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60000, initialDelay = 45000)
    public void precomputeTopMovers() {
        log.debug("Starting Top Movers precomputation...");
        try {
            List<Map<String, Object>> allData = marketAnalysisService.getMarketData();
            if (allData.isEmpty()) return;

            // 1. Gainers
            List<Map<String, Object>> gainers = allData.stream()
                    .sorted((a, b) -> Double.compare(
                            getDouble(b, "changesPercentage"),
                            getDouble(a, "changesPercentage")))
                    .limit(20)
                    .toList();
            marketGateway.updateTopMovers("gainers", gainers);

            // 2. Losers
            List<Map<String, Object>> losers = allData.stream()
                    .sorted((a, b) -> Double.compare(
                            getDouble(a, "changesPercentage"),
                            getDouble(b, "changesPercentage")))
                    .limit(20)
                    .toList();
            marketGateway.updateTopMovers("losers", losers);

            // 3. Trending (Absolute change)
            List<Map<String, Object>> trending = allData.stream()
                    .sorted((a, b) -> Double.compare(
                            Math.abs(getDouble(b, "changesPercentage")),
                            Math.abs(getDouble(a, "changesPercentage"))))
                    .limit(10)
                    .toList();
            marketGateway.updateTopMovers("trending", trending);

            log.info("Top Movers precomputed and cached in Redis.");
        } catch (Exception e) {
            log.error("Top Movers precomputation failed: {}", e.getMessage());
        }
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0.0;
    }

    // ── Portfolio value snapshots ─────────────────────────────────────────────
    // Runs 5 minutes after each market close, capturing end-of-session portfolio
    // value per user. One row per portfolio per day — lightweight, no event
    // sourcing. The 5-minute buffer lets the last-minute scheduler hydration
    // cycle complete so holdings prices are current when the snapshot is taken.

    /** India close: 3:35 PM IST on weekdays. */
    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void snapshotPortfoliosAfterIndiaClose() {
        log.info("Triggering portfolio snapshot after India market close.");
        portfolioHistoryService.snapshotAllPortfolios();
    }

    /** US close: 4:05 PM ET on weekdays. */
    @Scheduled(cron = "0 5 16 * * MON-FRI", zone = "America/New_York")
    public void snapshotPortfoliosAfterUsClose() {
        log.info("Triggering portfolio snapshot after US market close.");
        portfolioHistoryService.snapshotAllPortfolios();
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

    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void hydrateMarketMirror() {
        Set<String> urgentSymbols = marketGateway.getPrioritySymbols();
        boolean marketActive = marketStatusService.isAnyMarketActive();

        // If no market is active (Open or Pre-Market) and no user is actively waiting, skip
        if (!marketActive && urgentSymbols.isEmpty()) return;

        // 1. COLLECT TARGETS (Strict Priority: Urgent -> Holdings -> Universe)
        Set<String> targets = new LinkedHashSet<>();

        // Track the normalized urgent symbols that were actually admitted to targets
        // so we can drain precisely those entries from the priority Redis set —
        // symbols that were present in urgentSymbols but filtered out of symbolsToFetch
        // (e.g. their market was closed) must still be cleared to prevent queue jam.
        Set<String> drainedPrioritySymbols = new LinkedHashSet<>();

        // Priority 1: Symbols user is currently looking at (from SYNCING state)
        urgentSymbols.stream()
            .map(symbolNormalizer::normalize)
            .filter(Objects::nonNull)
            .limit(TWELVEDATA_CYCLE_LIMIT) // Maximize TwelveData quota
            .forEach(s -> { targets.add(s); drainedPrioritySymbols.add(s); });

        // Priority 2: If we have room, fill with other active symbols (Holdings/Universe)
        if (targets.size() < TWELVEDATA_CYCLE_LIMIT) {
            List<String> allSymbols = new ArrayList<>();
            try {
                holdingRepo.findAllDistinctSymbols().forEach(s -> {
                    String n = symbolNormalizer.normalize(s);
                    if (n != null && !targets.contains(n)) allSymbols.add(n);
                });
            } catch (Exception e) {
                log.warn("Failed to fetch holdings: {}", e.getMessage());
            }
            
            getUniverse().forEach(s -> {
                if (!targets.contains(s) && !allSymbols.contains(s)) {
                    allSymbols.add(s);
                }
            });

            int remainingSlots = TWELVEDATA_CYCLE_LIMIT - targets.size();
            if (!allSymbols.isEmpty()) {
                if (normalIndex >= allSymbols.size()) normalIndex = 0;
                int endIndex = Math.min(normalIndex + remainingSlots, allSymbols.size());
                targets.addAll(allSymbols.subList(normalIndex, endIndex));
                normalIndex = endIndex;
            }
        }

        // 2. FILTER: stale AND market currently open/pre-market for that symbol.
        //
        // Skipping symbols whose exchange is closed avoids burning TwelveData
        // quota on Indian stocks during US-only hours (and vice versa). Cached
        // last_close data serves those requests until the market reopens.
        // User-urgent symbols bypass this filter — they are already resolved from
        // Redis last_close by getLatestQuote() if the market is closed.
        List<String> symbolsToFetch = targets.stream()
                .filter(s -> !marketGateway.isFresh(s))
                .filter(s -> {
                    boolean isIndian = symbolNormalizer.isIndian(s);
                    if (isIndian) {
                        return marketStatusService.isIndianMarketOpen() || marketStatusService.isIndianPreMarket();
                    }
                    return marketStatusService.isUsMarketOpen() || marketStatusService.isUsPreMarket();
                })
                .toList();
        
        if (symbolsToFetch.isEmpty()) return;

        log.info("Hydration cycle: Fetching {} symbols ({} urgent). Quota: 8/min.", 
                 symbolsToFetch.size(), urgentSymbols.size());

        try {
            // 3. EXECUTE BATCH (TwelveData)
            Map<String, Map<String, Object>> quotes = externalMarketDataGateway.fetchTwelveDataBatch(symbolsToFetch);

            if (!quotes.isEmpty()) {
                quotes.forEach(marketGateway::updateMirror);
                log.info("Hydration cycle: Successfully updated {} symbols.", quotes.size());
            } else {
                // LAST RESORT: Yahoo slow-poll via dedicated scheduler-only method.
                // Uses fetchYahooFallbackQuote (not fetchQuoteWithFallback) so Yahoo is
                // never triggered from user-request threads — only here, staggered.
                // 500 ms pacing keeps Render's shared IP safe (~2 req/sec).
                log.warn("Batch failed. Falling back to staggered Yahoo poll for {} symbols.", symbolsToFetch.size());
                for (int i = 0; i < symbolsToFetch.size(); i++) {
                    final String s = symbolsToFetch.get(i);
                    CompletableFuture.runAsync(() -> {
                        try {
                            Map<String, Object> q = externalMarketDataGateway.fetchYahooFallbackQuote(s);
                            if (q != null) marketGateway.updateMirror(s, q);
                        } catch (Exception ex) {
                            log.warn("Yahoo fallback fetch failed for {}: {}", s, ex.getMessage());
                        }
                    }, CompletableFuture.delayedExecutor(i * 500L, TimeUnit.MILLISECONDS, aiPrecomputationExecutor));
                }
            }

            // 4. CLEANUP — clear ALL drained priority symbols (not just the ones that
            // passed the freshness/market-open filter). Symbols filtered out of
            // symbolsToFetch were still pulled from the Redis set this cycle and
            // must be cleared; otherwise they re-enter every tick and jam the queue.
            marketGateway.clearPrioritySymbols(drainedPrioritySymbols);
        } catch (Exception e) {
            log.error("Hydration cycle failed: {}", e.getMessage());
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
