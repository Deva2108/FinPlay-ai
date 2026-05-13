package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            org.springframework.http.ResponseEntity<Map> responseEntity = restTemplate.getForEntity(url, Map.class);
            
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
