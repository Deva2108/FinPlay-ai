package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SymbolNormalizer {

    private final StockUniverseRepo stockUniverseRepo;
    private final Map<String, StockUniverse> universeCache = new java.util.concurrent.ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        refreshCache();
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 1800000) // 30 minutes
    public void refreshCache() {
        log.info("Refreshing SymbolNormalizer universe cache...");
        List<StockUniverse> all = stockUniverseRepo.findAll();
        if (all.isEmpty()) return;
        
        Map<String, StockUniverse> newCache = new java.util.concurrent.ConcurrentHashMap<>();
        for (StockUniverse s : all) {
            newCache.put(s.getSymbol().toUpperCase(), s);
        }
        
        // Atomically replace cache
        universeCache.clear();
        universeCache.putAll(newCache);
    }

    public String normalize(String symbol) {
        if (!isSupported(symbol)) {
            return null;
        }

        String cleaned = symbol.trim().toUpperCase(Locale.ROOT);

        // Indices and known symbols in DB
        if (universeCache.containsKey(cleaned)) {
            return cleaned;
        }

        // Handle .NS suffix for Indian stocks not in cache
        if (cleaned.endsWith(".NS")) {
            return cleaned;
        }

        // Try compact lookup
        String compact = cleaned.replaceAll("[^A-Z0-9]", "");
        if (universeCache.containsKey(compact + ".NS")) {
            return compact + ".NS";
        }

        return cleaned;
    }

    public boolean isIndian(String symbol) {
        String normalized = normalize(symbol);
        if (normalized == null) return false;
        
        StockUniverse info = universeCache.get(normalized);
        if (info != null) return "INDIA".equalsIgnoreCase(info.getMarket());
        
        return normalized.endsWith(".NS") || normalized.startsWith("^NSE") || normalized.startsWith("^BS");
    }

    /** True when the symbol represents a market index rather than a single equity. */
    public boolean isIndex(String symbol) {
        if (symbol == null || symbol.isBlank()) return false;
        String normalized = normalize(symbol);
        
        StockUniverse info = universeCache.get(normalized);
        if (info != null) return info.isIndex();
        
        return normalized.startsWith("^");
    }

    public boolean isSupported(String symbol) {
        return symbol != null && !symbol.isBlank();
    }
}
