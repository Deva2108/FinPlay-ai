package com.example.stockPortfolio.MarketManagement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MarketAnalysisService {

    @Autowired
    private FinnhubService finnhubService;

    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private long lastCacheTime = 0;
    private static final long CACHE_DURATION_MS = 60000; // 1 min

    private static final List<Map<String, String>> UNIVERSE = Arrays.asList(
            Map.of("symbol", "AAPL", "sector", "Tech", "marketCap", "Mega Cap"),
            Map.of("symbol", "MSFT", "sector", "Tech", "marketCap", "Mega Cap"),
            Map.of("symbol", "NVDA", "sector", "Tech", "marketCap", "Mega Cap"),
            Map.of("symbol", "TSLA", "sector", "Automotive", "marketCap", "Mega Cap"),
            Map.of("symbol", "META", "sector", "Tech", "marketCap", "Mega Cap"),
            Map.of("symbol", "AMZN", "sector", "Tech", "marketCap", "Mega Cap"),
            Map.of("symbol", "GOOGL", "sector", "Tech", "marketCap", "Mega Cap"),
            Map.of("symbol", "AMD", "sector", "Tech", "marketCap", "Large Cap"),
            Map.of("symbol", "INTC", "sector", "Tech", "marketCap", "Large Cap"),
            Map.of("symbol", "NFLX", "sector", "Tech", "marketCap", "Large Cap"),
            Map.of("symbol", "JPM", "sector", "Finance", "marketCap", "Mega Cap"),
            Map.of("symbol", "V", "sector", "Finance", "marketCap", "Mega Cap"),
            Map.of("symbol", "MA", "sector", "Finance", "marketCap", "Mega Cap"),
            Map.of("symbol", "BAC", "sector", "Finance", "marketCap", "Large Cap"),
            Map.of("symbol", "XOM", "sector", "Energy", "marketCap", "Mega Cap"),
            Map.of("symbol", "CVX", "sector", "Energy", "marketCap", "Large Cap"),
            Map.of("symbol", "JNJ", "sector", "Healthcare", "marketCap", "Large Cap"),
            Map.of("symbol", "UNH", "sector", "Healthcare", "marketCap", "Mega Cap"),
            Map.of("symbol", "WMT", "sector", "Retail", "marketCap", "Mega Cap"),
            Map.of("symbol", "PG", "sector", "Consumer", "marketCap", "Large Cap"),
            Map.of("symbol", "CRM", "sector", "Tech", "marketCap", "Large Cap"),
            Map.of("symbol", "COST", "sector", "Retail", "marketCap", "Large Cap"),
            Map.of("symbol", "AVGO", "sector", "Tech", "marketCap", "Large Cap"),
            Map.of("symbol", "F", "sector", "Automotive", "marketCap", "Mid Cap"),
            Map.of("symbol", "GM", "sector", "Automotive", "marketCap", "Mid Cap"),
            Map.of("symbol", "SLB", "sector", "Energy", "marketCap", "Large Cap")
    );

    public synchronized List<Map<String, Object>> getMarketData() {
        long now = System.currentTimeMillis();
        if (now - lastCacheTime > CACHE_DURATION_MS || cache.isEmpty()) {
            UNIVERSE.parallelStream().forEach(u -> {
                try {
                    Map<String, Object> quote = finnhubService.getStockQuote(u.get("symbol"));
                    Map<String, Object> enriched = new HashMap<>(quote);
                    enriched.put("sector", u.get("sector"));
                    enriched.put("marketCap", u.get("marketCap"));
                    cache.put(u.get("symbol"), enriched);
                } catch (Exception e) {
                    // Skip on error
                }
            });
            lastCacheTime = now;
        }
        return new ArrayList<>(cache.values());
    }

    public List<Map<String, Object>> getGainers(String capFilter, String sectorFilter) {
        return getMarketData().stream()
                .filter(m -> filterByCap(m, capFilter) && filterBySector(m, sectorFilter))
                .sorted((a, b) -> Double.compare(
                        (Double) b.getOrDefault("changesPercentage", 0.0),
                        (Double) a.getOrDefault("changesPercentage", 0.0)))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getLosers(String capFilter, String sectorFilter) {
        return getMarketData().stream()
                .filter(m -> filterByCap(m, capFilter) && filterBySector(m, sectorFilter))
                .sorted((a, b) -> Double.compare(
                        (Double) a.getOrDefault("changesPercentage", 0.0),
                        (Double) b.getOrDefault("changesPercentage", 0.0)))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getBySector(String sector) {
        return getMarketData().stream()
                .filter(m -> filterBySector(m, sector))
                .sorted((a, b) -> Double.compare(
                        (Double) b.getOrDefault("changesPercentage", 0.0),
                        (Double) a.getOrDefault("changesPercentage", 0.0)))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTrending() {
        return getMarketData().stream()
                .sorted((a, b) -> Double.compare(
                        Math.abs((Double) b.getOrDefault("changesPercentage", 0.0)),
                        Math.abs((Double) a.getOrDefault("changesPercentage", 0.0))))
                .limit(5)
                .collect(Collectors.toList());
    }

    private boolean filterByCap(Map<String, Object> stock, String capFilter) {
        if (capFilter == null || capFilter.isEmpty() || capFilter.equalsIgnoreCase("all")) {
            return true;
        }
        String stockCap = (String) stock.get("marketCap");
        if (stockCap == null) return false;
        
        return stockCap.toLowerCase().contains(capFilter.toLowerCase());
    }

    private boolean filterBySector(Map<String, Object> stock, String sectorFilter) {
        if (sectorFilter == null || sectorFilter.isEmpty() || sectorFilter.equalsIgnoreCase("all")) {
            return true;
        }
        String stockSector = (String) stock.get("sector");
        if (stockSector == null) return false;
        
        return stockSector.equalsIgnoreCase(sectorFilter);
    }
}