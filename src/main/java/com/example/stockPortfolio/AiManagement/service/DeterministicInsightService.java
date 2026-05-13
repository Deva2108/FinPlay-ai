package com.example.stockPortfolio.AiManagement.service;

import com.example.stockPortfolio.AiManagement.ExplainRequestDTO;
import com.example.stockPortfolio.AiManagement.ExplainResponseDTO;
import com.example.stockPortfolio.AiManagement.RichInsightDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeterministicInsightService {

    private final CoreDataExtractor dataExtractor;
    private final InsightPatternDetector patternDetector;
    private final InsightTextGenerator textGenerator;

    /**
     * Main insight generation: Deterministic + Fast + Reliable
     */
    @Cacheable(value = "insights", key = "#symbol + '_' + (#userId != null ? #userId : 'anonymous')")
    public RichInsightDTO generateInsight(String symbol, Long userId) {
        try {
            // Step 1: Extract data (use cached values from MarketGateway)
            CoreDataExtractor.InsightContext ctx = dataExtractor.extractContext(symbol, userId);
            log.info("📊 Extracted context for {}: price={}, change={}%, sentiment={}",
                symbol, ctx.currentPrice, ctx.priceChangePct, ctx.sentiment);

            // Step 2: Detect pattern (rules-based)
            InsightPatternDetector.Pattern pattern = patternDetector.detect(ctx);
            log.info("🎯 Detected pattern for {}: {}", symbol, pattern);

            // Step 3: Generate text (deterministic)
            RichInsightDTO insight = textGenerator.generate(symbol, pattern, ctx);
            log.info("✅ Generated insight for {}: {}", symbol, insight.getWhatHappened());

            // Step 4: Map to Content Topic (Shorts/Podcasts)
            String topic = mapPatternToTopic(pattern);
            insight.setTopic(topic);

            return insight;

        } catch (Exception e) {
            log.error("❌ Error generating insight for {}: {}", symbol, e.getMessage(), e);
            return RichInsightDTO.builder()
                    .whatHappened("Market data is currently updating. Please check back in a moment.")
                    .whyItMatters("We're refreshing market information for you.")
                    .globalImpact("Real-time data ensures accuracy.")
                    .indiaImpact("Market data updates happen every minute.")
                    .whatYouCanLearn("Patience is part of smart investing.")
                    .analogy("Like waiting for weather data before flying, accurate information matters.")
                    .investorPerspective("Good decisions come from good data.")
                    .action("WAIT")
                    .confidence(0.3)
                    .build();
        }
    }

    private String mapPatternToTopic(InsightPatternDetector.Pattern pattern) {
        return switch (pattern) {
            case BUFFETT_VALUE_ZONE -> "Warren Buffett value investing strategy";
            case MULTI_YEAR_SUPPORT -> "how to identify stock support and resistance levels";
            case RELATIVE_STRENGTH -> "relative strength stock market indicator explained";
            case PSYCHOLOGICAL_BARRIER -> "psychological price levels in trading";
            case RELENTLESS_BULL -> "how to trade a parabolic stock bull run";
            case COILED_SPRING -> "volatility squeeze trading strategy #shorts";
            case DEAD_CAT_BOUNCE -> "what is a dead cat bounce stock #shorts";
            case PORTFOLIO_WINNING -> "trailing stop loss strategy for winning stocks";
            case OVERBOUGHT -> "how to identify overbought stocks RSI";
            case OVERSOLD -> "buying the dip oversold stocks strategy";
            default -> "stock market basics for beginners";
        };
    }

    /**
     * Fallback for existing getStructuredExplanation
     */
    public ExplainResponseDTO getStructuredExplanation(ExplainRequestDTO request) {
        String symbol = request.getSymbol() != null ? request.getSymbol() : "the stock";

        RichInsightDTO insight = generateInsight(symbol, null);

        return ExplainResponseDTO.builder()
                .explanation(insight.getWhatHappened())
                .observation(insight.getWhatYouCanLearn())
                .symbol(symbol)
                .richInsight(insight)
                .source("DETERMINISTIC_ENGINE")
                .build();
    }

    /**
     * Generate insight with user context
     */
    public RichInsightDTO generateInsightWithUser(String symbol, Long userId) {
        return generateInsight(symbol, userId);
    }
}
