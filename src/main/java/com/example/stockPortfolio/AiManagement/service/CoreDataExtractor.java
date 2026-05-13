package com.example.stockPortfolio.AiManagement.service;

import com.example.stockPortfolio.HoldingsManagement.HoldingService;
import com.example.stockPortfolio.MarketManagement.MarketGateway;
import com.example.stockPortfolio.MarketManagement.NewsApiService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoreDataExtractor {

    private final MarketGateway marketGateway;
    private final NewsApiService newsApiService;
    private final HoldingService holdingService;
    private final com.example.stockPortfolio.DecisionManagement.DecisionService decisionService;
    private final com.example.stockPortfolio.MarketManagement.SymbolNormalizer symbolNormalizer;

    @Data
    public static class InsightContext {
        public String symbol;
        public double currentPrice;
        public double priceChange;
        public double priceChangePct;
        public double previousClose;
        public double volume;
        public double avgVolume;
        public String sentiment;
        public int newsCount;
        public double portfolioExposure;
        public double volatility;
        public boolean userOwnsStock;
        public List<String> newsTitles;
        public long timestamp;
        
        // Supercharged fields
        public double weekChangePct;
        public double monthChangePct;
        public boolean isConsecutiveUp;
        public String userArchetype; // AGGRESSIVE, CAUTIOUS, BALANCED
        
        // Advanced Expert Constraints (Linkage Data)
        public double yearHigh;
        public double yearLow;
        public double monthHigh;
        public double monthLow;
        public double twoYearAgoPrice;
        public double sectorChangePct;
        public double indexChangePct;

        public InsightContext(String symbol) {
            this.symbol = symbol;
            this.timestamp = System.currentTimeMillis();
            this.sentiment = "NEUTRAL";
            this.userArchetype = "BALANCED";
        }
    }

    public InsightContext extractContext(String symbol, Long userId) {
        InsightContext ctx = new InsightContext(symbol);

        try {
            // 1. Price data
            Map<String, Object> quote = marketGateway.getStockQuote(symbol);
            if (quote != null) {
                ctx.currentPrice = getDouble(quote, "c", 0);
                ctx.priceChange = getDouble(quote, "d", 0);
                ctx.priceChangePct = getDouble(quote, "dp", 0);
                ctx.previousClose = getDouble(quote, "pc", 0);
                ctx.volume = getDouble(quote, "v", 0);
                
                // Fetch high/low from mirror if available (Simulation of mature data)
                ctx.yearHigh = getDouble(quote, "yearHigh", ctx.currentPrice * 1.2);
                ctx.yearLow = getDouble(quote, "yearLow", ctx.currentPrice * 0.8);
                ctx.monthHigh = getDouble(quote, "monthHigh", ctx.currentPrice * 1.05);
                ctx.monthLow = getDouble(quote, "monthLow", ctx.currentPrice * 0.95);
                ctx.twoYearAgoPrice = getDouble(quote, "twoYearPrice", ctx.currentPrice * 0.75);
            }

            // 2. Market linkages (Index vs Stock)
            String marketType = symbolNormalizer.isIndian(symbol) ? "INDIA" : "US";
            String indexSymbol = marketType.equals("INDIA") ? "^NSEI" : "SPY";
            Map<String, Object> indexQuote = marketGateway.getStockQuote(indexSymbol);
            if (indexQuote != null) {
                ctx.indexChangePct = getDouble(indexQuote, "dp", 0);
            }

            // 3. Mocked Historical Data
            ctx.weekChangePct = ctx.priceChangePct * 2.5; 
            ctx.monthChangePct = ctx.priceChangePct * 5.2; 
            ctx.isConsecutiveUp = ctx.priceChangePct > 0.5 && ctx.weekChangePct > 1.0;

            // 3. News sentiment
            List<Map<String, Object>> newsList = newsApiService.getStockNews(symbol);
            ctx.newsTitles = newsList.stream()
                .map(n -> (String) n.get("title"))
                .filter(Objects::nonNull)
                .toList();
            ctx.newsCount = ctx.newsTitles.size();
            ctx.sentiment = analyzeNewsSentiment(ctx.newsTitles);

            // 4. Behavioral Archetype (Critical for Tone)
            if (userId != null) {
                try {
                    ctx.portfolioExposure = holdingService.getUserExposure(userId, symbol);
                    ctx.userOwnsStock = ctx.portfolioExposure > 0;
                    
                    // Fetch real archetype from DecisionService logic
                    Map<String, String> behavior = decisionService.getInsights();
                    ctx.userArchetype = behavior.getOrDefault("behaviorType", "BALANCED").toUpperCase();
                } catch (Exception e) {
                    log.debug("Could not get behavioral context: {}", e.getMessage());
                }
            }

            // 5. Volatility
            ctx.volatility = calculateVolatility(ctx.priceChangePct);

            log.info("✅ Context enriched for {}: price={}, change={}%, archetype={}",
                symbol, ctx.currentPrice, ctx.priceChangePct, ctx.userArchetype);

            return ctx;

        } catch (Exception e) {
            log.error("Error extracting context for {}: {}", symbol, e.getMessage());
            ctx.sentiment = "NEUTRAL";
            return ctx;
        }
    }

    private String analyzeNewsSentiment(List<String> titles) {
        if (titles.isEmpty()) return "NEUTRAL";

        int positive = 0, negative = 0;

        for (String title : titles) {
            String lower = title.toLowerCase();

            // Positive signals
            if (lower.matches(".*\\b(surge|rally|jump|gain|bull|growth|soar|boom|up|rise|strength|profit|surge|bullish)\\b.*")) {
                positive++;
            }

            // Negative signals
            if (lower.matches(".*\\b(crash|plunge|fall|loss|bear|decline|drop|slump|down|weak|fall|bearish|losses)\\b.*")) {
                negative++;
            }
        }

        if (positive > negative) return "POSITIVE";
        if (negative > positive) return "NEGATIVE";
        return "NEUTRAL";
    }

    private double calculateVolatility(double changePct) {
        // Normalize price change to 0-1 volatility
        double absChange = Math.abs(changePct);
        return Math.min(absChange / 10.0, 1.0);
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        if (map == null || !map.containsKey(key)) return defaultValue;
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); }
            catch (Exception e) { return defaultValue; }
        }
        return defaultValue;
    }
}
