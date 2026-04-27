package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FinnhubService {

    @Value("${finnhub.api.key}")
    private String apiKey;

    @Value("${finnhub.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    @Cacheable(value = "stockQuotes", key = "#symbol.toUpperCase()", unless = "#result == null")
    public Map<String, Object> getStockQuote(String symbol) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/quote")
                    .queryParam("symbol", symbol.toUpperCase())
                    .queryParam("token", apiKey)
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.get("c") != null) {
                double c = Double.parseDouble(response.get("c").toString());
                if (c == 0) {
                    throw new IllegalArgumentException("Finnhub returned 0 price. Invalid symbol or limit reached.");
                }
                
                Map<String, Object> result = new HashMap<>();
                result.put("symbol", symbol.toUpperCase());
                result.put("price", c);
                
                double pc = Double.parseDouble(response.get("pc").toString());
                double changePct = pc != 0 ? ((c - pc) / pc * 100) : 0;
                
                result.put("changesPercentage", Math.round(changePct * 100.0) / 100.0);
                return result;
            }
            throw new RuntimeException("Finnhub returned empty quote response");
        } catch (Exception e) {
            log.error("Finnhub Quote Error for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to fetch quote from Finnhub", e);
        }
    }

    @Cacheable(value = "marketNews", key = "'finnhub:' + #symbol.toUpperCase()", unless = "#result == null || #result.isEmpty()")
    public List<Map<String, Object>> getCompanyNews(String symbol) {
        try {
            // Finnhub expects YYYY-MM-DD for news
            String today = java.time.LocalDate.now().toString();
            String lastWeek = java.time.LocalDate.now().minusDays(7).toString();

            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/company-news")
                    .queryParam("symbol", symbol.toUpperCase())
                    .queryParam("from", lastWeek)
                    .queryParam("to", today)
                    .queryParam("token", apiKey)
                    .toUriString();

            List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);
            if (response != null) {
                return response.stream().limit(10).map(n -> {
                    Map<String, Object> simplified = new HashMap<>();
                    simplified.put("headline", n.get("headline"));
                    simplified.put("source", n.get("source"));
                    // Convert seconds to ISO string for frontend consistency
                    Object datetime = n.get("datetime");
                    if (datetime instanceof Number) {
                        simplified.put("datetime", java.time.Instant.ofEpochSecond(((Number) datetime).longValue()).toString());
                    }
                    simplified.put("url", n.get("url"));
                    return simplified;
                }).toList();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Finnhub News Error for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Finnhub News API unavailable");
        }
    }

    public List<Map<String, Object>> searchStocks(String query) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search")
                    .queryParam("q", query)
                    .queryParam("token", apiKey)
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("result")) {
                List<Map<String, Object>> searchResults = (List<Map<String, Object>>) response.get("result");
                
                // Filter for common stocks and limit to 5 for speed
                return searchResults.stream()
                        .filter(r -> "Common Stock".equals(r.get("type")))
                        .limit(5)
                        .map(r -> {
                            String symbol = (String) r.get("symbol");
                            Map<String, Object> map = new HashMap<>();
                            map.put("symbol", symbol);
                            map.put("name", r.get("description"));
                            return map;
                        }).collect(Collectors.toList());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Finnhub Search Error for {}: {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }
}
