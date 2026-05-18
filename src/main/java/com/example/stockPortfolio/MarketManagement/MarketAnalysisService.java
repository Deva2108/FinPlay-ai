package com.example.stockPortfolio.MarketManagement;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class MarketAnalysisService {

    private final MarketGateway marketGateway;
    private final StockUniverseRepo stockUniverseRepo;

    /**
     * READ ONLY FROM GATEWAY.
     */
    public List<Map<String, Object>> getMarketData() {
        List<StockUniverse> universe = stockUniverseRepo.findAll();
        List<String> symbols = universe.stream()
                .map(StockUniverse::getSymbol)
                .toList();
        
        Map<String, Map<String, Object>> quotes = marketGateway.getBatchQuotes(symbols);

        return universe.stream()
                .map(u -> {
                    String symbol = u.getSymbol();
                    Map<String, Object> quote = quotes.get(symbol);

                    if (quote != null) {
                        Map<String, Object> enriched = new HashMap<>(quote);
                        enriched.put("sector", u.getSector());
                        enriched.put("marketCap", u.getMarketCap() != null ? u.getMarketCap() : "Mid Cap");
                        return enriched;
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
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

    public String getSectorForSymbol(String symbol) {
        if (symbol == null) return "Other";
        return stockUniverseRepo.findBySymbol(symbol)
                .map(StockUniverse::getSector)
                .orElse("Other");
    }

    public List<Map<String, String>> getFamousInsights(String symbol) {
        List<Map<String, String>> allInsights = new ArrayList<>();
        
        allInsights.add(Map.of(
            "investor", "Warren Buffett",
            "stock", "AAPL",
            "title", "The Power of Moat",
            "podcastUrl", "https://www.youtube.com/watch?v=QdNR2G-3DI0",
            "message", "I don't look to jump over 7-foot bars: I look around for 1-foot bars that I can step over."
        ));
        
        allInsights.add(Map.of(
            "investor", "Rakesh Jhunjhunwala",
            "stock", "TATA MOTORS",
            "title", "India's Structural Bull Run",
            "podcastUrl", "https://www.youtube.com/watch?v=A8O2A1-0O6U",
            "message", "Respect the market. Have an open mind. Know what to stake and when to take a loss."
        ));

        allInsights.add(Map.of(
            "investor", "Cathie Wood",
            "stock", "TSLA",
            "title", "Disruptive Innovation",
            "podcastUrl", "https://www.youtube.com/watch?v=NUehKZBLBCQ",
            "message", "Innovation is the key to growth. We focus on the next big technology shifts."
        ));

        allInsights.add(Map.of(
            "investor", "Naval Ravikant",
            "stock", "ALL",
            "title", "The Psychology of Money",
            "podcastUrl", "https://www.youtube.com/watch?v=Xun73T7r4vE",
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
