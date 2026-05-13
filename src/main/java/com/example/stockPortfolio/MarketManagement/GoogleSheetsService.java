package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleSheetsService {

    private final RestTemplate restTemplate;

    @Value("${google.script.url:}")
    private String scriptUrl;

    private String normalizeForGoogle(String symbol) {
        if (symbol == null) return null;
        String upper = symbol.toUpperCase();
        if (upper.endsWith(".NS")) {
            return "NSE:" + upper.replace(".NS", "");
        } else if (upper.endsWith(".BO")) {
            return "BOM:" + upper.replace(".BO", "");
        }
        return upper;
    }

    /**
     * Fetches historical data from the Google Apps Script Web App.
     */
    public List<Map<String, Object>> fetchDeepHistory(String symbol, String timeframe) {
        if (scriptUrl == null || scriptUrl.isBlank()) {
            log.warn("Google Script URL is missing. Cannot fetch deep history for {}", symbol);
            return Collections.emptyList();
        }

        try {
            String googleSymbol = normalizeForGoogle(symbol);
            String url = UriComponentsBuilder.fromHttpUrl(scriptUrl)
                    .queryParam("symbol", googleSymbol)
                    .queryParam("type", "chart")
                    .queryParam("timeframe", timeframe)
                    .toUriString();

            log.info("Fetching {} history for {} from Google Sheets Proxy...", timeframe, symbol);
            List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);
            
            if (response == null || response.isEmpty()) {
                return Collections.emptyList();
            }

            // Standardize field names for the frontend (price -> value)
            for (Map<String, Object> point : response) {
                if (point.containsKey("price")) {
                    point.put("value", point.get("price"));
                }
            }

            return response;
        } catch (Exception e) {
            log.error("Google Sheets Chart error for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches P/E, Market Cap, and Profit Trends.
     */
    public Map<String, Object> fetchFinancials(String symbol) {
        if (scriptUrl == null || scriptUrl.isBlank()) return Collections.emptyMap();

        try {
            String googleSymbol = normalizeForGoogle(symbol);
            String url = UriComponentsBuilder.fromHttpUrl(scriptUrl)
                    .queryParam("symbol", googleSymbol)
                    .queryParam("type", "financials")
                    .toUriString();

            log.info("Fetching deep financials for {} from Google Sheets Proxy...", symbol);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            return response != null ? response : Collections.emptyMap();
        } catch (Exception e) {
            log.error("Google Sheets Financials error for {}: {}", symbol, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
