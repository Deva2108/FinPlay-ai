package com.example.stockPortfolio.MarketManagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
@Slf4j
public class YahooFinanceService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    public List<Map<String, Object>> getHistoricalData(String symbol) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + symbol.toUpperCase())
                    .queryParam("interval", "15m")
                    .queryParam("range", "1d")
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> response = responseEntity.getBody();

            if (response != null && response.containsKey("chart")) {
                Map<String, Object> chart = (Map<String, Object>) response.get("chart");
                List<Map<String, Object>> resultList = (List<Map<String, Object>>) chart.get("result");
                
                if (resultList != null && !resultList.isEmpty()) {
                    Map<String, Object> result = resultList.get(0);
                    List<Number> timestamps = (List<Number>) result.get("timestamp");
                    Map<String, Object> indicators = (Map<String, Object>) result.get("indicators");
                    List<Map<String, Object>> quoteList = (List<Map<String, Object>>) indicators.get("quote");
                    List<Number> closePrices = (List<Number>) quoteList.get(0).get("close");

                    List<Map<String, Object>> formattedData = new ArrayList<>();
                    
                    if (timestamps != null && closePrices != null) {
                        int size = Math.min(timestamps.size(), closePrices.size());
                        for (int i = 0; i < size; i++) {
                            Number close = closePrices.get(i);
                            Number ts = timestamps.get(i);
                            
                            // Filter null values and invalid points
                            if (close != null && ts != null) {
                                Map<String, Object> point = new HashMap<>();
                                // STRICT CONTRACT: time, value
                                point.put("time", ts.longValue());
                                point.put("value", close.doubleValue());
                                formattedData.add(point);
                            }
                        }
                        
                        if (!formattedData.isEmpty()) {
                            log.info("Successfully fetched {} real data points for {}", formattedData.size(), symbol);
                            return formattedData;
                        }
                    }
                }
            }
            throw new RuntimeException("No real data found for " + symbol);
        } catch (Exception e) {
            log.error("Yahoo Finance Error for {}: {}", symbol, e.getMessage());
            // NO FAKE FALLBACK
            throw new RuntimeException("Yahoo Finance API unavailable: " + e.getMessage());
        }
    }
}
