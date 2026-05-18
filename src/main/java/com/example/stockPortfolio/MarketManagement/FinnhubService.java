package com.example.stockPortfolio.MarketManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FinnhubService {

    private final ExternalMarketDataGateway externalMarketDataGateway;

    public ApiResponse<Map<String, Object>> getStockQuote(String symbol) {
        Map<String, Object> quote = externalMarketDataGateway.fetchQuoteWithFallback(symbol);
        return ApiResponse.ok(quote, "Stock quote fetched successfully");
    }

    public List<Map<String, Object>> getCompanyNews(String symbol) {
        return externalMarketDataGateway.fetchCompanyNews(symbol);
    }

    public List<Map<String, Object>> searchStocks(String query) {
        return externalMarketDataGateway.searchStocks(query);
    }

    public List<Map<String, Object>> fetchStockCandles(String symbol) {
        return externalMarketDataGateway.fetchHistoricalData(symbol);
    }
}
