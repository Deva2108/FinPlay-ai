package com.example.stockPortfolio.MarketManagement;

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

    public Map<String, Object> getStockQuote(String symbol) {
        return externalMarketDataGateway.fetchQuoteWithFallback(symbol);
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
