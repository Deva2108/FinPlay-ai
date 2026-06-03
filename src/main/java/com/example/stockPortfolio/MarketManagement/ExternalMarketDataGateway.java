package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.data.redis.core.RedisTemplate;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExternalMarketDataGateway {

    private final RestTemplate restTemplate;
    private final SymbolNormalizer symbolNormalizer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final YahooFinanceService yahooFinanceService;
    private final GoogleNewsRssService googleNewsRssService;
    // Centralized governance — consulted before every outbound call and notified
    // on any 429 / hard-failure so other systems immediately stop hitting the
    // disabled provider for the cooldown window.
    private final MarketDataPolicyService policyService;

    private static final String LAST_UPDATED_TS_PREFIX = "stock:lastUpdated:";
    private static final String STOCK_KEY_PREFIX = "stock:";
    // Must match MarketGateway.LAST_CLOSE_PREFIX — used by quoteFallback to serve
    // durable stale data when all authenticated providers are unavailable.
    private static final String LAST_CLOSE_KEY_PREFIX = "last_close:";
    private static final long FRESHNESS_THRESHOLD_MS = 10 * 60 * 1000L;

    // Failure cooldown: when all providers fail OR TwelveData returns 429, the symbol
    // is written to Redis with a 5-minute TTL.  The fetch path and the scheduler both
    // skip symbols whose cooldown key is present, breaking the infinite retry loop.
    // Must match MarketGateway.FAILURE_COOLDOWN_PREFIX.
    static final String FAILURE_COOLDOWN_PREFIX = "market:fail:";
    private static final long FAILURE_COOLDOWN_TTL_MINUTES = 5L;

    // In-flight deduplication: ensures concurrent cold requests for the same symbol
    // share one CompletableFuture rather than fanning out to N simultaneous API calls.
    // The future is removed from the map ONLY by its own whenComplete callback, so the
    // map has exactly one cleanup owner and no caller-side removal can race it.
    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> inFlightFetches =
            new ConcurrentHashMap<>();

    // Stale-future protection: a hard upper bound on how long an in-flight entry may
    // live. If the underlying fetch stalls (e.g. a RestTemplate socket hang that never
    // returns), orTimeout completes the future exceptionally at this bound, which fires
    // whenComplete and purges the map entry. Guarantees a symbol can never be permanently
    // "poisoned" by a stuck future. Must be > the per-caller get() timeout (5s) so that
    // slow-but-legitimate fetches still complete and serve late-arriving joiners.
    private static final long IN_FLIGHT_HARD_TIMEOUT_SECONDS = 10L;

    // Provider-specific symbol overrides.
    //
    // Indian stocks: TwelveData uses SYMBOL:NSE format, not the Yahoo-style .NS suffix.
    // Global indices: TwelveData does NOT accept the caret (^) prefix used by Yahoo/Finnhub.
    //   ^NSEI  → NSEI   (Nifty 50)
    //   ^BSESN → BSESN  (BSE Sensex)
    //   ^DJI   → DJI    (Dow Jones)
    //   ^IXIC  → IXIC   (NASDAQ Composite)
    //   ^GSPC  → SPX    (S&P 500 — TwelveData uses "SPX", not "GSPC")
    // Using Map.ofEntries to support > 10 entries (Map.of limit).
    private static final Map<String, String> SYMBOL_OVERRIDE_MAP = Map.ofEntries(
        Map.entry("JIOFIN.NS", "JIOFIN:NSE"),
        Map.entry("ZOMATO.NS", "ZOMATO:NSE"),
        Map.entry("PAYTM.NS",  "PAYTM:NSE"),
        Map.entry("NYKAA.NS",  "FSNRENEW:NSE"),
        Map.entry("^NSEI",     "NSEI"),
        Map.entry("^BSESN",    "BSESN"),
        Map.entry("^DJI",      "DJI"),
        Map.entry("^IXIC",     "IXIC"),
        Map.entry("^GSPC",     "SPX")
    );

    @Value("${finnhub.api.key}")
    private String finnhubApiKey;

    @Value("${finnhub.base-url}")
    private String finnhubBaseUrl;

    @Value("${news.api.key}")
    private String newsApiKey;

    @Value("${news.api.base-url}")
    private String newsApiBaseUrl;

    @Value("${twelvedata.api.key:}")
    private String twelveDataApiKey;

    @Value("${twelvedata.base-url:https://api.twelvedata.com}")
    private String twelveDataBaseUrl;

    @Value("${alpha.vantage.key:}")
    private String alphaVantageApiKey;

    @PostConstruct
    public void validateConfig() {
        if (finnhubApiKey == null || finnhubApiKey.isBlank() || "mock".equalsIgnoreCase(finnhubApiKey)) {
            log.warn("Finnhub API key is missing or mock. Using Yahoo Public as primary for US.");
        }
        if (newsApiKey == null || newsApiKey.isBlank() || "mock".equalsIgnoreCase(newsApiKey)) {
            log.warn("News API key is missing or mock. Using Google RSS as primary.");
        }
    }

    /**
     * Tries to fetch a quote from the best available source.
     * Fallback Chain: Twelve Data → Finnhub/AlphaVantage → Yahoo Public.
     *
     * In-flight deduplication: if N concurrent callers request the same cold symbol,
     * only one external API call is made — all others await the same CompletableFuture.
     * This prevents request fan-out on cache misses (e.g. multiple users opening the
     * same stock page simultaneously after a cache eviction).
     */
    public Map<String, Object> fetchQuoteWithFallback(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return null;

        // 0. FRESHNESS GUARD: return cached value immediately, no locking needed.
        Map<String, Object> cachedQuote = getCachedQuoteIfFresh(normalized);
        if (cachedQuote != null) {
            log.debug("Freshness guard: {} is fresh, skipping API call", normalized);
            return cachedQuote;
        }

        // 1. IN-FLIGHT DEDUP: join an existing in-progress fetch or start a new one.
        //    orTimeout caps the future's lifetime so a hung fetch cannot keep the map
        //    entry alive indefinitely; whenComplete is the single cleanup owner and
        //    fires on success, failure, OR the orTimeout backstop.
        CompletableFuture<Map<String, Object>> future = inFlightFetches.computeIfAbsent(
            normalized,
            sym -> CompletableFuture
                .supplyAsync(() -> doFetchWithFallbacks(sym))
                .orTimeout(IN_FLIGHT_HARD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> inFlightFetches.remove(sym))
        );

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // This caller waited 5s and gave up — but DO NOT remove the map entry here.
            // The shared future keeps running under its orTimeout backstop, so late-arriving
            // callers JOIN it instead of fanning out into duplicate API calls. Removing the
            // entry here was the lockup bug: it let N concurrent callers each spawn a fresh
            // fetch and discarded the in-progress result. Cleanup is owned solely by
            // whenComplete (success, failure, or the orTimeout backstop).
            log.warn("In-flight fetch timed out for {} (caller gave up; shared future continues)", normalized);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            log.warn("In-flight fetch failed for {}: {}", normalized,
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return null;
        }
    }

    /**
     * Executes the authenticated-provider fallback chain for a single normalized symbol.
     * Called on a background thread by fetchQuoteWithFallback's CompletableFuture.
     *
     * Yahoo is intentionally NOT called here. Calling Yahoo from this path would
     * create a request storm: if TwelveData is down, every concurrent cold-cache
     * request fans out to Yahoo simultaneously, risking shared-IP blacklisting.
     *
     * Yahoo hydration is handled exclusively by the scheduler's staggered async
     * path (fetchYahooFallbackQuote, 500 ms/symbol pacing) — the right place for
     * unauthenticated scraping because it is rate-controlled and non-user-blocking.
     */
    private Map<String, Object> doFetchWithFallbacks(String normalized) {
        // 0. COOLDOWN CHECK: skip API calls entirely for recently-failed symbols.
        //    Cooldown is set below when all providers fail, and by the 429 detector
        //    in fetchTwelveDataQuote / processSingleTwelveQuote. Auto-expires in 5 min.
        if (isInFailureCooldown(normalized)) {
            log.debug("Symbol {} is in failure cooldown — skipping provider attempts.", normalized);
            return null;
        }

        // 1. TRY AUTHENTICATED PROVIDERS (Managed by local RateLimiters)
        try {
            Map<String, Object> twelveQuote = fetchTwelveDataQuote(normalized);
            if (twelveQuote != null) return twelveQuote;
        } catch (Exception e) {
            log.debug("Twelve Data failed or throttled for {}", normalized);
        }

        try {
            if (symbolNormalizer.isIndian(normalized)) {
                Map<String, Object> ind = fetchIndianQuote(normalized);
                if (ind != null) return ind;
            } else {
                Map<String, Object> st = fetchStockQuote(normalized);
                if (st != null) return st;
            }
        } catch (Exception e) {
            log.debug("Standard authenticated providers failed for {}", normalized);
        }

        // All providers exhausted — enter failure cooldown to stop the retry loop.
        // The scheduler and this method will both skip this symbol for the next 5 min.
        markFailureCooldown(normalized);
        log.debug("All authenticated providers failed for {}. Cooldown set ({}m).", normalized, FAILURE_COOLDOWN_TTL_MINUTES);
        return null;
    }

    /**
     * Scheduler-only Yahoo public fallback.
     *
     * Called exclusively from MarketDataScheduler's staggered async loop
     * (500 ms delay between symbols). Never invoked on user-request threads.
     * Keeping this path separate from fetchQuoteWithFallback/doFetchWithFallbacks
     * ensures a TwelveData outage cannot amplify into concurrent Yahoo scraping.
     */
    public Map<String, Object> fetchYahooFallbackQuote(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return null;
        // Governance gate: if Yahoo was recently marked disabled (e.g. anti-bot block),
        // skip immediately so we don't keep hitting an endpoint that's actively refusing us.
        if (policyService.isProviderDisabled(MarketDataPolicyService.PROVIDER_YAHOO)) {
            log.debug("Yahoo globally disabled by policy — skipping fallback for {}.", normalized);
            return null;
        }
        try {
            Map<String, Object> q = yahooFinanceService.fetchPublicQuote(normalized);
            // Hard-failure heuristic: yahooFinanceService returns null on 403/429/parse errors.
            // We don't disable on a single null (could be a single bad symbol), but the per-symbol
            // cooldown stops the loop and the next cycle will try again.
            return q;
        } catch (Exception e) {
            // Outright exceptions from Yahoo (TLS, DNS, IP block) — assume provider-wide trouble.
            log.warn("Scheduler Yahoo fallback exception for {}: {} — marking Yahoo disabled.", normalized, e.getMessage());
            policyService.markProviderDisabled(MarketDataPolicyService.PROVIDER_YAHOO);
            return null;
        }
    }

    @CircuitBreaker(name = "twelvedata", fallbackMethod = "quoteFallback")
    @RateLimiter(name = "twelvedata")
    @Retry(name = "default")
    public Map<String, Object> fetchTwelveDataQuote(String symbol) {
        if (twelveDataApiKey == null || twelveDataApiKey.isBlank() || "mock".equalsIgnoreCase(twelveDataApiKey)) return null;
        // Governance gate: if TwelveData was recently disabled (429 or repeated hard-fail),
        // return null immediately so the fallback chain (Finnhub/AlphaVantage → Yahoo) takes over.
        if (policyService.isProviderDisabled(MarketDataPolicyService.PROVIDER_TWELVEDATA)) {
            log.debug("TwelveData globally disabled by policy — skipping quote fetch for {}.", symbol);
            return null;
        }

        String normalized = symbolNormalizer.normalize(symbol);
        
        // 1. Resolve TwelveData Symbol
        String twelveSymbol = SYMBOL_OVERRIDE_MAP.get(normalized);
        if (twelveSymbol == null) {
            twelveSymbol = normalized;
            if (symbolNormalizer.isIndian(normalized)) {
                twelveSymbol = normalized.replace(".NS", ":NSE");
            }
        }

        // OPTIMIZATION: Use /quote endpoint directly (includes price and change in 1 credit)
        String quoteUrl = UriComponentsBuilder.fromHttpUrl(twelveDataBaseUrl + "/quote")
                .queryParam("symbol", twelveSymbol)
                .queryParam("apikey", twelveDataApiKey)
                .toUriString();

        try {
            Map<String, Object> quoteResponse = restTemplate.getForObject(quoteUrl, Map.class);
            // Reject null, error envelopes ({"code":429,...}), and responses missing the 'close' field.
            // TwelveData /quote uses "close" for the current price, not "price".
            // The separate /price endpoint uses "price"; /quote does not — confirmed via OpenAPI spec.
            if (quoteResponse == null || quoteResponse.containsKey("code") || !quoteResponse.containsKey("close")) {
                if (quoteResponse != null && quoteResponse.containsKey("code")) {
                    Object codeVal = quoteResponse.get("code");
                    log.warn("TwelveData error for {}: code={} msg={}",
                            twelveSymbol, codeVal, quoteResponse.get("message"));
                    // 429 = quota exhausted (account-wide).
                    //   Per-symbol cooldown:  prevents this symbol from being retried for 5 min.
                    //   Provider-wide outage: prevents ALL OTHER symbols from hitting TwelveData
                    //                         for 15 min — this is the central governance signal.
                    if ("429".equals(String.valueOf(codeVal))) {
                        markFailureCooldown(normalized);
                        policyService.markProviderDisabled(MarketDataPolicyService.PROVIDER_TWELVEDATA);
                    }
                } else if (quoteResponse != null) {
                    log.warn("TwelveData /quote for {}: 'close' field absent. Keys present: {}",
                            twelveSymbol, quoteResponse.keySet());
                }
                return null;
            }

            double price = safeParseDouble(quoteResponse.get("close"));
            if (price <= 0) {
                log.warn("TwelveData returned non-positive price for {}. Skipping.", twelveSymbol);
                return null;
            }
            double changePct = safeParseDouble(quoteResponse.getOrDefault("percent_change", "0"));

            Map<String, Object> res = new HashMap<>();
            res.put("symbol", normalized);
            res.put("price", price);
            res.put("changesPercentage", Math.round(changePct * 100.0) / 100.0);
            res.put("source", "twelvedata");
            return res;
        } catch (Exception e) {
            log.warn("Twelve Data API error for {}: {}", twelveSymbol, e.getMessage());
            return null;
        }
    }

    @CircuitBreaker(name = "alphavantage", fallbackMethod = "quoteFallback")
    @RateLimiter(name = "alphavantage")
    @Retry(name = "default")
    public Map<String, Object> fetchIndianQuote(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null || !symbolNormalizer.isIndian(normalized)) {
            return null;
        }

        if (alphaVantageApiKey == null || alphaVantageApiKey.isBlank() || "mock".equalsIgnoreCase(alphaVantageApiKey)) {
            return null;
        }

        // Check for specific overrides first
        String alphavSymbol = SYMBOL_OVERRIDE_MAP.get(normalized);
        if (alphavSymbol == null) {
            alphavSymbol = normalized.replace(".NS", ".BSE");
        }

        String url = UriComponentsBuilder.fromHttpUrl("https://www.alphavantage.co/query")
                .queryParam("function", "GLOBAL_QUOTE")
                .queryParam("symbol", alphavSymbol)
                .queryParam("apikey", alphaVantageApiKey)
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || !response.containsKey("Global Quote")) {
            return null;
        }

        Map<String, Object> quote = (Map<String, Object>) response.get("Global Quote");
        if (quote == null || quote.isEmpty()) {
            return null;
        }

        double current = safeParseDouble(quote.get("05. price"));
        double changePct = safeParseDouble(quote.get("10. change percent").toString().replace("%", ""));

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", normalized);
        result.put("price", current);
        result.put("changesPercentage", Math.round(changePct * 100.0) / 100.0);
        result.put("source", "alphavantage");
        return result;
    }

    @CircuitBreaker(name = "finnhub", fallbackMethod = "quoteFallback")
    @RateLimiter(name = "finnhub")
    @Retry(name = "default")
    public Map<String, Object> fetchStockQuote(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null || symbolNormalizer.isIndian(normalized)) {
            return null;
        }

        if (finnhubApiKey == null || finnhubApiKey.isBlank() || "mock".equalsIgnoreCase(finnhubApiKey)) {
            return null;
        }

        String url = UriComponentsBuilder.fromHttpUrl(finnhubBaseUrl + "/quote")
                .queryParam("symbol", normalized)
                .queryParam("token", finnhubApiKey)
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || response.get("c") == null) {
            return null;
        }

        double current = safeParseDouble(response.get("c"));
        double previousClose = safeParseDouble(response.getOrDefault("pc", 0));
        double changePct = previousClose != 0 ? ((current - previousClose) / previousClose) * 100 : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", normalized);
        result.put("price", current);
        result.put("changesPercentage", Math.round(changePct * 100.0) / 100.0);
        result.put("source", "finnhub");
        return result;
    }

    @CircuitBreaker(name = "newsapi", fallbackMethod = "listFallback")
    @RateLimiter(name = "newsapi")
    @Retry(name = "default")
    public List<Map<String, Object>> fetchMarketNews(String query) {
        // 1. ALWAYS TRY GOOGLE RSS FIRST (Free, Unlimited)
        try {
            List<Map<String, Object>> rssNews = googleNewsRssService.fetchRssNews(query);
            if (!rssNews.isEmpty()) return rssNews;
        } catch (Exception e) {
            log.warn("Google RSS failed, trying authenticated sources...");
        }

        // 2. FALLBACK TO NEWSAPI (Authenticated)
        if (newsApiKey == null || newsApiKey.isBlank() || "mock".equalsIgnoreCase(newsApiKey)) {
            return Collections.emptyList();
        }

        String normalized = query == null || query.isBlank() ? "stock market" : query;
        String url = UriComponentsBuilder.fromHttpUrl(newsApiBaseUrl + "/everything")
                .queryParam("q", normalized)
                .queryParam("sortBy", "publishedAt")
                .queryParam("language", "en")
                .queryParam("pageSize", 10)
                .queryParam("apiKey", newsApiKey)
                .toUriString();

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !response.containsKey("articles")) return Collections.emptyList();

            List<Map<String, Object>> articles = (List<Map<String, Object>>) response.get("articles");
            return articles.stream().limit(10).map(article -> {
                Map<String, Object> item = new HashMap<>();
                item.put("headline", article.get("title"));
                Map<String, Object> source = (Map<String, Object>) article.get("source");
                item.put("source", source != null ? source.get("name") : "NewsAPI");
                item.put("datetime", article.get("publishedAt"));
                item.put("url", article.get("url"));
                return item;
            }).toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @CircuitBreaker(name = "finnhub", fallbackMethod = "listFallback")
    @RateLimiter(name = "finnhub")
    @Retry(name = "default")
    public List<Map<String, Object>> fetchCompanyNews(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null || symbolNormalizer.isIndian(normalized)) {
            return Collections.emptyList();
        }

        if (finnhubApiKey == null || finnhubApiKey.isBlank() || "mock".equalsIgnoreCase(finnhubApiKey)) {
            return Collections.emptyList();
        }

        String today = LocalDate.now().toString();
        String lastWeek = LocalDate.now().minusDays(7).toString();
        String url = UriComponentsBuilder.fromHttpUrl(finnhubBaseUrl + "/company-news")
                .queryParam("symbol", normalized)
                .queryParam("from", lastWeek)
                .queryParam("to", today)
                .queryParam("token", finnhubApiKey)
                .toUriString();

        try {
            List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);
            if (response == null) return Collections.emptyList();

            return response.stream().limit(10).map(item -> {
                Map<String, Object> simplified = new HashMap<>();
                simplified.put("headline", item.get("headline"));
                simplified.put("source", item.get("source"));
                Object datetime = item.get("datetime");
                if (datetime instanceof Number number) {
                    simplified.put("datetime", Instant.ofEpochSecond(number.longValue()).toString());
                } else if (datetime != null) {
                    simplified.put("datetime", datetime.toString());
                }
                simplified.put("url", item.get("url"));
                return simplified;
            }).toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // Fallbacks

    /**
     * Circuit-breaker / rate-limiter fallback for all authenticated quote providers.
     *
     * MUST NOT call Yahoo or any external HTTP endpoint — this runs synchronously on
     * the caller's thread and fires once per request when a circuit is open.
     * Calling Yahoo here would create a synchronized scraping storm under load.
     *
     * Strategy: serve the best available cached value from Redis so the SWR layer
     * can keep the UI alive with stale-but-usable data while the scheduler's paced
     * async Yahoo path catches up in the background.
     */
    public Map<String, Object> quoteFallback(String symbol, Exception e) {
        String normalized = symbolNormalizer.normalize(symbol);
        log.warn("Provider fallback for {} ({}): serving stale cache.", normalized, e.getMessage());
        if (normalized != null) {
            // 1. Warm cache (stock: key, 30-min TTL) — present during normal operation.
            try {
                Object warm = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + normalized);
                if (warm instanceof Map) return (Map<String, Object>) warm;
            } catch (Exception ignored) {}
            // 2. Last-close snapshot (last_close: key, 7-day TTL) — durable through
            //    extended outages; written by MarketGateway after every successful fetch.
            try {
                Object stale = redisTemplate.opsForValue().get(LAST_CLOSE_KEY_PREFIX + normalized);
                if (stale instanceof Map) return (Map<String, Object>) stale;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public List<Map<String, Object>> listFallback(String symbol, Exception e) {
        log.warn("Fallback triggered for news/list {}: {}", symbol, e.getMessage());
        return Collections.emptyList();
    }

    /**
     * Circuit-breaker / rate-limiter fallback for the TwelveData batch endpoint.
     * Returns an empty map so the scheduler falls through to the Yahoo staggered path.
     */
    public Map<String, Map<String, Object>> batchFallback(List<String> symbols, Exception e) {
        log.warn("TwelveData batch circuit-breaker/rate-limit triggered ({}): returning empty — Yahoo stagger path will compensate.", e.getMessage());
        if (symbols != null) {
            for (String s : symbols) {
                String n = symbolNormalizer.normalize(s);
                if (n != null) markFailureCooldown(n);
            }
        }
        return Collections.emptyMap();
    }

    // ── Failure Cooldown Helpers ──────────────────────────────────────────────────
    // Writes a self-expiring Redis key when a symbol has exhausted all providers.
    // Both the per-request path (doFetchWithFallbacks) and the scheduler filter
    // (MarketGateway.isInFailureCooldown) read this key before issuing API calls,
    // preventing the dead-symbol retry loop that burns 5 200+ TwelveData credits/day.

    private void markFailureCooldown(String normalized) {
        try {
            redisTemplate.opsForValue().set(
                    FAILURE_COOLDOWN_PREFIX + normalized,
                    "1",
                    FAILURE_COOLDOWN_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Failure cooldown set for {} ({}m TTL).", normalized, FAILURE_COOLDOWN_TTL_MINUTES);
        } catch (Exception e) {
            log.debug("Could not write failure cooldown for {}: {}", normalized, e.getMessage());
        }
    }

    private boolean isInFailureCooldown(String normalized) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(FAILURE_COOLDOWN_PREFIX + normalized));
        } catch (Exception e) {
            return false; // fail-open: let the fetch proceed when Redis is unreachable
        }
    }

    public List<Map<String, Object>> fetchHistoricalData(String symbol) {
        return fetchHistoricalData(symbol, "1Y");
    }

    public List<Map<String, Object>> fetchHistoricalData(String symbol, String range) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return Collections.emptyList();

        // 1. TRY TWELVE DATA (Global)
        try {
            List<Map<String, Object>> twelveCharts = fetchTwelveDataCandles(normalized, range);
            if (twelveCharts != null && !twelveCharts.isEmpty()) return twelveCharts;
        } catch (Exception e) {
            log.warn("Twelve Data charts failed for {}", normalized);
        }

        // 2. TRY SECONDARY (Finnhub for US, AlphaVantage for India)
        return fetchStockCandles(normalized);
    }

    public List<Map<String, Object>> fetchTwelveDataCandles(String symbol) {
        return fetchTwelveDataCandles(symbol, "1Y");
    }

    @CircuitBreaker(name = "twelvedata", fallbackMethod = "listFallback")
    @RateLimiter(name = "twelvedata")
    @Retry(name = "default")
    public List<Map<String, Object>> fetchTwelveDataCandles(String symbol, String range) {
        if (twelveDataApiKey == null || twelveDataApiKey.isBlank()) return Collections.emptyList();

        String normalized = symbolNormalizer.normalize(symbol);
        
        // Optimize outputsize based on range
        int outputSize = switch (range.toUpperCase()) {
            case "1M" -> 30;
            case "6M" -> 180;
            case "1Y" -> 365;
            case "3Y" -> 1095;
            default -> 365;
        };

        String twelveSymbol = normalized;
        if (symbolNormalizer.isIndian(normalized)) {
            twelveSymbol = normalized.replace(".NS", ":NSE");
        }

        String url = UriComponentsBuilder.fromHttpUrl(twelveDataBaseUrl + "/time_series")
                .queryParam("symbol", twelveSymbol)
                .queryParam("interval", "1day")
                .queryParam("outputsize", outputSize)
                .queryParam("apikey", twelveDataApiKey)
                .toUriString();

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !response.containsKey("values")) {
                return Collections.emptyList();
            }

            List<Map<String, String>> values = (List<Map<String, String>>) response.get("values");
            List<Map<String, Object>> formatted = new ArrayList<>();
            
            for (Map<String, String> val : values) {
                Map<String, Object> point = new HashMap<>();
                point.put("date", val.get("datetime")); // Standardized field name
                point.put("close", safeParseDouble(val.get("close")));
                formatted.add(point);
            }
            
            Collections.reverse(formatted);
            return formatted;
        } catch (Exception e) {
            log.error("Twelve Data candles failed for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches quotes for multiple symbols in a single API call (Batch).
     * Free-tier safe: max 1 chunk of 8 symbols per invocation = max 8 credits per call.
     * Caller (MarketDataScheduler) is responsible for capping the symbol list at 6
     * before calling this method (TWELVEDATA_CYCLE_LIMIT).
     *
     * @CircuitBreaker: trips after sustained failures so the scheduler falls through to
     *   the Yahoo stagger path rather than hammering a down TwelveData endpoint.
     * @RateLimiter: shares the 8 req/min TwelveData bucket with the single-quote path,
     *   creating a hard ceiling on credits consumed per minute across all callers.
     * Note: @Retry intentionally omitted — retrying a batch call multiplies credit cost
     *   (2 attempts × 8 symbols = up to 16 credits) without meaningful benefit.
     */
    @CircuitBreaker(name = "twelvedata", fallbackMethod = "batchFallback")
    @RateLimiter(name = "twelvedata")
    public Map<String, Map<String, Object>> fetchTwelveDataBatch(List<String> symbols) {
        if (twelveDataApiKey == null || twelveDataApiKey.isBlank() || symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. Resolve and Normalize symbols, skipping any that are already fresh in Redis.
        // Defense-in-depth: the scheduler pre-filters, but this guard ensures we never
        // issue a TwelveData credit for a symbol whose data is still within the freshness window.
        Map<String, String> reverseMap = new HashMap<>(); // TwelveSymbol -> OurNormalizedSymbol
        List<String> resolvedSymbols = new ArrayList<>();

        for (String s : symbols) {
            String normalized = symbolNormalizer.normalize(s);
            if (normalized == null) continue;

            if (isFresh(normalized)) {
                log.debug("Batch freshness guard: {} is still fresh, skipping TwelveData credit", normalized);
                continue;
            }

            String twelveSymbol = SYMBOL_OVERRIDE_MAP.get(normalized);
            if (twelveSymbol == null) {
                twelveSymbol = symbolNormalizer.isIndian(normalized) ? normalized.replace(".NS", ":NSE") : normalized;
            }
            resolvedSymbols.add(twelveSymbol);
            reverseMap.put(twelveSymbol, normalized);
        }

        if (resolvedSymbols.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. Fetch in chunks of 8 (hard cap per free-tier: 8 credits/min).
        // Max 1 chunk per invocation so a single scheduler cycle never exceeds 8 credits.
        Map<String, Map<String, Object>> allResults = new HashMap<>();
        int requestCount = 0;
        for (int i = 0; i < resolvedSymbols.size(); i += 8) {
            if (requestCount >= 1) {
                log.warn("TwelveData batch quota guard: capping at 1 chunk per cycle ({} symbols skipped).",
                        resolvedSymbols.size() - i);
                break;
            }

            List<String> chunk = resolvedSymbols.subList(i, Math.min(i + 8, resolvedSymbols.size()));
            String batchString = String.join(",", chunk);

            String url = UriComponentsBuilder.fromHttpUrl(twelveDataBaseUrl + "/quote")
                    .queryParam("symbol", batchString)
                    .queryParam("apikey", twelveDataApiKey)
                    .toUriString();

            try {
                // Rate control delay to prevent instant 429
                if (requestCount > 0) Thread.sleep(1000); 
                
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                requestCount++;
                
                if (response == null) continue;

                // Handle global error envelope for the whole batch
                if (response.containsKey("code") && !response.containsKey("close")) {
                    Object codeVal = response.get("code");
                    log.warn("TwelveData batch global error: code={} msg={}", codeVal, response.get("message"));
                    if ("429".equals(String.valueOf(codeVal))) {
                        // Provider-wide outage broadcast (was MISSING on the batch path).
                        // A 429 here is account-wide, so disable TwelveData for ALL symbols
                        // for the cooldown window — mirroring the single-quote path. Without
                        // this, hydrateMarketMirror re-fired the batch every cycle and kept
                        // re-triggering 429s: the cascading-outage bug.
                        policyService.markProviderDisabled(MarketDataPolicyService.PROVIDER_TWELVEDATA);
                        for (String tSym : chunk) {
                            String normalized = reverseMap.get(tSym);
                            if (normalized != null) markFailureCooldown(normalized);
                        }
                    }
                    continue;
                }

                // Twelve Data returns a single Map if only 1 symbol, or Map<Symbol, Map> if multiple
                if (chunk.size() == 1) {
                    processSingleTwelveQuote(response, reverseMap, allResults);
                } else {
                    for (String tSym : chunk) {
                        Object symData = response.get(tSym);
                        if (symData instanceof Map) {
                            processSingleTwelveQuote((Map<String, Object>) symData, reverseMap, allResults);
                        }
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Twelve Data Batch failed for chunk {}: {}", batchString, e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    // Provider-wide outage broadcast (was MISSING on the batch path).
                    // Mirror the single-quote path so an HTTP-level 429 also disables
                    // TwelveData globally, stopping the every-cycle retry storm.
                    policyService.markProviderDisabled(MarketDataPolicyService.PROVIDER_TWELVEDATA);
                    for (String tSym : chunk) {
                        String normalized = reverseMap.get(tSym);
                        if (normalized != null) markFailureCooldown(normalized);
                    }
                }
            }
        }
        return allResults;
    }

    private void processSingleTwelveQuote(Map<String, Object> data, Map<String, String> reverseMap, Map<String, Map<String, Object>> results) {
        if (data == null || !data.containsKey("symbol") || !data.containsKey("close")) {
            if (data != null && !data.containsKey("code")) {
                log.warn("TwelveData batch sub-map missing 'symbol' or 'close'. Keys present: {}", data.keySet());
            }
            return;
        }

        // TwelveData returns error envelopes as HTTP 200 with a "code" field
        // (e.g. {"code": 429, "message": "You have run out of API credits..."}).
        // Reject these before they corrupt the Redis cache with zero/garbage prices.
        if (data.containsKey("code")) {
            Object codeVal = data.get("code");
            log.warn("TwelveData error envelope for {}: code={} msg={}",
                    data.get("symbol"), codeVal, data.get("message"));
            // 429 in a batch response is account-wide:
            //   - per-symbol cooldown stops this symbol re-entering next tick
            //   - provider-wide outage stops the WHOLE scheduler from calling TwelveData
            //     for the next 15 min (governance broadcast)
            if ("429".equals(String.valueOf(codeVal))) {
                String tSym = data.get("symbol") != null ? data.get("symbol").toString() : null;
                String normalized = tSym != null ? reverseMap.get(tSym) : null;
                if (normalized != null) markFailureCooldown(normalized);
                policyService.markProviderDisabled(MarketDataPolicyService.PROVIDER_TWELVEDATA);
            }
            return;
        }

        String tSym = data.get("symbol").toString();
        String normalized = reverseMap.get(tSym);
        if (normalized == null) return;

        double price = safeParseDouble(data.get("close"));
        // A zero or negative price means either parse failure or a TwelveData placeholder.
        // Writing price=0 to Redis silently corrupts portfolio valuations.
        if (price <= 0) {
            log.warn("TwelveData returned non-positive price ({}) for {}. Skipping cache write.", price, normalized);
            return;
        }

        double changePct = safeParseDouble(data.getOrDefault("percent_change", "0"));

        Map<String, Object> quote = new HashMap<>();
        quote.put("symbol", normalized);
        quote.put("price", price);
        quote.put("changesPercentage", Math.round(changePct * 100.0) / 100.0);
        quote.put("source", "twelvedata_batch");
        results.put(normalized, quote);
    }

    @CircuitBreaker(name = "finnhub", fallbackMethod = "listFallback")
    @RateLimiter(name = "finnhub")
    @Retry(name = "default")
    public List<Map<String, Object>> searchStocks(String query) {
        String normalized = query == null ? "" : query;
        String url = UriComponentsBuilder.fromHttpUrl(finnhubBaseUrl + "/search")
                .queryParam("q", normalized)
                .queryParam("token", finnhubApiKey)
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || !response.containsKey("result")) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> searchResults = (List<Map<String, Object>>) response.get("result");
        if (searchResults == null || searchResults.isEmpty()) {
            return Collections.emptyList();
        }

        return searchResults.stream()
                .filter(r -> "Common Stock".equals(r.get("type")))
                .limit(5)
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("symbol", r.get("symbol"));
                    map.put("name", r.get("description"));
                    return map;
                })
                .toList();
    }

    @CircuitBreaker(name = "twelvedata", fallbackMethod = "listFallback")
    @RateLimiter(name = "twelvedata")
    @Retry(name = "default")
    public List<Map<String, Object>> fetchStockCandles(String symbol) {
        // MOCK MODE check
        if ("mock".equalsIgnoreCase(finnhubApiKey) || "mock".equalsIgnoreCase(twelveDataApiKey)) {
            return generateMockCandles(symbol);
        }
        // Finnhub candle is now premium only. Twelve Data is our primary for charts.
        return fetchTwelveDataCandles(symbol);
    }

    private List<Map<String, Object>> generateMockCandles(String symbol) {
        List<Map<String, Object>> list = new ArrayList<>();
        double base = Math.abs(symbol.hashCode() % 500) + 50.0;
        for (int i = 30; i >= 0; i--) {
            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", LocalDate.now().minusDays(i).toString());
            double price = base + (Math.sin(i * 0.5) * 10.0);
            point.put("price", Math.round(price * 100.0) / 100.0);
            point.put("value", Math.round(price * 100.0) / 100.0);
            list.add(point);
        }
        return list;
    }

    public List<Map<String, Object>> fetchIndianStockCandles(String symbol) {
        // Redirecting to Twelve Data for consistency and quota protection
        return fetchTwelveDataCandles(symbol);
    }

    private boolean isFresh(String symbol) {
        try {
            Object ts = redisTemplate.opsForValue().get(LAST_UPDATED_TS_PREFIX + symbol);
            if (ts == null) return false;
            long lastUpdatedMs = Long.parseLong(ts.toString());
            return (System.currentTimeMillis() - lastUpdatedMs) < FRESHNESS_THRESHOLD_MS;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> getCachedQuoteIfFresh(String symbol) {
        if (!isFresh(symbol)) return null;
        try {
            Object data = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + symbol);
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
        } catch (Exception e) {
            log.debug("Cache read failed for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    private double safeParseDouble(Object v) {
        if (v == null) return 0.0;
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
