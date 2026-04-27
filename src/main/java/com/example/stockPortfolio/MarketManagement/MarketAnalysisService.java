package com.example.stockPortfolio.MarketManagement;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class MarketAnalysisService {

    private final FinnhubService finnhubService;

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
            UNIVERSE.stream().forEach(u -> {
                try {
                    Map<String, Object> quote = finnhubService.getStockQuote(u.get("symbol"));
                    if (quote != null) {
                        Map<String, Object> enriched = new HashMap<>(quote);
                        enriched.put("sector", u.get("sector"));
                        enriched.put("marketCap", u.get("marketCap"));
                        cache.put(u.get("symbol"), enriched);
                    }
                } catch (Exception e) {
                    // Fail silently for individual stocks to keep the list populated
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
                        getDouble(b, "changesPercentage"),
                        getDouble(a, "changesPercentage")))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getLosers(String capFilter, String sectorFilter) {
        return getMarketData().stream()
                .filter(m -> filterByCap(m, capFilter) && filterBySector(m, sectorFilter))
                .sorted((a, b) -> Double.compare(
                        getDouble(a, "changesPercentage"),
                        getDouble(b, "changesPercentage")))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getBySector(String sector) {
        return getMarketData().stream()
                .filter(m -> filterBySector(m, sector))
                .sorted((a, b) -> Double.compare(
                        getDouble(b, "changesPercentage"),
                        getDouble(a, "changesPercentage")))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTrending() {
        try {
            return getMarketData().stream()
                    .sorted((a, b) -> Double.compare(
                            Math.abs(getDouble(b, "changesPercentage")),
                            Math.abs(getDouble(a, "changesPercentage"))))
                    .limit(5)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0.0;
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

    public List<Map<String, String>> getFamousInsights(String symbol) {
        List<Map<String, String>> allInsights = new ArrayList<>();
        
        allInsights.add(Map.of(
            "investor", "Warren Buffett",
            "stock", "AAPL",
            "title", "The Power of Moat",
            "podcastUrl", "https://www.youtube.com/watch?v=2a9Lx9J8uEs",
            "message", "I don't look to jump over 7-foot bars: I look around for 1-foot bars that I can step over."
        ));
        
        allInsights.add(Map.of(
            "investor", "Rakesh Jhunjhunwala",
            "stock", "TATA MOTORS",
            "title", "India's Structural Bull Run",
            "podcastUrl", "https://www.youtube.com/watch?v=0A6vW0V0-pU",
            "message", "Respect the market. Have an open mind. Know what to stake and when to take a loss."
        ));

        allInsights.add(Map.of(
            "investor", "Cathie Wood",
            "stock", "TSLA",
            "title", "Disruptive Innovation",
            "podcastUrl", "https://www.youtube.com/watch?v=mY9uI2O_fX8",
            "message", "Innovation is the key to growth. We focus on the next big technology shifts."
        ));

        allInsights.add(Map.of(
            "investor", "Naval Ravikant",
            "stock", "ALL",
            "title", "How to Get Rich",
            "podcastUrl", "https://www.youtube.com/watch?v=1-TZqOsVCNM",
            "message", "Productize yourself. Wealth is assets that earn while you sleep."
        ));

        if (symbol == null || symbol.equalsIgnoreCase("all")) {
            return allInsights;
        }

        try {
            return allInsights.stream()
                    .filter(i -> i.get("stock").equalsIgnoreCase(symbol) || i.get("stock").equalsIgnoreCase("ALL"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
