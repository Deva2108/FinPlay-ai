package com.example.stockPortfolio.MarketManagement;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class MarketAnalysisService {

    private final MarketGateway marketGateway;
    private final StockUniverseRepo stockUniverseRepo;
    private final SymbolNormalizer symbolNormalizer;

    /**
     * READ ONLY FROM GATEWAY.
     * PERFORMANCE FIX: Use cached enriched universe if available to avoid O(N) HashMap churn.
     */
    public List<Map<String, Object>> getMarketData() {
        // 1. Try pre-calculated enriched universe first
        List<Map<String, Object>> cachedUniverse = marketGateway.getFullEnrichedUniverse();
        if (!cachedUniverse.isEmpty()) return cachedUniverse;

        // 2. Build on-the-fly if cache is cold
        Map<String, StockUniverse> universeMap = symbolNormalizer.getUniverseCache();
        if (universeMap.isEmpty()) {
            List<StockUniverse> dbUniverse = stockUniverseRepo.findAll();
            return enrich(dbUniverse);
        }

        List<String> symbols = new ArrayList<>(universeMap.keySet());
        Map<String, Map<String, Object>> quotes = marketGateway.getBatchQuotes(symbols);

        List<Map<String, Object>> freshData = universeMap.values().stream()
                .map(u -> {
                    Map<String, Object> quote = quotes.get(u.getSymbol());
                    if (quote != null) {
                        // Pre-size to avoid HashMap rehash; only copy when the quote
                        // does not already carry sector/marketCap metadata.
                        if (quote.containsKey("sector") && quote.containsKey("marketCap")) return quote;
                        Map<String, Object> enriched = new HashMap<>(quote.size() + 2);
                        enriched.putAll(quote);
                        enriched.put("sector", u.getSector());
                        enriched.put("marketCap", u.getMarketCap() != null ? u.getMarketCap() : "Mid Cap");
                        return enriched;
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        
        // 3. Update cache for subsequent requests (TTL 5m managed by Gateway)
        marketGateway.updateFullEnrichedUniverse(freshData);
        return freshData;
    }

    private List<Map<String, Object>> enrich(List<StockUniverse> universe) {
        List<String> symbols = universe.stream().map(StockUniverse::getSymbol).toList();
        Map<String, Map<String, Object>> quotes = marketGateway.getBatchQuotes(symbols);
        return universe.stream()
                .map(u -> {
                    Map<String, Object> quote = quotes.get(u.getSymbol());
                    if (quote != null) {
                        // Pre-size to avoid HashMap rehash; only copy when the quote
                        // does not already carry sector/marketCap metadata.
                        if (quote.containsKey("sector") && quote.containsKey("marketCap")) return quote;
                        Map<String, Object> enriched = new HashMap<>(quote.size() + 2);
                        enriched.putAll(quote);
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
        // PERFORMANCE FIX: Try pre-calculated list from Redis first
        if ((capFilter == null || capFilter.equalsIgnoreCase("all")) && (sectorFilter == null || sectorFilter.equalsIgnoreCase("all"))) {
            List<Map<String, Object>> cached = marketGateway.getTopMovers("gainers");
            if (!cached.isEmpty()) return cached;
        }

        return getMarketData().stream()
                .filter(m -> filterByCap(m, capFilter) && filterBySector(m, sectorFilter))
                .sorted((a, b) -> Double.compare(
                        getDouble(b, "changesPercentage"),
                        getDouble(a, "changesPercentage")))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getLosers(String capFilter, String sectorFilter) {
        // PERFORMANCE FIX: Try pre-calculated list from Redis first
        if ((capFilter == null || capFilter.equalsIgnoreCase("all")) && (sectorFilter == null || sectorFilter.equalsIgnoreCase("all"))) {
            List<Map<String, Object>> cached = marketGateway.getTopMovers("losers");
            if (!cached.isEmpty()) return cached;
        }

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
        // PERFORMANCE FIX: Try pre-calculated list from Redis first
        List<Map<String, Object>> cached = marketGateway.getTopMovers("trending");
        if (!cached.isEmpty()) return cached;

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
        StockUniverse info = symbolNormalizer.getUniverseCache().get(symbol.toUpperCase());
        return info != null ? info.getSector() : "Other";
    }

    /**
     * Bulk variant of {@link #getSectorForSymbol(String)}. Returns a
     * {@code Symbol -> StockUniverse} map built from the SymbolNormalizer cache.
     * PERFORMANCE FIX: No longer hits the database.
     */
    public Map<String, StockUniverse> getUniverseBySymbols(Collection<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyMap();
        Map<String, StockUniverse> cache = symbolNormalizer.getUniverseCache();
        Map<String, StockUniverse> result = new HashMap<>();
        
        for (String s : symbols) {
            StockUniverse info = cache.get(s.toUpperCase());
            if (info != null) result.put(info.getSymbol(), info);
        }
        return result;
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
