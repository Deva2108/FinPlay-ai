package com.example.stockPortfolio.MarketManagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
@Slf4j
public class NewsApiService {

    @Value("${news.api.key}")
    private String apiKey;

    @Value("${news.api.base-url}")
    private String baseUrl;

    @Autowired
    private FinnhubService finnhubService;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Map<String, Object>> getStockNews(String query) {
        // Step 1: Try NewsAPI (Primary)
        try {
            log.info("Fetching NewsAPI news for query: {}", query);
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/everything")
                    .queryParam("q", query)
                    .queryParam("sortBy", "publishedAt")
                    .queryParam("language", "en")
                    .queryParam("pageSize", 10)
                    .queryParam("apiKey", apiKey)
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("articles")) {
                List<Map<String, Object>> articles = (List<Map<String, Object>>) response.get("articles");
                if (articles != null && !articles.isEmpty()) {
                    return articles.stream().map(a -> {
                        Map<String, Object> simplified = new HashMap<>();
                        simplified.put("headline", a.get("title"));
                        simplified.put("source", ((Map<String, Object>)a.get("source")).get("name"));
                        simplified.put("datetime", a.get("publishedAt"));
                        simplified.put("url", a.get("url"));
                        return simplified;
                    }).toList();
                }
            }
        } catch (Exception e) {
            log.warn("NewsAPI failed for {}: {}. Switching to fallback.", query, e.getMessage());
        }

        // Step 2: Try Finnhub (Fallback - Real Data)
        try {
            log.info("Fetching Finnhub news for fallback: {}", query);
            // If query looks like a symbol, use it directly, else default to 'AAPL' or similar for general market
            String symbol = (query.length() <= 5 && query.matches("[A-Z]+")) ? query : "AAPL";
            return finnhubService.getCompanyNews(symbol);
        } catch (Exception e) {
            log.error("Finnhub Fallback also failed: {}", e.getMessage());
            // NO FAKE DATA
            throw new RuntimeException("All news sources currently unavailable. " + e.getMessage());
        }
    }
}
