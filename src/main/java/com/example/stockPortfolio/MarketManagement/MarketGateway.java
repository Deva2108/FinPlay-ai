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
     * Reads multiple stock quotes in a single Redis call.
     */
    public Map<String, Map<String, Object>> getBatchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyMap();

        Map<String, Map<String, Object>> results = new HashMap<>();
        for (String s : symbols) {
            Map<String, Object> quote = getStockQuote(s);
            if (quote != null) {
                results.put(s, quote);
            }
        }
        return results;
    }
    /**
     * Reads stock data directly from Redis as a Map.
     */
    public Map<String, Object> getStockQuote(String symbol) {
        com.example.stockPortfolio.HoldingsManagement.ApiResponse<Map<String, Object>> response = getLatestQuote(symbol);
        if (response != null && response.isSuccess()) {
            return response.getData();
        }
        return null;
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

        // 4. Final Failover: Emergency Mock (Guarantees NO NULL and price consistency)
        log.warn("Deep failover triggered for symbol: {}. Serving emergency mock.", normalized);
        Map<String, Object> mock = externalMarketDataGateway.generateMockQuote(normalized);
        
        // Mark as priority for the next scheduler cycle to try and get real data
        markAsPriority(normalized);

        return createOkResponse(mock, "FALLBACK");
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

        // Also update Last Close as a high-quality fallback (24 hours TTL)
        redisTemplate.opsForValue().set(LAST_CLOSE_PREFIX + normalized, data, 24, TimeUnit.HOURS);
        
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
            if (!stockHistoryRepo.existsBySymbolAndDate(normalized, today)) {
                double price = Double.parseDouble(data.get("price").toString());
                StockHistory history = StockHistory.builder()
                        .symbol(normalized)
                        .date(today)
                        .close(java.math.BigDecimal.valueOf(price))
                        .isSimulated(false)
                        .build();
                stockHistoryRepo.save(history);
                log.info("Persisted daily history for {}: {}", normalized, price);
            }
        } catch (Exception e) {
            log.warn("Failed to persist daily history for {}: {}", normalized, e.getMessage());
        }
    }

    /**
     * Reads financial metrics from Redis.
     */
    public Map<String, Object> getFinancials(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) return Collections.emptyMap();
        
        try {
            Object data = redisTemplate.opsForValue().get(FINANCIALS_KEY_PREFIX + normalized);
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
        } catch (Exception e) {
            log.error("Redis Financials Read Error for {}: {}", symbol, e.getMessage());
        }
        return Collections.emptyMap();
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
     * This replaces the live Finnhub search to avoid rate limits.
     */
    public List<Map<String, Object>> searchLocalMirror(String query) {
        try {
            String safeQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
            
            // Optimization: Use SCAN instead of MEMBERS to avoid blocking Redis if the set is huge
            List<String> matchingKeys = new ArrayList<>();
            redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.sScan(ACTIVE_SYMBOLS_SET_KEY.getBytes(), 
                        org.springframework.data.redis.core.ScanOptions.scanOptions().match("*").count(1000).build())) {
                    while (cursor.hasNext() && matchingKeys.size() < 20) {
                        String symbol = new String(cursor.next());
                        if (symbol.toLowerCase(Locale.ROOT).contains(safeQuery)) {
                            matchingKeys.add(STOCK_KEY_PREFIX + symbol);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error scanning active symbols: {}", e.getMessage());
                }
                return null;
            });

            if (matchingKeys.isEmpty()) return Collections.emptyList();

            List<Object> values = redisTemplate.opsForValue().multiGet(matchingKeys);
            return values.stream()
                    .filter(v -> v instanceof Map)
                    .map(v -> (Map<String, Object>) v)
                    .limit(10)
                    .toList();
        } catch (Exception e) {
            log.error("Local search error: {}", e.getMessage());
            return Collections.emptyList();
        }
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
