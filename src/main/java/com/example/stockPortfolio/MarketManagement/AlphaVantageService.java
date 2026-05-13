package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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
 * Free tier: 5 calls/min, 25/day. We enforce the 5/min cap with a tiny in-
 * process token bucket. Results are written to Redis with a 12-hour TTL so
 * the daily quota isn't burned up on every request.
 *
 * Used only when Finnhub/Yahoo are unavailable for a given symbol.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlphaVantageService {

    private static final String BASE_URL = "https://www.alphavantage.co/query";
    private static final String DAILY_KEY_PREFIX = "alphav:daily:";
    private static final String SMA_KEY_PREFIX = "alphav:sma:";
    private static final long TTL_HOURS = 12;
    private static final int MAX_CALLS_PER_MIN = 5;

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${ALPHA_VANTAGE_KEY:${alpha.vantage.key:}}")
    private String apiKey;

    private final Object rateLock = new Object();
    private final List<Instant> recentCalls = new ArrayList<>();

    @PostConstruct
    public void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Alpha Vantage key missing. Fallback historical/indicator data is disabled.");
        }
    }

    /**
     * Returns up to {@code limit} OHLC candles (most recent last). Reads from
     * cache; only calls the API when the cache is cold.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDailySeries(String symbol, int limit) {
        if (symbol == null || symbol.isBlank()) return List.of();
        String key = DAILY_KEY_PREFIX + symbol.toUpperCase();
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List) {
            return clampAndCast((List<Object>) cached, limit);
        }

        if (!acquireToken()) {
            log.debug("Alpha Vantage rate limit reached; serving empty.");
            return List.of();
        }

        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("function", "TIME_SERIES_DAILY")
                .queryParam("symbol", symbol)
                .queryParam("outputsize", "compact")
                .queryParam("apikey", apiKey)
                .toUriString();

        try {
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return List.of();
            Map<?, ?> series = (Map<?, ?>) response.get("Time Series (Daily)");
            if (series == null) return List.of();

            List<Map<String, Object>> candles = new ArrayList<>();
            // entries are date-keyed; LinkedHashMap from Jackson is descending — flip to ascending
            List<Map.Entry<?, ?>> entries = new ArrayList<>(series.entrySet());
            Collections.reverse(entries);
            for (Map.Entry<?, ?> entry : entries) {
                String date = String.valueOf(entry.getKey());
                Map<?, ?> ohlc = (Map<?, ?>) entry.getValue();
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("time", date);
                point.put("open", parseDouble(ohlc.get("1. open")));
                point.put("high", parseDouble(ohlc.get("2. high")));
                point.put("low",  parseDouble(ohlc.get("3. low")));
                point.put("close", parseDouble(ohlc.get("4. close")));
                point.put("volume", parseDouble(ohlc.get("5. volume")));
                candles.add(point);
            }

            redisTemplate.opsForValue().set(key, candles, TTL_HOURS, TimeUnit.HOURS);
            return clampAndCast(new ArrayList<>(candles), limit);
        } catch (Exception ex) {
            log.warn("Alpha Vantage daily fetch failed for {}: {}", symbol, ex.getMessage());
            return List.of();
        }
    }

    /** Simple Moving Average (SMA) over the last {@code period} closes. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSma(String symbol, int period, String interval) {
        if (symbol == null || symbol.isBlank()) return List.of();
        String safeInterval = (interval == null || interval.isBlank()) ? "daily" : interval;
        String key = SMA_KEY_PREFIX + symbol.toUpperCase() + ":" + period + ":" + safeInterval;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List) return (List<Map<String, Object>>) cached;

        if (!acquireToken()) return List.of();

        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("function", "SMA")
                .queryParam("symbol", symbol)
                .queryParam("interval", safeInterval)
                .queryParam("time_period", period)
                .queryParam("series_type", "close")
                .queryParam("apikey", apiKey)
                .toUriString();

        try {
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return List.of();
            Map<?, ?> series = (Map<?, ?>) response.get("Technical Analysis: SMA");
            if (series == null) return List.of();

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<?, ?> entry : series.entrySet()) {
                Map<?, ?> row = (Map<?, ?>) entry.getValue();
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("time", String.valueOf(entry.getKey()));
                point.put("sma", parseDouble(row.get("SMA")));
                rows.add(point);
            }
            redisTemplate.opsForValue().set(key, rows, TTL_HOURS, TimeUnit.HOURS);
            return rows;
        } catch (Exception ex) {
            log.warn("Alpha Vantage SMA fetch failed for {}: {}", symbol, ex.getMessage());
            return List.of();
        }
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
