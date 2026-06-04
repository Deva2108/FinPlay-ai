package com.example.stockPortfolio.MarketManagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    // Centralized governance. Field-injected (not constructor) so the existing
    // long constructor stays stable; @Lazy lets context refresh complete before
    // any cross-bean wiring is exercised.
    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private MarketDataPolicyService policyService;

    private int highPriorityIndex = 0;
    private int normalIndex = 0;
    private int afterHoursIndex = 0;
    private int chartCycleCounter = 0;
    private static final int CHART_UPDATE_FREQUENCY = 5;
    private static final int BATCH_SIZE = 12;

    // Cold-deploy bootstrap: the market page's default symbols (RELIANCE.NS / AAPL)
    // plus a couple of top constituents, so a fresh deploy serves real prices
    // before any user activity and regardless of market hours. Hard-capped list,
    // provider-friendly equities only (no index symbols) — must NEVER grow into a
    // full-universe walk. Fetched at most 5; seeded once per JVM.
    private static final List<String> BOOTSTRAP_SYMBOLS =
            List.of("AAPL", "RELIANCE.NS", "MSFT", "TCS.NS", "HDFCBANK.NS");
    private final java.util.concurrent.atomic.AtomicBoolean bootstrapHydrated =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // Instance ID for scheduler lock ownership. Generated once per JVM so log
    // messages can identify which instance held a contested lock.
    private static final String INSTANCE_ID = java.util.UUID.randomUUID().toString().substring(0, 8);

    /**
     * Distributed scheduler lock via Redis SET NX EX. If two backend instances
     * ever run simultaneously (rolling restart, manual scale-out), only one
     * fires each scheduled job per cycle. Lock TTL is conservative — long
     * enough to outlast the slowest expected run, short enough to recover
     * automatically if a holder crashes mid-cycle.
     *
     * Returns true if this instance acquired the lock and should proceed.
     * Returns true (with a debug log) if Redis is unavailable — schedulers
     * still need to run on standalone deployments without distributed Redis.
     */
    private boolean acquireSchedulerLock(String name) {
        try {
            String key = "scheduler:lock:" + name;
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                    key, INSTANCE_ID, java.time.Duration.ofMinutes(2));
            if (Boolean.TRUE.equals(ok)) return true;
            log.debug("Scheduler '{}' skipped — lock held by another instance.", name);
            return false;
        } catch (Exception e) {
            // Fail open: if Redis is down, we don't want to silently stop the
            // scheduler. Better to risk double-fire than no-fire.
            log.debug("Scheduler lock check failed for '{}'; running anyway. ({})", name, e.getMessage());
            return true;
        }
    }

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
                               @Lazy MarketGateway marketGateway,
                               @Lazy SymbolNormalizer symbolNormalizer,
                               @Lazy com.example.stockPortfolio.HoldingsManagement.HoldingRepo holdingRepo,
                               @Lazy com.example.stockPortfolio.AlertManagement.AlertRepo alertRepo,
                               @Lazy com.example.stockPortfolio.AiManagement.service.AiService aiService,
                               @Lazy com.example.stockPortfolio.PortfolioManagement.PortfolioRepo portfolioRepo,
                               @Lazy com.example.stockPortfolio.UserManagement.UserRepo userRepo,
                               @Lazy ExternalMarketDataGateway externalMarketDataGateway,
                               @Lazy MarketStatusService marketStatusService,
                               @Lazy GoogleSheetsService googleSheetsService,
                               @Lazy StockUniverseRepo stockUniverseRepo,
                               @Lazy org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate,
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

    @Scheduled(fixedRateString = "${finplay.scheduler.afterhours-rate-ms:60000}", initialDelay = 120000)
    public void afterHoursHydration() {
        if (!acquireSchedulerLock("afterHoursHydration")) return;
        boolean indiaClosed = !marketStatusService.isIndianMarketOpen();
        boolean usClosed = !marketStatusService.isUsMarketOpen();

        // If both are open, the live hydration handles everything.
        if (!indiaClosed && !usClosed) return;

        // ── Governance gate: skip after-hours hydration entirely when TwelveData
        // is in provider-wide cooldown. After-hours data is the lowest-priority
        // workload — it should never burn quota during an outage.
        if (policyService.isProviderDisabled(MarketDataPolicyService.PROVIDER_TWELVEDATA)) {
            log.debug("After-hours hydration skipped — TwelveData in provider-wide cooldown.");
            return;
        }

        log.debug("Starting market-aware after-hours hydration (India Closed: {}, US Closed: {})", indiaClosed, usClosed);

        // ── ACTIVE-ONLY targets (no universe rotation) ─────────────────────────
        // After-hours hydration used to rotate through the full StockUniverse
        // (a 20-symbol sliding window every minute = 1 200 credits/hour wasted
        // on symbols nobody was viewing). Now: only refresh symbols that are
        // currently active AND belong to a closed market that needs an EOD price.
        Set<String> activeSymbols = policyService.listActiveSymbols();
        if (activeSymbols.isEmpty()) return;

        List<String> targets = activeSymbols.stream()
                .filter(s -> {
                    boolean isIndian = symbolNormalizer.isIndian(s);
                    if (isIndian && !indiaClosed) return false;
                    if (!isIndian && !usClosed) return false;
                    return !marketGateway.isFreshForAfterHours(s);
                })
                .filter(s -> !marketGateway.isInFailureCooldown(s))
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

    @Scheduled(fixedRateString = "${finplay.scheduler.movers-rate-ms:60000}", initialDelay = 45000)
    public void precomputeTopMovers() {
        if (!acquireSchedulerLock("precomputeTopMovers")) return;
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

    @Scheduled(fixedRateString = "${finplay.scheduler.ai-rate-ms:1800000}", initialDelay = 60000) // first run 60 s after startup
    public void precomputeAiInsights() {
        if (!acquireSchedulerLock("precomputeAiInsights")) return;
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
                    // CACHE-ONLY read — getLatestQuote would fall through to a synchronous
                    // external fetch on cache miss, allowing the AI precompute job to burn
                    // TwelveData quota. AI insights are best-effort enhancements; if the
                    // quote isn't already in Redis, we simply skip this index's insight
                    // for this cycle (it will be available on the next 30-min tick).
                    Map<String, Object> quote = marketGateway.getCachedQuoteSnapshot(idx.getSymbol());
                    if (quote == null) {
                        log.debug("Skipping AI insight for {} — no cached quote (cache-only policy).", idx.getSymbol());
                        return;
                    }
                    String prompt = String.format("Generate index insight for %s at %s.", idx.getName(), quote.get("price"));
                    String insight = aiService.generateRichInsight(prompt);
                    if (insight != null) marketGateway.updatePrecomputedInsight("index", idx.getSymbol(), insight);
                } catch (Exception e) {
                    log.error("Failed to precompute insight for {}: {}", idx.getSymbol(), e.getMessage());
                }
            });
        }
    }

    /**
     * One-time cold-deploy warm-up. Seeds the Redis mirror for a tiny, fixed set
     * of provider-friendly default symbols so the market page shows real prices
     * immediately after deploy — before any user activity and regardless of
     * market hours. Hard-capped at 5 symbols; never walks the universe. Uses ONLY
     * existing fetch/persist paths (no provider or caching changes).
     */
    private void bootstrapWarmHydration() {
        List<String> targets = BOOTSTRAP_SYMBOLS.stream()
                .map(symbolNormalizer::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
        if (targets.isEmpty()) { bootstrapHydrated.set(true); return; }

        try {
            Map<String, Map<String, Object>> quotes = externalMarketDataGateway.fetchTwelveDataBatch(targets);
            if (quotes != null && !quotes.isEmpty()) {
                quotes.forEach(marketGateway::updateMirror);
                bootstrapHydrated.set(true);
                log.info("Bootstrap warm hydration seeded {} default symbols.", quotes.size());
                return;
            }
            // Batch empty → staggered Yahoo fallback (same pattern as the main
            // cycle). Mark done so we don't retry-storm; the async tasks fill in.
            log.warn("Bootstrap batch empty. Falling back to staggered Yahoo for {} default symbols.", targets.size());
            for (int i = 0; i < targets.size(); i++) {
                final String s = targets.get(i);
                CompletableFuture.runAsync(() -> {
                    try {
                        Map<String, Object> q = externalMarketDataGateway.fetchYahooFallbackQuote(s);
                        if (q != null) marketGateway.updateMirror(s, q);
                    } catch (Exception ex) {
                        log.warn("Bootstrap Yahoo fallback failed for {}: {}", s, ex.getMessage());
                    }
                }, CompletableFuture.delayedExecutor(i * 500L, TimeUnit.MILLISECONDS, aiPrecomputationExecutor));
            }
            bootstrapHydrated.set(true);
        } catch (Exception e) {
            // Leave the flag false so the next tick retries the bootstrap once more.
            log.warn("Bootstrap warm hydration failed, will retry next cycle: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRateString = "${finplay.scheduler.hydration-rate-ms:60000}", initialDelay = 30000)
    public void hydrateMarketMirror() {
        if (!acquireSchedulerLock("hydrateMarketMirror")) return;
        // ── Governance gate 1: provider outage state ─────────────────────────
        // If TwelveData was 429'd or hard-failed in the last 15 min, skip the
        // entire cycle. The cooldown is set inside ExternalMarketDataGateway
        // when a 429 envelope is detected. Eliminates the post-outage retry
        // storm that previously kept hammering an exhausted provider.
        if (policyService.isProviderDisabled(MarketDataPolicyService.PROVIDER_TWELVEDATA)) {
            log.debug("Hydration cycle skipped — TwelveData in provider-wide cooldown.");
            return;
        }

        // Cold-deploy warm-up: seed the default symbols once, before the
        // market-active / market-hours gates below would otherwise skip a fresh,
        // idle, off-hours deploy and leave the market page showing "--".
        if (!bootstrapHydrated.get()) {
            bootstrapWarmHydration();
        }

        Set<String> urgentSymbols = marketGateway.getPrioritySymbols();
        boolean marketActive = marketStatusService.isAnyMarketActive();

        // ── Governance gate 2: nothing to do ──────────────────────────────────
        // Skip entirely when no market is active AND no user is currently waiting.
        if (!marketActive && urgentSymbols.isEmpty()) return;

        // ── Target selection: ACTIVE SYMBOLS ONLY ────────────────────────────
        // Old model collected every symbol in the universe + every holding and
        // rotated through them — burned 5 000+ credits/day on symbols nobody
        // was viewing. New model: only urgent (user-blocking) + active (user-
        // touched within the last 15 min). Cold symbols are served from Redis
        // last_close until someone actually opens them.
        Set<String> targets = new LinkedHashSet<>();
        Set<String> drainedPrioritySymbols = new LinkedHashSet<>();

        // Priority A — urgent symbols (a user is currently SYNCING / waiting).
        urgentSymbols.stream()
            .map(symbolNormalizer::normalize)
            .filter(Objects::nonNull)
            .limit(TWELVEDATA_CYCLE_LIMIT)
            .forEach(s -> { targets.add(s); drainedPrioritySymbols.add(s); });

        // Priority B — active symbols (recently viewed by any user, 15-min TTL).
        // This is the ONLY remaining source of background hydration targets.
        if (targets.size() < TWELVEDATA_CYCLE_LIMIT) {
            for (String s : policyService.listActiveSymbols()) {
                if (targets.size() >= TWELVEDATA_CYCLE_LIMIT) break;
                String n = symbolNormalizer.normalize(s);
                if (n != null) targets.add(n);
            }
        }

        // ── Final filter: master policy gate + market-hours guard ────────────
        // Urgent symbols bypass shouldFetch() because the user is actively
        // waiting; everything else must pass the full governance check
        // (not fresh, not in cooldown, active). The market-hours filter
        // avoids burning credits on closed exchanges.
        List<String> symbolsToFetch = targets.stream()
                .filter(s -> urgentSymbols.contains(s) || policyService.shouldFetch(s))
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
