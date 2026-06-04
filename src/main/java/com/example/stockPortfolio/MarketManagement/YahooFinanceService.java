package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * High-reliability fallback service using public (unauthenticated) endpoints.
 * Prioritizes Yahoo Finance Public API which has high limits and requires NO key.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class YahooFinanceService {

    private final RestTemplate restTemplate;
    private final SymbolNormalizer symbolNormalizer;

    private static final String YAHOO_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    // Realistic browser headers prevent Yahoo's anti-bot layer from returning 403/429.
    // Without these, the default Spring RestTemplate sends no User-Agent and is immediately
    // fingerprinted as a headless client. The header values mirror a standard macOS Chrome
    // request — generic enough to not require rotation.
    private static final HttpHeaders BROWSER_HEADERS;
    static {
        BROWSER_HEADERS = new HttpHeaders();
        BROWSER_HEADERS.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        BROWSER_HEADERS.set(HttpHeaders.ACCEPT,
                "application/json,text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        BROWSER_HEADERS.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        BROWSER_HEADERS.set(HttpHeaders.CONNECTION, "keep-alive");
    }

    /**
     * Fetches real-time quote using Yahoo Finance public chart endpoint.
     * Works for US (^DJI, TSLA) and India (RELIANCE.NS, ^NSEI).
     */
    public Map<String, Object> fetchPublicQuote(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return null;

        String url = UriComponentsBuilder.fromHttpUrl(YAHOO_URL + normalized)
                .queryParam("interval", "1d")
                .queryParam("range", "1d")
                .toUriString();

        try {
            HttpEntity<Void> entity = new HttpEntity<>(BROWSER_HEADERS);
            ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            Map<String, Object> response = responseEntity.getBody();
            if (response == null || !response.containsKey("chart")) return null;

            Map<String, Object> chart = (Map<String, Object>) response.get("chart");
            List<Map<String, Object>> result = (List<Map<String, Object>>) chart.get("result");
            if (result == null || result.isEmpty()) return null;

            Map<String, Object> meta = (Map<String, Object>) result.get(0).get("meta");
            
            Double price = parseDouble(meta.get("regularMarketPrice"));
            Double prevClose = parseDouble(meta.get("previousClose"));
            
            if (price == null) return null;

            double changePct = 0.0;
            if (prevClose != null && prevClose != 0) {
                changePct = ((price - prevClose) / prevClose) * 100.0;
            }

            Map<String, Object> quote = new HashMap<>();
            quote.put("symbol", normalized);
            quote.put("price", Math.round(price * 100.0) / 100.0);
            quote.put("changesPercentage", Math.round(changePct * 100.0) / 100.0);
            quote.put("source", "yahoo_public");
            
            log.debug("Successfully fetched public quote for {} from Yahoo: {}", normalized, price);
            return quote;

        } catch (Exception e) {
            log.warn("Yahoo Public API failed for {}: {}", normalized, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches REAL daily OHLC history from Yahoo's public chart endpoint.
     * Free, no API key, no quota; works for US (AAPL) and India (RELIANCE.NS).
     * Returns oldest-first [{date:"yyyy-MM-dd", open, high, low, close}]. Used ONLY
     * by the background scheduler to backfill stock_history — never on a request path.
     */
    public List<Map<String, Object>> fetchDailyHistory(String symbol, String range) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return Collections.emptyList();

        String yahooRange = switch (range == null ? "1Y" : range.toUpperCase()) {
            case "1M" -> "1mo";
            case "6M" -> "6mo";
            case "1Y" -> "1y";
            case "5Y" -> "5y";
            default -> "1y";
        };

        String url = UriComponentsBuilder.fromHttpUrl(YAHOO_URL + normalized)
                .queryParam("interval", "1d")
                .queryParam("range", yahooRange)
                .toUriString();

        try {
            HttpEntity<Void> entity = new HttpEntity<>(BROWSER_HEADERS);
            ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> response = responseEntity.getBody();
            if (response == null || !response.containsKey("chart")) return Collections.emptyList();

            Map<String, Object> chart = (Map<String, Object>) response.get("chart");
            List<Map<String, Object>> result = (List<Map<String, Object>>) chart.get("result");
            if (result == null || result.isEmpty()) return Collections.emptyList();

            Map<String, Object> r0 = result.get(0);
            List<Number> timestamps = (List<Number>) r0.get("timestamp");
            Map<String, Object> indicators = (Map<String, Object>) r0.get("indicators");
            if (timestamps == null || indicators == null) return Collections.emptyList();

            List<Map<String, Object>> quoteArr = (List<Map<String, Object>>) indicators.get("quote");
            if (quoteArr == null || quoteArr.isEmpty()) return Collections.emptyList();

            Map<String, Object> q = quoteArr.get(0);
            List<Number> opens = (List<Number>) q.get("open");
            List<Number> highs = (List<Number>) q.get("high");
            List<Number> lows = (List<Number>) q.get("low");
            List<Number> closes = (List<Number>) q.get("close");
            if (closes == null) return Collections.emptyList();

            List<Map<String, Object>> out = new ArrayList<>();
            int n = Math.min(timestamps.size(), closes.size());
            for (int i = 0; i < n; i++) {
                Number close = closes.get(i);
                if (close == null) continue; // Yahoo nulls holidays/halted sessions
                java.time.LocalDate date = java.time.Instant.ofEpochSecond(timestamps.get(i).longValue())
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate();
                Map<String, Object> point = new HashMap<>();
                point.put("date", date.toString());
                point.put("close", close.doubleValue());
                if (opens != null && i < opens.size() && opens.get(i) != null) point.put("open", opens.get(i).doubleValue());
                if (highs != null && i < highs.size() && highs.get(i) != null) point.put("high", highs.get(i).doubleValue());
                if (lows != null && i < lows.size() && lows.get(i) != null) point.put("low", lows.get(i).doubleValue());
                out.add(point);
            }
            log.debug("Yahoo history for {}: {} points", normalized, out.size());
            return out;
        } catch (Exception e) {
            log.warn("Yahoo history failed for {}: {}", normalized, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Double parseDouble(Object v) {
        if (v == null) return null;
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
