package com.example.stockPortfolio.MarketManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketGateway {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SymbolNormalizer symbolNormalizer;
    private final org.springframework.cache.CacheManager l1CacheManager;

    public SymbolNormalizer getSymbolNormalizer() {
        return symbolNormalizer;
    }
    
    private static final String PRIORITY_SET_KEY = "market:priority_symbols";
    private static final String EXPLAIN_SET_KEY = "market:explain_priorities";
    private static final String ACTIVE_SYMBOLS_SET_KEY = "market:active_symbols";
    
    private static final int MAX_PRIORITY_SIZE = 100;
    private static final int STALE_THRESHOLD_MINUTES = 10;
    private static final long FRESHNESS_THRESHOLD_MS = 10 * 60 * 1000L; // 10 minutes
    private static final String LAST_UPDATED_TS_PREFIX = "stock:lastUpdated:";

    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final String LAST_CLOSE_PREFIX = "last_close:";
    private static final String NEWS_KEY_PREFIX = "news:";
    private static final String CHART_KEY_PREFIX = "chart:";
    private static final String FINANCIALS_KEY_PREFIX = "financials:";
    private static final String AI_INSIGHT_PREFIX = "insight:";
    private static final String USER_INSIGHT_PREFIX = "user_insight:";
    private static final String ONBOARDING_KEY_PREFIX = "onboarding:";

    /**
     * Marks a symbol for urgent hydration.
     * Only adds if not already present and limit not reached.
     */
    public void markAsPriority(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized != null) {
            Long size = redisTemplate.opsForSet().size(PRIORITY_SET_KEY);
            if (size != null && size < MAX_PRIORITY_SIZE) {
                redisTemplate.opsForSet().add(PRIORITY_SET_KEY, normalized);
            } else if (size != null) {
                log.warn("Priority queue full ({}). Skipping symbol: {}", MAX_PRIORITY_SIZE, normalized);
            }
        }
    }

    /**
     * Retrieves all symbols currently marked as priority.
     */
    public Set<String> getPrioritySymbols() {
        Set<Object> members = redisTemplate.opsForSet().members(PRIORITY_SET_KEY);
        if (members == null) return Collections.emptySet();
        Set<String> result = new HashSet<>();
        for (Object m : members) {
            if (m instanceof String) result.add((String) m);
        }
        return result;
    }

    /**
     * Clears processed symbols from the priority list.
     */
    public void clearPrioritySymbols(Collection<String> symbols) {
        if (symbols != null && !symbols.isEmpty()) {
            redisTemplate.opsForSet().remove(PRIORITY_SET_KEY, symbols.toArray());
        }
    }

    /**
     * Returns true if the symbol was updated within the freshness threshold (10 min).
     * Used by the scheduler and gateway to skip redundant API calls.
     */
    public boolean isFresh(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) return false;
        try {
            Object ts = redisTemplate.opsForValue().get(LAST_UPDATED_TS_PREFIX + normalized);
            if (ts == null) return false;
            long lastUpdatedMs = Long.parseLong(ts.toString());
            return (System.currentTimeMillis() - lastUpdatedMs) < FRESHNESS_THRESHOLD_MS;
        } catch (Exception e) {
            log.warn("Freshness check failed for {}: {}", normalized, e.getMessage());
            return false;
        }
    }

    /**
     * Stamps the current time as the last successful update for this symbol.
     * Called after a successful external API fetch.
     */
    public void markFreshTimestamp(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) return;
        redisTemplate.opsForValue().set(
                LAST_UPDATED_TS_PREFIX + normalized,
                String.valueOf(System.currentTimeMillis()),
                30, TimeUnit.MINUTES
        );
    }

    /**
     * Returns cached quote data if the symbol is fresh, null otherwise.
     * Allows the external gateway to short-circuit API calls.
     */
    public Map<String, Object> getCachedQuoteIfFresh(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null || !isFresh(normalized)) return null;
        try {
            Object data = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + normalized);
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
        } catch (Exception e) {
            log.warn("Cache read failed for {}: {}", normalized, e.getMessage());
        }
        return null;
    }

    /**
     * Marks an AI explanation for urgent generation.
     */
    public void markExplainAsPriority(com.example.stockPortfolio.AiManagement.ExplainRequestDTO request) {
        if (request != null) {
            Long size = redisTemplate.opsForSet().size(EXPLAIN_SET_KEY);
            if (size != null && size < MAX_PRIORITY_SIZE) {
                redisTemplate.opsForSet().add(EXPLAIN_SET_KEY, request);
            }
        }
    }

    /**
     * Retrieves all pending AI explanation priorities.
     */
    public Set<com.example.stockPortfolio.AiManagement.ExplainRequestDTO> getExplainPriorities() {
        Set<Object> members = redisTemplate.opsForSet().members(EXPLAIN_SET_KEY);
        if (members == null) return Collections.emptySet();
        Set<com.example.stockPortfolio.AiManagement.ExplainRequestDTO> result = new HashSet<>();
        for (Object m : members) {
            if (m instanceof com.example.stockPortfolio.AiManagement.ExplainRequestDTO) {
                result.add((com.example.stockPortfolio.AiManagement.ExplainRequestDTO) m);
            }
        }
        return result;
    }

    /**
     * Clears processed AI explanation priorities.
     */
    public void clearExplainPriorities(Collection<com.example.stockPortfolio.AiManagement.ExplainRequestDTO> requests) {
        if (requests != null && !requests.isEmpty()) {
            redisTemplate.opsForSet().remove(EXPLAIN_SET_KEY, requests.toArray());
        }
    }

    /**
     * Reads precomputed AI insight from Redis.
     */
    public String getPrecomputedInsight(String type, String subType) {
        String key = AI_INSIGHT_PREFIX + type + ":" + safeKeyPart(subType);
        try {
            return readStringValue(key);
        } catch (Exception e) {
            log.error("Redis AI Insight Read Error for {}:{}: {}", type, subType, e.getMessage());
        }
        return null;
    }

    /**
     * Writes precomputed AI insight to Redis.
     */
    public void updatePrecomputedInsight(String type, String subType, String content) {
        String key = AI_INSIGHT_PREFIX + type + ":" + safeKeyPart(subType);
        writeStringValue(key, content, 1, TimeUnit.HOURS);
    }

    public String getOnboardingScenario(String userType) {
        return readStringValue(onboardingKey("scenario", userType));
    }

    public void updateOnboardingScenario(String userType, String content) {
        writeStringValue(onboardingKey("scenario", userType), content, 1, TimeUnit.HOURS);
    }

    public String getOnboardingFeedback(String userKey) {
        return readStringValue(onboardingKey("feedback", userKey));
    }

    public void updateOnboardingFeedback(String userKey, String content) {
        writeStringValue(onboardingKey("feedback", userKey), content, 1, TimeUnit.HOURS);
    }

    public String getOnboardingSummary(String sessionId) {
        return readStringValue(onboardingKey("summary", sessionId));
    }

    public void updateOnboardingSummary(String sessionId, String content) {
        writeStringValue(onboardingKey("summary", sessionId), content, 1, TimeUnit.HOURS);
    }

    /**
     * Reads user-specific precomputed AI insight.
     */
    public Object getUserInsight(Long userId, String type) {
        String key = USER_INSIGHT_PREFIX + userId + ":" + type;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Writes user-specific precomputed AI insight.
     */
    public void updateUserInsight(Long userId, String type, Object data, int hours) {
        String key = USER_INSIGHT_PREFIX + userId + ":" + type;
        redisTemplate.opsForValue().set(key, data, hours, TimeUnit.HOURS);
    }

    /**
     * Reads multiple stock quotes with at most TWO Redis roundtrips (one MGET on
     * {@code stock:*} keys, one MGET on {@code last_close:*} keys for any misses),
     * plus an in-process L1 (Caffeine) check for indices.
     *
     * <p>Previously this method looped {@link #getStockQuote(String)} per symbol,
     * which produced N Redis GETs on the hot path of the holdings/alerts/market
     * endpoints. On free-tier Redis (Upstash HTTP, ~50 ms per call) that meant a
     * 20-symbol portfolio paid ~1 s just on Redis network time. Two MGETs reduce
     * the same workload to ~100 ms regardless of portfolio size.
     *
     * <p>Fallback chain mirrors {@link #getLatestQuote(String)}:
     * L1 indices cache → Redis {@code stock:*} → Redis {@code last_close:*} → emergency mock.
     *
     * @param symbols caller-supplied symbol list; output map is keyed by these same strings
     *                (preserves caller's casing) so existing call sites don't break.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> getBatchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyMap();

        // Normalize once; remember the original input string so the result map
        // is keyed exactly like the caller asked (callers use Holding.getSymbol()
        // which is already upper-cased, but be defensive).
        List<String> normalizedSymbols = new ArrayList<>(symbols.size());
        Map<String, String> originalBySymbol = new LinkedHashMap<>();
        for (String s : symbols) {
            String n = normalizeSymbol(s);
            if (n != null && !originalBySymbol.containsKey(n)) {
                normalizedSymbols.add(n);
                originalBySymbol.put(n, s);
            }
        }
        if (normalizedSymbols.isEmpty()) return Collections.emptyMap();

        Map<String, Map<String, Object>> results = new LinkedHashMap<>();

        // Pass 1: L1 (Caffeine) cache for indices — avoids Redis altogether for
        // the handful of indices we keep super-hot.
        org.springframework.cache.Cache indexCache = l1CacheManager.getCache("indices");
        List<String> needRedis = new ArrayList<>(normalizedSymbols.size());
        for (String n : normalizedSymbols) {
            if (indexCache != null && symbolNormalizer.isIndex(n)) {
                Map<String, Object> data = indexCache.get(n, Map.class);
                if (data != null) {
                    results.put(originalBySymbol.get(n), data);
                    continue;
                }
            }
            needRedis.add(n);
        }
        if (needRedis.isEmpty()) return results;

        // Pass 2: ONE Redis MGET on stock:* — replaces N individual GETs.
        List<String> stockKeys = needRedis.stream().map(n -> STOCK_KEY_PREFIX + n).toList();
        List<Object> stockValues;
        try {
            stockValues = redisTemplate.opsForValue().multiGet(stockKeys);
        } catch (Exception e) {
            log.error("Redis MGET (stock) failed for {} symbols: {}", stockKeys.size(), e.getMessage());
            stockValues = null;
        }

        List<String> needFallback = new ArrayList<>();
        for (int i = 0; i < needRedis.size(); i++) {
            Object data = (stockValues != null && i < stockValues.size()) ? stockValues.get(i) : null;
            if (data instanceof Map) {
                Map<String, Object> quote = (Map<String, Object>) data;
                results.put(originalBySymbol.get(needRedis.get(i)), quote);
                // Warm L1 for indices so the next request skips Redis entirely.
                if (indexCache != null && symbolNormalizer.isIndex(needRedis.get(i))) {
                    indexCache.put(needRedis.get(i), quote);
                }
            } else {
                needFallback.add(needRedis.get(i));
            }
        }

        // Pass 3: ONE Redis MGET on last_close:* for everything still missing.
        if (!needFallback.isEmpty()) {
            List<String> closeKeys = needFallback.stream().map(n -> LAST_CLOSE_PREFIX + n).toList();
            List<Object> closeValues;
            try {
                closeValues = redisTemplate.opsForValue().multiGet(closeKeys);
            } catch (Exception e) {
                log.error("Redis MGET (last_close) failed for {} symbols: {}", closeKeys.size(), e.getMessage());
                closeValues = null;
            }

            for (int i = 0; i < needFallback.size(); i++) {
                String n = needFallback.get(i);
                Object data = (closeValues != null && i < closeValues.size()) ? closeValues.get(i) : null;
                if (data instanceof Map) {
                    results.put(originalBySymbol.get(n), (Map<String, Object>) data);
                } else {
                    // Pass 4: No legitimate data found. Mark for urgent hydration and return null.
                    // Callers (like getMarketData) will filter out nulls or handle them as SYNCING.
                    log.info("Deep fallback failed for {}. Queueing for hydration.", n);
                    markAsPriority(n);
                }
            }
        }

        return results;
    }
    /**
     * Reads stock data directly from Redis as a Map.
     */
    public ApiResponse<Map<String, Object>> getStockQuote(String symbol) {
        ApiResponse<Map<String, Object>> response = getLatestQuote(symbol);
        if (response != null) {
            return response;
        }
        return ApiResponse.syncing(Collections.emptyMap(), "Data unavailable", "fallback");
    }

    private final ExternalMarketDataGateway externalMarketDataGateway;

    /**
     * Reads stock data directly from Redis. 
     * Fallback Chain: L1 Cache -> L2 Redis (Mirror) -> L2 Redis (Last Close) -> Emergency Mock
     */
    public ApiResponse<Map<String, Object>> getLatestQuote(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return ApiResponse.syncing(null, "Unsupported symbol", "FALLBACK");
        }

        // 1. Check L1 Cache (In-Memory) for Indices
        if (symbolNormalizer.isIndex(normalized)) {
            org.springframework.cache.Cache indexCache = l1CacheManager.getCache("indices");
            if (indexCache != null) {
                Map<String, Object> data = indexCache.get(normalized, Map.class);
                if (data != null) {
                    return createOkResponse(data, "LIVE");
                }
            }
        }

        String key = STOCK_KEY_PREFIX + normalized;
        try {
            // 2. Check L2 Cache (Redis Mirror)
            Object data = redisTemplate.opsForValue().get(key);
            if (data instanceof Map) {
                Map<String, Object> quoteMap = (Map<String, Object>) data;
                
                // If it's an index, populate L1
                if (symbolNormalizer.isIndex(normalized)) {
                    org.springframework.cache.Cache indexCache = l1CacheManager.getCache("indices");
                    if (indexCache != null) indexCache.put(normalized, quoteMap);
                }
                
                return createOkResponse(quoteMap, "LIVE");
            }

            // 3. Fallback: Last Close (Stable Fallback for Off-Hours or API failures)
            Object lastClose = redisTemplate.opsForValue().get(LAST_CLOSE_PREFIX + normalized);
            if (lastClose instanceof Map) {
                return createOkResponse((Map<String, Object>) lastClose, "SNAPSHOT");
            }
        } catch (Exception e) {
            log.error("Redis Read Error for {}: {}", symbol, e.getMessage());
        }

        // 4. Final Failover: Queue for hydration and return SYNCING. 
        // We no longer generate fake/mock quotes during the request cycle.
        log.info("Symbol {} not in cache. Queueing for hydration and returning SYNCING.", normalized);
        
        // Mark as priority for the next scheduler cycle to fetch real data
        markAsPriority(normalized);

        return ApiResponse.syncing(null, "Price data is being synced. Please wait.", "SYNCING");
    }

    private ApiResponse<Map<String, Object>> createOkResponse(Map<String, Object> quoteMap, String source) {
        java.time.LocalDateTime lastUpdated = java.time.LocalDateTime.now();
        String freshness = "fresh";
        // All cached/mirror data is delayed by definition (scheduler-driven, not real-time)
        boolean isDelayed = true;

        if (quoteMap.containsKey("lastUpdated")) {
            try {
                lastUpdated = java.time.LocalDateTime.parse(quoteMap.get("lastUpdated").toString());
                if (lastUpdated.isBefore(java.time.LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES))) {
                    freshness = "stale";
                }
            } catch (Exception e) {
                log.warn("Failed to parse lastUpdated: {}", e.getMessage());
            }
        }

        String resolvedSource = "LIVE".equals(source) ? "CACHE" : source;

        return ApiResponse.ok(quoteMap, ApiResponse.Meta.builder()
                .status("OK")
                .message("Data fetched from " + resolvedSource)
                .source(resolvedSource)
                .lastUpdated(lastUpdated)
                .freshness(freshness)
                .isDelayed(isDelayed)
                .build());
    }

    /**
     * Reads news data from Redis.
     */
    public ApiResponse<List<Map<String, Object>>> getLatestNews(String query) {
        String key = NEWS_KEY_PREFIX + (query == null ? "market" : query.toLowerCase(Locale.ROOT));
        try {
            Object data = redisTemplate.opsForValue().get(key);
            if (data instanceof List) {
                return ApiResponse.ok((List<Map<String, Object>>) data, ApiResponse.Meta.builder()
                        .status("OK")
                        .message("News fetched from cache")
                        .source("cache")
                        .lastUpdated(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.error("Redis News Read Error: {}", e.getMessage());
        }
        
        return ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .data(Collections.emptyList())
                .meta(ApiResponse.Meta.builder()
                        .status("SYNCING")
                        .message("News syncing...")
                        .source("fallback")
                        .lastUpdated(LocalDateTime.now())
                        .build())
                .build();
    }

    /**
     * Reads chart data from Redis with timeframe support.
     */
    public ApiResponse<List<Map<String, Object>>> getChartData(String symbol, String timeframe) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return ApiResponse.syncing(Collections.emptyList(), "Unsupported symbol", "fallback");
        }

        // Key strategy: Default (1D/1W/1M) is "chart:AAPL", Deep history is "chart:1Y:AAPL" or "chart:5Y:AAPL"
        String key = (timeframe == null || timeframe.equals("1D") || timeframe.equals("1W") || timeframe.equals("1M"))
                ? CHART_KEY_PREFIX + normalized
                : CHART_KEY_PREFIX + timeframe + ":" + normalized;

        try {
            Object data = redisTemplate.opsForValue().get(key);
            if (data instanceof List) {
                return ApiResponse.ok((List<Map<String, Object>>) data, ApiResponse.Meta.builder()
                        .status("OK")
                        .message("Chart " + (timeframe != null ? timeframe : "1M") + " fetched from cache")
                        .source("cache")
                        .lastUpdated(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.error("Redis Chart Read Error for {} {}: {}", symbol, timeframe, e.getMessage());
        }

        return ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .data(Collections.emptyList())
                .meta(ApiResponse.Meta.builder()
                        .status("SYNCING")
                        .message("Chart syncing for " + (timeframe != null ? timeframe : "1M") + "...")
                        .source("fallback")
                        .lastUpdated(LocalDateTime.now())
                        .build())
                .build();
    }

    // Keep legacy method for backward compatibility
    public ApiResponse<List<Map<String, Object>>> getChartData(String symbol) {
        return getChartData(symbol, "1M");
    }

    private final StockHistoryRepo stockHistoryRepo;

    /**
     * Internal method used only by the Scheduler to update the "Mirror".
     */
    public void updateMirror(String symbol, Map<String, Object> data) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null || data == null) {
            return;
        }
        
        // Inject lastUpdated for freshness tracking
        data.put("lastUpdated", LocalDateTime.now().toString());
        
        String key = STOCK_KEY_PREFIX + normalized;
        redisTemplate.opsForValue().set(key, data, 30, TimeUnit.MINUTES);

        // Stamp freshness so the scheduler and gateway can skip redundant fetches
        markFreshTimestamp(normalized);

        // Also update Last Close as a high-quality fallback (7 days TTL for durability)
        redisTemplate.opsForValue().set(LAST_CLOSE_PREFIX + normalized, data, 7, TimeUnit.DAYS);
        
        // Sync L1 Cache (Caffeine) to prevent staleness on this node
        if (symbolNormalizer.isIndex(normalized)) {
            org.springframework.cache.Cache indexCache = l1CacheManager.getCache("indices");
            if (indexCache != null) indexCache.put(normalized, data);
        }
        
        // Track as active symbol for local search
        redisTemplate.opsForSet().add(ACTIVE_SYMBOLS_SET_KEY, normalized);

        // DAILY PERSISTENCE: Save to stock_history ONLY if it doesn't exist for today
        try {
            java.time.LocalDate today = java.time.LocalDate.now();
            String market = symbolNormalizer.isIndian(normalized) ? "INDIA" : "US";
            
            if (!stockHistoryRepo.existsBySymbolAndDate(normalized, today)) {
                double price = Double.parseDouble(data.get("price").toString());
                StockHistory history = StockHistory.builder()
                        .symbol(normalized)
                        .market(market)
                        .date(today)
                        .close(java.math.BigDecimal.valueOf(price))
                        .isSimulated(false)
                        .build();
                stockHistoryRepo.save(history);
                log.info("Persisted daily history for {} ({}): {}", normalized, market, price);
            }
        } catch (Exception e) {
            log.warn("Failed to persist daily history for {}: {}", normalized, e.getMessage());
        }
    }

    /**
     * Reads financial metrics from Redis.
     */
    public ApiResponse<Map<String, Object>> getFinancials(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return ApiResponse.syncing(Collections.emptyMap(), "Unsupported symbol", "fallback");
        }

        try {
            Object data = redisTemplate.opsForValue().get(FINANCIALS_KEY_PREFIX + normalized);
            if (data instanceof Map) {
                return ApiResponse.ok((Map<String, Object>) data, ApiResponse.Meta.builder()
                        .status("OK")
                        .message("Financials fetched from cache")
                        .source("cache")
                        .lastUpdated(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.error("Redis Financials Read Error for {}: {}", symbol, e.getMessage());
        }
        return ApiResponse.syncing(Collections.emptyMap(), "Financials syncing...", "fallback");
    }

    /**
     * Internal method used only by the Scheduler to update financial metrics.
     */
    public void updateFinancialsMirror(String symbol, Map<String, Object> financials) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null || financials == null) return;
        
        redisTemplate.opsForValue().set(FINANCIALS_KEY_PREFIX + normalized, financials, 24, TimeUnit.HOURS);
    }

    /**
     * Internal method used only by the Scheduler to update news in the mirror.
     */
    public void updateNewsMirror(String query, List<Map<String, Object>> news) {
        String key = NEWS_KEY_PREFIX + (query == null ? "market" : query.toLowerCase(Locale.ROOT));
        redisTemplate.opsForValue().set(key, news, 30, TimeUnit.MINUTES);
    }

    /**
     * Internal method used only by the Scheduler to update chart data in the mirror.
     */
    public void updateChartMirror(String symbol, List<Map<String, Object>> chartData) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null || chartData == null) {
            return;
        }
        String key = CHART_KEY_PREFIX + normalized;
        redisTemplate.opsForValue().set(key, chartData, 30, TimeUnit.MINUTES);
    }

    /**
     * Internal method used only by the Scheduler to update deep historical chart data.
     */
    public void updateDeepChartMirror(String symbol, String timeframe, List<Map<String, Object>> chartData) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null || chartData == null || timeframe == null) {
            return;
        }
        String key = CHART_KEY_PREFIX + timeframe + ":" + normalized;
        // Deep history is static, cache for 24 hours
        redisTemplate.opsForValue().set(key, chartData, 24, TimeUnit.HOURS);
    }

    /**
     * Searches our local mirrored universe for symbols matching the query.
     * High Reliability: Pulls from mirror, falls back to last_close, queues stale symbols.
     */
    public List<Map<String, Object>> searchLocalMirror(String query) {
        try {
            String safeQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
            
            // 1. Identify matching symbols via SCAN
            List<String> matchingSymbols = new ArrayList<>();
            redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.sScan(ACTIVE_SYMBOLS_SET_KEY.getBytes(), 
                        org.springframework.data.redis.core.ScanOptions.scanOptions().match("*").count(1000).build())) {
                    while (cursor.hasNext() && matchingSymbols.size() < 20) {
                        String symbol = new String(cursor.next());
                        if (symbol.toLowerCase(Locale.ROOT).contains(safeQuery)) {
                            matchingSymbols.add(symbol);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error scanning active symbols: {}", e.getMessage());
                }
                return null;
            });

            if (matchingSymbols.isEmpty()) return Collections.emptyList();

            // 2. Bulk fetch latest quotes
            Map<String, Map<String, Object>> batch = getBatchQuotes(matchingSymbols);
            List<Map<String, Object>> results = new ArrayList<>();

            for (String s : matchingSymbols) {
                Map<String, Object> quote = batch.get(s);
                if (quote != null) {
                    // Enrich search result with price/change
                    Map<String, Object> result = new HashMap<>(quote);
                    result.put("name", quote.getOrDefault("name", quote.getOrDefault("companyName", s)));
                    results.add(result);
                }
            }

            return results;
        } catch (Exception e) {
            log.error("Local search error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static final String TOP_MOVERS_KEY_PREFIX = "market:movers:";

    public void updateTopMovers(String type, List<Map<String, Object>> data) {
        if (type == null || data == null) return;
        redisTemplate.opsForValue().set(TOP_MOVERS_KEY_PREFIX + type.toLowerCase(), data, 10, TimeUnit.MINUTES);
    }

    public List<Map<String, Object>> getTopMovers(String type) {
        Object data = redisTemplate.opsForValue().get(TOP_MOVERS_KEY_PREFIX + type.toLowerCase());
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        return Collections.emptyList();
    }

    private String normalizeSymbol(String symbol) {
        return symbolNormalizer.normalize(symbol);
    }

    private String onboardingKey(String kind, String keyPart) {
        String normalizedKey = keyPart == null || keyPart.isBlank()
                ? "default"
                : keyPart.trim().toLowerCase(Locale.ROOT);
        return ONBOARDING_KEY_PREFIX + kind + ":" + normalizedKey;
    }

    private String readStringValue(String key) {
        Object data = redisTemplate.opsForValue().get(key);
        return data instanceof String ? (String) data : null;
    }

    private void writeStringValue(String key, String content, long ttl, TimeUnit unit) {
        if (content == null) {
            return;
        }
        redisTemplate.opsForValue().set(key, content, ttl, unit);
    }

    private String safeKeyPart(String value) {
        return value == null || value.isBlank()
                ? "default"
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
