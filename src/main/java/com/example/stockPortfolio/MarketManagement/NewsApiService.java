package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewsApiService {

    private final ExternalMarketDataGateway externalMarketDataGateway;

    public List<Map<String, Object>> getStockNews(String query) {
        return externalMarketDataGateway.fetchMarketNews(query);
    }

    public List<Map<String, Object>> getPortfolioNews(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return getStockNews("stock market");
        }

        String query = symbols.stream().limit(3).collect(java.util.stream.Collectors.joining(" OR "));
        return getStockNews(query);
    }
}
