package com.example.stockPortfolio.AiManagement.service;

import com.example.stockPortfolio.AiManagement.RichInsightDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@lombok.RequiredArgsConstructor
@Slf4j
public class InsightTextGenerator {

    private final com.example.stockPortfolio.MarketManagement.SymbolNormalizer symbolNormalizer;

    public RichInsightDTO generate(
            String symbol,
            InsightPatternDetector.Pattern pattern,
            CoreDataExtractor.InsightContext ctx) {

        String whatHappened = generateWhatHappened(symbol, pattern, ctx);
        String whyItMatters = generateWhyItMatters(pattern, ctx.userArchetype);
        String action = generateAction(pattern);
        String analogy = generateAnalogy(pattern);

        return RichInsightDTO.builder()
                .whatHappened(whatHappened)
                .whyItMatters(whyItMatters)
                .globalImpact(generateGlobalImpact(pattern, ctx))
                .indiaImpact(generateIndiaImpact(symbol, pattern, ctx))
                .whatYouCanLearn(generateLearning(pattern, ctx.userArchetype))
                .analogy(analogy)
                .investorPerspective(generatePerspective(pattern))
                .action(action)
                .confidence(pattern.getConfidence())
                .build();
    }

    private String generateWhatHappened(String symbol,
                                        InsightPatternDetector.Pattern pattern,
                                        CoreDataExtractor.InsightContext ctx) {
        String archetype = ctx.userArchetype;
        
        return switch (pattern) {
            case BUFFETT_VALUE_ZONE ->
                String.format("💎 Legendary 'Buffett Zone' detected for %s. Price is hovering near its multi-year support level (₹%.0f), where value investors typically start accumulating.", 
                    symbol, ctx.twoYearAgoPrice);

            case MULTI_YEAR_SUPPORT ->
                String.format("🧱 %s has hit its 1-year low floor (₹%.0f) and is refusing to break further. This is a classic 'structural support' where strong hands are defending the level.", 
                    symbol, ctx.yearLow);

            case RELATIVE_STRENGTH ->
                String.format("🛡️ %s is defying index gravity. While the broader market (%s) is down %.1f%%, this stock is holding steady. This 'relative strength' often leads the next market recovery.", 
                    symbol, symbol.endsWith(".NS") ? "NIFTY" : "S&P 500", Math.abs(ctx.indexChangePct));

            case PSYCHOLOGICAL_BARRIER ->
                String.format("🧠 Psychological battle at ₹%.0f for %s. Round numbers act as invisible barriers where buy/sell orders tend to cluster.", 
                    Math.round(ctx.currentPrice / 100.0) * 100, symbol);

            case RELENTLESS_BULL ->
                String.format("🔥 %s is in a relentless bull run, up %.1f%% this month. This is the definition of institutional accumulation.", 
                    symbol, ctx.monthChangePct);
            
            case FADING_MOMENTUM ->
                String.format("⚠️ %s's momentum is fading. Despite being up %.1f%% this month, today's drop suggests profit-taking is starting.", 
                    symbol, ctx.monthChangePct);

            case DEAD_CAT_BOUNCE ->
                String.format("🐱 %s showing a potential 'Dead Cat Bounce'. It's up %.1f%% today, but remember it's still down %.1f%% this month.", 
                    symbol, ctx.priceChangePct, Math.abs(ctx.monthChangePct));

            case COILED_SPRING ->
                String.format("⚡ %s is a coiled spring. Volatility is crushed at low levels while sentiment stays positive. A breakout is often imminent.", 
                    symbol);

            case PORTFOLIO_WINNING ->
                archetype.equals("AGGRESSIVE") 
                    ? String.format("🚀 Your position in %s is crushing it! Up %.1f%% today. Ride the momentum.", symbol, ctx.priceChangePct)
                    : String.format("✅ %s is performing well (up %.1f%%). Your disciplined entry is paying off.", symbol, ctx.priceChangePct);

            case PORTFOLIO_LOSING ->
                archetype.equals("CAUTIOUS")
                    ? String.format("🛡️ %s is under pressure (down %.1f%%). Your cautious stance protects your overall capital.", symbol, Math.abs(ctx.priceChangePct))
                    : String.format("⚠️ Your holding in %s is in the red today (down %.1f%%). Stay objective.", symbol, Math.abs(ctx.priceChangePct));

            case BULLISH_BREAKOUT ->
                String.format("🚀 %s breaking upward! Up %.1f%% with strong momentum. The trend is your friend.",
                    symbol, ctx.priceChangePct);

            case BEARISH_BREAKDOWN ->
                String.format("📉 %s breaking downward! Down %.1f%%. Sellers have taken control of the narrative.",
                    symbol, Math.abs(ctx.priceChangePct));

            default ->
                String.format("📊 %s trading at ₹%.0f. Market sentiment is %s. Stable conditions prevail.",
                    symbol, ctx.currentPrice, ctx.sentiment);
        };
    }

    private String generateWhyItMatters(InsightPatternDetector.Pattern pattern, String archetype) {
        String base = switch (pattern) {
            case BUFFETT_VALUE_ZONE -> "Warren Buffett famously looks for '1-foot bars to step over'. Buying near multi-year support provides a significant 'Margin of Safety'.";
            case MULTI_YEAR_SUPPORT -> "When a stock hits a floor it hasn't seen in years and holds, it suggests that the 'Selling Exhaustion' phase is complete.";
            case RELATIVE_STRENGTH -> "Relative strength is the #1 indicator of a leader. If a stock won't fall when the market is crashing, imagine what it will do when the market turns green.";
            case PSYCHOLOGICAL_BARRIER -> "Round numbers create a mental anchor for investors. Breaking through or bouncing off these levels often triggers a cascade of automated trades.";
            case RELENTLESS_BULL -> "Long-term trends are driven by big institutions, not retail hype. This strength suggests deeper conviction.";
            case COILED_SPRING -> "Markets move from periods of low volatility to high volatility. Compression always precedes expansion.";
            default -> "Price movements reflect the collective wisdom (and emotion) of all market participants.";
        };

        if (archetype.equals("AGGRESSIVE")) return base + " For an aggressive trader, this is where the biggest opportunities hide.";
        if (archetype.equals("CAUTIOUS")) return base + " From a cautious perspective, risk management is more important than missing out.";
        return base;
    }

    private String generateAction(InsightPatternDetector.Pattern pattern) {
        return switch (pattern) {
            case BUFFETT_VALUE_ZONE -> "VALUE_BUY";
            case MULTI_YEAR_SUPPORT -> "ACCUMULATE";
            case RELATIVE_STRENGTH -> "WATCH_LEADERSHIP";
            case PSYCHOLOGICAL_BARRIER -> "WAIT_FOR_CONFIRMATION";
            case RELENTLESS_BULL -> "RIDE_TREND";
            case FADING_MOMENTUM -> "TRIM_POSITIONS";
            case DEAD_CAT_BOUNCE -> "AVOID_TRAP";
            case COILED_SPRING -> "PREPARE_ENTRY";
            default -> "OBSERVE";
        };
    }

    private String generateAnalogy(InsightPatternDetector.Pattern pattern) {
        return switch (pattern) {
            case BUFFETT_VALUE_ZONE -> "Think of it like buying a premium luxury car for the price of a used sedan. The value is obvious, you just need patience.";
            case MULTI_YEAR_SUPPORT -> "Like a trampoline hitting the ground—the harder the hit, the more likely the bounce if the fabric (the floor) doesn't tear.";
            case RELATIVE_STRENGTH -> "Like a swimmer going upstream. When the current (the index) stops being so strong, this swimmer will fly forward.";
            case PSYCHOLOGICAL_BARRIER -> "Like a runner hitting a 'wall' during a marathon. It takes a huge burst of mental energy to push through.";
            case RELENTLESS_BULL -> "Like a heavy freight train, this trend has massive inertia. It takes a lot to stop it once it's moving.";
            case COILED_SPRING -> "Like a runner in starting blocks, the silence before the breakout is full of potential energy.";
            default -> "Markets are like oceans - they flow with trends but also have pullbacks and corrections.";
        };
    }

    private String generateGlobalImpact(InsightPatternDetector.Pattern pattern,
                                       CoreDataExtractor.InsightContext ctx) {
        if (ctx.sentiment.equals("POSITIVE")) {
            return "Global markets reward positive sentiment. Strong companies attract international capital flows.";
        } else if (ctx.sentiment.equals("NEGATIVE")) {
            return "Global uncertainty can spread. Negative sentiment often affects multiple markets simultaneously.";
        } else {
            return "Global stability supports steady market conditions. International cooperation drives growth.";
        }
    }

    private String generateIndiaImpact(String symbol, InsightPatternDetector.Pattern pattern,
                                      CoreDataExtractor.InsightContext ctx) {
        boolean isIndian = symbolNormalizer.isIndian(symbol);

        if (isIndian) {
            return "Indian markets are driven by domestic growth, RBI policy, and FII flows. This stock reflects India's economic narrative.";
        } else {
            return "Global stocks influence Indian markets through FIIs and currency movements. Watch for correlation effects.";
        }
    }

    private String generateLearning(InsightPatternDetector.Pattern pattern, String archetype) {
        return switch (pattern) {
            case BUFFETT_VALUE_ZONE -> "Learn the 'Margin of Safety'. Price is what you pay, value is what you get.";
            case RELATIVE_STRENGTH -> "Understand 'Beta'. A stock with high relative strength is essentially outperforming its risk profile.";
            case RELENTLESS_BULL -> "Learn to 'sit on your hands'. The hardest part of a bull run is doing nothing and letting it run.";
            case COILED_SPRING -> "Watch for the 'volatility squeeze'. When Bollinger Bands narrow, a big move is coming.";
            default -> "Every market pattern has a psychological root. Understand the people, and you'll understand the price.";
        };
    }

    private String generatePerspective(InsightPatternDetector.Pattern pattern) {
        return "\"The stock market is a device for transferring money from the impatient to the patient.\" — Warren Buffett";
    }
}
