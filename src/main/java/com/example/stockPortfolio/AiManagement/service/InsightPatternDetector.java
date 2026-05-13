package com.example.stockPortfolio.AiManagement.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InsightPatternDetector {

    @Getter
    public enum Pattern {
        // Legendary Expert Patterns
        BUFFETT_VALUE_ZONE("Legendary Value Zone", 0.99),
        MULTI_YEAR_SUPPORT("Multi-Year Floor Detected", 0.92),
        RELATIVE_STRENGTH("Defying Index Gravity", 0.90),
        PSYCHOLOGICAL_BARRIER("Round Number Barrier", 0.85),
        
        // Deep Multi-Day Patterns
        RELENTLESS_BULL("Relentless Bullish Trend", 0.98),
        FADING_MOMENTUM("Momentum Fading", 0.90),
        DEAD_CAT_BOUNCE("Dead Cat Bounce?", 0.85),
        COILED_SPRING("Coiled Spring (Volatility Compression)", 0.88),
        STEADY_ACCUMULATION("Steady Accumulation", 0.82),

        // Portfolio patterns
        PORTFOLIO_WINNING("Your holding is winning", 0.95),
        PORTFOLIO_LOSING("Your holding is under pressure", 0.95),

        // Price action patterns
        BULLISH_BREAKOUT("Bullish Breakout", 0.80),
        BEARISH_BREAKDOWN("Bearish Breakdown", 0.80),
        CONSOLIDATION("Healthy Consolidation", 0.70),

        // News patterns
        POSITIVE_NEWS_CATALYST("News Catalyst (Bullish)", 0.75),
        NEGATIVE_NEWS_SHOCK("News Shock (Bearish)", 0.75),

        // Volatility patterns
        OVERSOLD("Deep Oversold", 0.65),
        OVERBOUGHT("Extended Overbought", 0.65),

        // Default
        STABLE("Market Neutral", 0.50);

        @Getter
        private final String label;
        @Getter
        private final double confidence;

        Pattern(String label, double confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }

    public Pattern detect(CoreDataExtractor.InsightContext ctx) {

        // Rule 1: BUFFETT_VALUE_ZONE (Price near multi-year lows but steady)
        if (ctx.currentPrice <= ctx.twoYearAgoPrice * 1.05 && ctx.volatility < 0.3) {
            log.debug("🎯 Detected BUFFETT_VALUE_ZONE for {}", ctx.symbol);
            return Pattern.BUFFETT_VALUE_ZONE;
        }

        // Rule 2: MULTI_YEAR_SUPPORT (Hitting 1-year low with support holding)
        if (ctx.currentPrice <= ctx.yearLow * 1.02 && ctx.priceChangePct >= -0.2) {
            log.debug("🎯 Detected MULTI_YEAR_SUPPORT for {}", ctx.symbol);
            return Pattern.MULTI_YEAR_SUPPORT;
        }

        // Rule 3: RELATIVE_STRENGTH (Index down big, but stock holding green)
        if (ctx.indexChangePct < -1.0 && ctx.priceChangePct > 0.2) {
            log.debug("🎯 Detected RELATIVE_STRENGTH for {}", ctx.symbol);
            return Pattern.RELATIVE_STRENGTH;
        }

        // Rule 4: PSYCHOLOGICAL_BARRIER (Near 100, 500, 1000, etc.)
        if (isNearRoundNumber(ctx.currentPrice)) {
            log.debug("🎯 Detected PSYCHOLOGICAL_BARRIER for {}", ctx.symbol);
            return Pattern.PSYCHOLOGICAL_BARRIER;
        }

        // Rule 5: Deep Trend - RELENTLESS_BULL
        if (ctx.monthChangePct > 15.0 && ctx.isConsecutiveUp) {
            return Pattern.RELENTLESS_BULL;
        }

        // Rule 6: DEAD_CAT_BOUNCE (Down big on month, but up today)
        if (ctx.monthChangePct < -10.0 && ctx.priceChangePct > 1.5) {
            return Pattern.DEAD_CAT_BOUNCE;
        }

        // Rule 7: FADING_MOMENTUM (Up on month, but down today)
        if (ctx.monthChangePct > 5.0 && ctx.priceChangePct < -1.0) {
            return Pattern.FADING_MOMENTUM;
        }

        // Rule 8: COILED_SPRING (Low volatility but sudden volume spike or recent consolidation)
        if (ctx.volatility < 0.2 && Math.abs(ctx.priceChangePct) < 0.3 && ctx.sentiment.equals("POSITIVE")) {
            return Pattern.COILED_SPRING;
        }

        // Rule 9: User owns stock + price up
        if (ctx.userOwnsStock && ctx.priceChangePct > 1.0) {
            return Pattern.PORTFOLIO_WINNING;
        }

        // Rule 10: User owns stock + price down
        if (ctx.userOwnsStock && ctx.priceChangePct < -1.0) {
            return Pattern.PORTFOLIO_LOSING;
        }

        // Rule 11: News Catalysts
        if (ctx.sentiment.equals("POSITIVE") && ctx.priceChangePct > 0.5) {
            return Pattern.POSITIVE_NEWS_CATALYST;
        }
        if (ctx.sentiment.equals("NEGATIVE") && ctx.priceChangePct < -0.5) {
            return Pattern.NEGATIVE_NEWS_SHOCK;
        }

        // Rule 12: High volatility breakouts
        if (ctx.volatility > 0.5 && ctx.priceChangePct > 2.0) {
            return Pattern.BULLISH_BREAKOUT;
        }
        if (ctx.volatility > 0.5 && ctx.priceChangePct < -2.0) {
            return Pattern.BEARISH_BREAKDOWN;
        }

        // Rule 13: Consolidation
        if (Math.abs(ctx.priceChangePct) < 0.5 && ctx.sentiment.equals("POSITIVE")) {
            return Pattern.CONSOLIDATION;
        }

        // Rule 14: Overextended
        if (ctx.priceChangePct > 5.0) return Pattern.OVERBOUGHT;
        if (ctx.priceChangePct < -5.0) return Pattern.OVERSOLD;

        return Pattern.STABLE;
    }

    private boolean isNearRoundNumber(double price) {
        if (price < 10) return false;
        double[] roundUnits = {10, 50, 100, 500, 1000, 5000, 10000};
        for (double unit : roundUnits) {
            double remainder = price % unit;
            if (remainder < (unit * 0.005) || remainder > (unit * 0.995)) {
                return true;
            }
        }
        return false;
    }
}
