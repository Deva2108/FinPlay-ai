package com.example.stockPortfolio.HoldingsManagement;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class FmpStockPriceService {

    @Value("${stock.api.key}")
    private String apiKey;

    @Value("${stock.api.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    @Cacheable(value = "stockPrices", key = "#symbol.toUpperCase()", unless = "#result == null || #result <= 0")
    public Double getStockPrice(String symbol) {
        log.info("CACHE MISS: Fetching stock price from FMP API for {}", symbol.toUpperCase());
        String url = String.format("%s/quote/%s?apikey=%s", baseUrl, symbol.toUpperCase(), apiKey);
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            if (response != null && response.isArray() && !response.isEmpty()) {
                JsonNode quote = response.get(0);
                if (quote.has("price")) {
                    return quote.get("price").asDouble();
                }
            }
        } catch (Exception e) {
            log.error("FMP API Error for {}: {}", symbol, e.getMessage());
        }
        return 0.0; 
    }

    @Cacheable(value = "fmpStockQuotes", key = "#symbol.toUpperCase()", unless = "#result == null")
    public JsonNode getFullQuote(String symbol) {
        log.info("CACHE MISS: Fetching full quote from FMP API for {}", symbol.toUpperCase());
        String url = String.format("%s/quote/%s?apikey=%s", baseUrl, symbol.toUpperCase(), apiKey);
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            if (response != null && response.isArray() && !response.isEmpty()) {
                return response.get(0);
            }
        } catch (Exception e) {
            log.error("FMP Quote Error for {}: {}", symbol, e.getMessage());
        }
        return null;
    }
}
