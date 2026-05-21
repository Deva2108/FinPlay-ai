package com.example.stockPortfolio.MarketManagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Fallback historical / indicator service backed by Alpha Vantage.
 *
 * <p>Free tier: 5 calls/min, 25/day. We enforce the 5/min cap with a tiny
 * in-process token bucket. Results are written to Redis with a 12-hour TTL so
 * the daily quota isn't burned up on every request.
 *
 * <p>Used only when Finnhub/Yahoo are unavailable for a given symbol.
 *
 * <h3>Threading model (Phase 3 rewrite)</h3>
 * The HTTP call is performed via {@link WebClient} on Reactor Netty — fully
 * non-blocking. Tomcat workers are not held for the duration of the upstream
 * request. The Redis cache check is wrapped in {@code Mono.fromCallable} on
 * {@link Schedulers#boundedElastic()} so the blocking Lettuce sync API doesn't
 * stall the event loop.
 *
 * <p>Two public APIs are offered:
 * <ul>
 *   <li>{@link #getDailySeriesAsync} / {@link #getSmaAsync} — return
 *       {@code Mono<...>} for reactive chaining; <em>never</em> blocks a
 *       Tomcat worker.</li>
 *   <li>{@link #getDailySeries} / {@link #getSma} — sync overloads for legacy
 *       callers. They internally subscribe with a bounded {@code block(12 s)}
 *       so behavior is identical to the old RestTemplate code from the
 *       caller's perspective, but the underlying client is async + pooled.</li>
 * </ul>
 */
@Service
@Slf4j
public class AlphaVantageService {

    private static final String BASE_URL = "https://www.alphavantage.co/query";
    private static final String DAILY_KEY_PREFIX = "alphav:daily:";
    private static final String SMA_KEY_PREFIX = "alphav:sma:";
    private static final long TTL_HOURS = 12;
    private static final int MAX_CALLS_PER_MIN = 5;
    /** Upper bound when a sync caller invokes the *blocking* overload. */
    private static final Duration SYNC_BLOCK_TIMEOUT = Duration.ofSeconds(12);

    private final WebClient webClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${ALPHA_VANTAGE_KEY:${alpha.vantage.key:}}")
    private String apiKey;

    private final Object rateLock = new Object();
    private final List<Instant> recentCalls = new ArrayList<>();

    public AlphaVantageService(WebClient.Builder webClientBuilder,
                               RedisTemplate<String, Object> redisTemplate) {
        // Bake the base URL into the builder so the per-call code is a single
        // mutate(query) chain. Builder itself comes from WebClientConfig and
        // carries the 5s connect / 10s read timeouts.
        this.webClient = webClientBuilder.baseUrl(BASE_URL).build();
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Alpha Vantage key missing. Fallback historical/indicator data is disabled.");
        }
    }

    // =====================================================================
    // Public API — reactive (preferred)
    // =====================================================================

    /**
     * Non-blocking daily OHLC fetch. Reads from Redis cache first; only calls
     * the API on a cache miss. Never holds a Tomcat worker for the network
     * roundtrip.
     */
    public Mono<List<Map<String, Object>>> getDailySeriesAsync(String symbol, int limit) {
        if (symbol == null || symbol.isBlank()) return Mono.just(List.of());
        String key = DAILY_KEY_PREFIX + symbol.toUpperCase();

        return readCachedList(key)
                .flatMap(cached -> {
                    if (cached != null) {
                        return Mono.just(clampAndCast(cached, limit));
                    }
                    if (!acquireToken()) {
                        log.debug("Alpha Vantage rate limit reached; serving empty for {}.", symbol);
                        return Mono.just(List.<Map<String, Object>>of());
                    }
                    return fetchDailyFromApi(symbol)
                            .doOnNext(candles -> writeCacheAsync(key, candles))
                            .map(candles -> clampAndCast(new ArrayList<>(candles), limit));
                })
                .switchIfEmpty(Mono.just(List.<Map<String, Object>>of()))
                .onErrorResume(ex -> {
                    log.warn("Alpha Vantage daily fetch failed for {}: {}", symbol, ex.getMessage());
                    return Mono.just(List.<Map<String, Object>>of());
                });
    }

    /** Non-blocking SMA fetch. See {@link #getDailySeriesAsync} for the threading contract. */
    public Mono<List<Map<String, Object>>> getSmaAsync(String symbol, int period, String interval) {
        if (symbol == null || symbol.isBlank()) return Mono.just(List.of());
        String safeInterval = (interval == null || interval.isBlank()) ? "daily" : interval;
        String key = SMA_KEY_PREFIX + symbol.toUpperCase() + ":" + period + ":" + safeInterval;

        return readCachedList(key)
                .flatMap(cached -> {
                    if (cached != null) {
                        // Already typed when written; safe to cast structurally.
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> typed = (List<Map<String, Object>>) (List<?>) cached;
                        return Mono.just(typed);
                    }
                    if (!acquireToken()) return Mono.just(List.<Map<String, Object>>of());
                    return fetchSmaFromApi(symbol, period, safeInterval)
                            .doOnNext(rows -> writeCacheAsync(key, rows));
                })
                .switchIfEmpty(Mono.just(List.<Map<String, Object>>of()))
                .onErrorResume(ex -> {
                    log.warn("Alpha Vantage SMA fetch failed for {}: {}", symbol, ex.getMessage());
                    return Mono.just(List.<Map<String, Object>>of());
                });
    }

    // =====================================================================
    // Public API — sync overloads (legacy callers; bounded by SYNC_BLOCK_TIMEOUT)
    // =====================================================================

    public List<Map<String, Object>> getDailySeries(String symbol, int limit) {
        List<Map<String, Object>> result = getDailySeriesAsync(symbol, limit).block(SYNC_BLOCK_TIMEOUT);
        return result == null ? List.of() : result;
    }

    public List<Map<String, Object>> getSma(String symbol, int period, String interval) {
        List<Map<String, Object>> result = getSmaAsync(symbol, period, interval).block(SYNC_BLOCK_TIMEOUT);
        return result == null ? List.of() : result;
    }

    // =====================================================================
    // Internals — HTTP + parsing + cache helpers
    // =====================================================================

    /**
     * Reads a cached list from Redis on a bounded-elastic scheduler so the
     * blocking Lettuce sync API doesn't end up on the Netty event loop.
     * Returns Mono.just(null) on miss (use {@code flatMap} + null check; we
     * keep null as the miss sentinel here rather than {@code Mono.empty()} so
     * downstream operators don't accidentally swallow the rest of the chain).
     */
    private Mono<List<Object>> readCachedList(String key) {
        return Mono.fromCallable(() -> {
                    Object cached = redisTemplate.opsForValue().get(key);
                    if (cached instanceof List<?> list) {
                        @SuppressWarnings("unchecked")
                        List<Object> raw = (List<Object>) list;
                        return raw;
                    }
                    return (List<Object>) null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("Alpha Vantage cache read failed for {}: {}", key, ex.getMessage());
                    return Mono.justOrEmpty((List<Object>) null);
                });
    }

    /** Fire-and-forget cache write on boundedElastic; never blocks the response chain. */
    private void writeCacheAsync(String key, List<Map<String, Object>> value) {
        Mono.fromRunnable(() -> redisTemplate.opsForValue().set(key, value, TTL_HOURS, TimeUnit.HOURS))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        ignored -> { },
                        ex -> log.warn("Alpha Vantage cache write failed for {}: {}", key, ex.getMessage())
                );
    }

    private Mono<List<Map<String, Object>>> fetchDailyFromApi(String symbol) {
        return webClient.get()
                .uri(uri -> uri
                        .queryParam("function", "TIME_SERIES_DAILY")
                        .queryParam("symbol", symbol)
                        .queryParam("outputsize", "compact")
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (response == null) return List.<Map<String, Object>>of();
                    Map<?, ?> series = (Map<?, ?>) response.get("Time Series (Daily)");
                    if (series == null) return List.<Map<String, Object>>of();

                    // Alpha Vantage returns dates descending; flip to ascending
                    // so the resulting candles render left-to-right.
                    List<Map.Entry<?, ?>> entries = new ArrayList<>(series.entrySet());
                    Collections.reverse(entries);
                    List<Map<String, Object>> candles = new ArrayList<>();
                    for (Map.Entry<?, ?> entry : entries) {
                        Map<?, ?> ohlc = (Map<?, ?>) entry.getValue();
                        Map<String, Object> point = new LinkedHashMap<>();
                        point.put("time", String.valueOf(entry.getKey()));
                        point.put("open", parseDouble(ohlc.get("1. open")));
                        point.put("high", parseDouble(ohlc.get("2. high")));
                        point.put("low",  parseDouble(ohlc.get("3. low")));
                        point.put("close", parseDouble(ohlc.get("4. close")));
                        point.put("volume", parseDouble(ohlc.get("5. volume")));
                        candles.add(point);
                    }
                    return candles;
                })
                .defaultIfEmpty(List.of());
    }

    private Mono<List<Map<String, Object>>> fetchSmaFromApi(String symbol, int period, String interval) {
        return webClient.get()
                .uri(uri -> uri
                        .queryParam("function", "SMA")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("time_period", period)
                        .queryParam("series_type", "close")
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    if (response == null) return List.<Map<String, Object>>of();
                    Map<?, ?> series = (Map<?, ?>) response.get("Technical Analysis: SMA");
                    if (series == null) return List.<Map<String, Object>>of();

                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (Map.Entry<?, ?> entry : series.entrySet()) {
                        Map<?, ?> row = (Map<?, ?>) entry.getValue();
                        Map<String, Object> point = new LinkedHashMap<>();
                        point.put("time", String.valueOf(entry.getKey()));
                        point.put("sma", parseDouble(row.get("SMA")));
                        rows.add(point);
                    }
                    return rows;
                })
                .defaultIfEmpty(List.of());
    }

    private boolean acquireToken() {
        if (apiKey == null || apiKey.isBlank()) return false;
        synchronized (rateLock) {
            Instant cutoff = Instant.now().minus(Duration.ofMinutes(1));
            recentCalls.removeIf(t -> t.isBefore(cutoff));
            if (recentCalls.size() >= MAX_CALLS_PER_MIN) return false;
            recentCalls.add(Instant.now());
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> clampAndCast(List<Object> source, int limit) {
        List<Map<String, Object>> typed = new ArrayList<>();
        for (Object o : source) if (o instanceof Map) typed.add((Map<String, Object>) o);
        if (limit <= 0 || limit >= typed.size()) return typed;
        return typed.subList(typed.size() - limit, typed.size());
    }

    private Double parseDouble(Object v) {
        if (v == null) return null;
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException ex) { return null; }
    }
}
