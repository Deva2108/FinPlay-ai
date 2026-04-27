package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import org.springframework.cache.annotation.Cacheable;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewsApiService {

    @Value("${news.api.key}")
    private String apiKey;

    @Value("${news.api.base-url}")
    private String baseUrl;

    private final FinnhubService finnhubService;
    private final RestTemplate restTemplate;

    @Cacheable(value = "marketNews", key = "#query", unless = "#result == null || #result.isEmpty()")
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
                        // Determine sentiment from headline for UX
                        String h = ((String)a.get("title")).toLowerCase();
                        simplified.put("isRisk", h.contains("fall") || h.contains("drop") || h.contains("crash") || h.contains("loss"));
                        simplified.put("isOpportunity", h.contains("gain") || h.contains("surge") || h.contains("rise") || h.contains("profit"));
                        return simplified;
                    }).collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.warn("NewsAPI failed for {}: {}. Switching to fallback.", query, e.getMessage());
        }

        // Step 2: Try Finnhub (Fallback)
        try {
            String symbol = (query.length() <= 5 && query.matches("[A-Z]+")) ? query : "AAPL";
            return finnhubService.getCompanyNews(symbol);
        } catch (Exception e) {
            log.error("Finnhub Fallback also failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getPortfolioNews(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return getStockNews("stock market");
        }
        
        // Take top 3 symbols to avoid rate limits
        String query = symbols.stream().limit(3).collect(Collectors.joining(" OR "));
        return getStockNews(query);
    }
}
