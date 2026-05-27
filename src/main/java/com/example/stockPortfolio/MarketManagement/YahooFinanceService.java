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

    private Double parseDouble(Object v) {
        if (v == null) return null;
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
