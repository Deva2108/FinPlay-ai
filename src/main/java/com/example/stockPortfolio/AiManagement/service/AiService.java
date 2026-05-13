package com.example.stockPortfolio.AiManagement.service;

import com.example.stockPortfolio.AiManagement.ExplainRequestDTO;
import com.example.stockPortfolio.AiManagement.ExplainResponseDTO;
import com.example.stockPortfolio.AiManagement.RichInsightDTO;
import com.example.stockPortfolio.VaultManagement.VaultScenarioDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiService {

    private final DeterministicInsightService deterministicInsightService;
    private final GroqGateway groqGateway;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT_JSON = 
        "You are a JSON-only financial analyst API. Output ONLY valid JSON. " +
        "No markdown, no preambles, no conversational filler. " +
        "Your output must strictly follow the requested schema.";

    public String generateRichInsight(String userPrompt) {
        try {
            String symbol = "Market";
            if (userPrompt.toLowerCase().contains("india")) symbol = "NIFTY 50";
            if (userPrompt.toLowerCase().contains("us")) symbol = "S&P 500";
            
            RichInsightDTO dto = deterministicInsightService.generateInsight(symbol, null);
            
            // Explicitly handle serialization with a fresh, safe mapper to avoid config conflicts
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dto);
        } catch (Exception e) {
            log.error("Safe AI Serialization failed: {}", e.getMessage());
            return "{\"whatHappened\":\"Market is currently in a stable consolidation phase.\", \"whyItMatters\":\"Data is being synced.\", \"action\":\"WAIT\", \"confidence\": 0.5}";
        }
    }

    public RichInsightDTO getDefaultRichInsight(String symbol, String trend, String action, String behavior) {
        // ✅ DETERMINISTIC: Use the Engine
        return deterministicInsightService.generateInsight(symbol, null);
    }

    @Cacheable(value = "aiExplanations", key = "#request.symbol + #request.trend + #request.action + #request.lang + #request.behavior", cacheManager = "cacheManager")
    public ExplainResponseDTO getStructuredExplanation(ExplainRequestDTO request) {
        // ✅ DETERMINISTIC: Use rule-based engine instead of Groq
        return deterministicInsightService.getStructuredExplanation(request);
    }

    public List<Map<String, Object>> generateMarketScenarios(String marketType) {
        // ✅ DETERMINISTIC: Return pre-canned scenarios
        return buildFallbackScenarios(marketType);
    }

    public VaultScenarioDTO generateVaultScenario(String date) {
        // ✅ DETERMINISTIC: Return pre-canned scenario
        return VaultScenarioDTO.builder()
                .scenario("A major blue-chip stock just announced a 2:1 stock split. What is your move?")
                .options(Arrays.asList("BUY", "WATCHLIST", "SKIP"))
                .correctAnswer("WATCHLIST")
                .explanation("Stock splits don't change fundamental value but can increase liquidity and retail interest.")
                .learning("Always look past cosmetic changes to underlying valuation.")
                .build();
    }

    public String getDecisionSummary(List<Map<String, Object>> decisions) {
        // ✅ DETERMINISTIC: Analyze trading pattern from decision count
        if (decisions == null || decisions.isEmpty()) {
            return "Your trading journey is just beginning. Focus on building conviction through small wins.";
        }
        int decisionCount = decisions.size();
        if (decisionCount < 5) {
            return "You're testing the waters carefully. This cautious approach builds confidence over time.";
        }
        if (decisionCount < 15) {
            return "You're actively exploring different setups. Document what works and refine your strategy.";
        }
        return "You're consistently testing the market's limits. Your next phase is learning to wait for higher-probability setups.";
    }

    public String getExplanation(String symbol, String trend, Map<String, Object> metrics) {
        // ✅ DETERMINISTIC: Explain price movement based on trend direction
        if (trend != null && trend.toLowerCase().contains("up")) {
            return symbol + " is moving upward with strong buying interest. Supply-demand balance is shifting in the bulls' favor. Like a snowball rolling downhill, momentum often builds upon itself.";
        }
        if (trend != null && trend.toLowerCase().contains("down")) {
            return symbol + " is moving downward as sellers dominate the market. Weak hands are exiting positions. Like a falling leaf, once the trend starts, multiple forces can accelerate the decline.";
        }
        return symbol + " is showing sideways movement with neutral sentiment. Market participants are assessing the next direction. This consolidation phase often precedes significant moves.";
    }

    public String getOnboardingScenario(String userType) {
        // ✅ DETERMINISTIC: Return scenario based on user type
        return switch (userType != null ? userType.toLowerCase() : "beginner") {
            case "beginner" -> "You found ₹500 in an old pair of jeans. It's enough for a nice dinner out, or you could add it to your seed capital. What would you do?";
            case "intermediate" -> "You have ₹10,000 and identified a stock showing early bullish signals. A friend recommends a \"sure-shot\" penny stock instead. What would you do?";
            case "advanced" -> "Your portfolio is up 15% this month. You're tempted to leverage 2x to accelerate gains. What would you do?";
            default -> "You have some capital and multiple investment opportunities. How will you allocate it based on your risk appetite? What would you do?";
        };
    }

    public String getOnboardingFeedback(String choice, String userType) {
        // ✅ DETERMINISTIC: Provide feedback based on choice pattern
        if (choice != null && choice.toLowerCase().contains("save")) {
            return "Smart choice! Building habit of capital preservation first creates a stronger foundation. How will you identify your next investment opportunity?";
        }
        if (choice != null && choice.toLowerCase().contains("invest")) {
            return "Good initiative! Starting early gives time for compounding. Have you thought about diversification across sectors?";
        }
        if (choice != null && choice.toLowerCase().contains("learn")) {
            return "Excellent! Education before execution prevents costly mistakes. What aspect of markets interests you most?";
        }
        return "Decisions shape your financial future. What would you do differently next time?";
    }

    public String getPortfolioMentorAdvice(List<Map<String, Object>> holdings, Double balance) {
        // ✅ DETERMINISTIC: Provide advice based on portfolio composition
        if (holdings == null || holdings.isEmpty()) {
            return "Build your foundation: start with 3-5 quality stocks across different sectors.";
        }
        if (holdings.size() < 3) {
            return "Diversification is your safety net: expand your holdings across at least 5 stocks.";
        }
        if (balance != null && balance > 10000) {
            return "You have capital available: deploy it strategically into positions with strong fundamentals.";
        }
        return "Keep diversifying your portfolio and watch your position sizes carefully.";
    }

    public String getMentorExplanation(String type, String topic, String action, String lang, String behavior, Map<String, String> context) {
        // ✅ DETERMINISTIC: Generate explanation based on topic keywords
        if (topic != null && topic.toLowerCase().contains("portfolio")) {
            return "Portfolio management is about balancing risk and return. Diversification reduces volatility while maintaining growth potential. Track allocation and rebalance quarterly.";
        }
        if (topic != null && topic.toLowerCase().contains("risk")) {
            return "Risk management protects capital. Always maintain stop-losses, position size appropriately, and never risk more than 2% on a single trade.";
        }
        if (topic != null && topic.toLowerCase().contains("technical")) {
            return "Technical analysis uses price patterns and volume. Support/resistance levels show where buyers and sellers congregate. Practice identifying these zones.";
        }
        return "Smart investing combines research with discipline. Understand what you own, why you own it, and have a clear exit plan before entering any position.";
    }

    public String getMarketPulseInsights(List<Object> usQuotes, List<Object> indiaQuotes) {
        // ✅ DETERMINISTIC: Market pulse based on comparative strength
        boolean usStrong = usQuotes != null && !usQuotes.isEmpty();
        boolean indiaStrong = indiaQuotes != null && !indiaQuotes.isEmpty();

        if (usStrong && indiaStrong) {
            return "{\"pulse\":\"Global momentum is positive\",\"note\":\"Both US and Indian markets showing strength. Risk appetite is elevated across markets.\"}";
        }
        if (usStrong) {
            return "{\"pulse\":\"US leading the charge\",\"note\":\"American markets strong. Indian markets tracking with FII inflows likely. Correlation effects in play.\"}";
        }
        if (indiaStrong) {
            return "{\"pulse\":\"Domestic strength\",\"note\":\"Indian markets strong on domestic catalysts. Global cues secondary to local drivers.\"}";
        }
        return "{\"pulse\":\"Consolidation phase\",\"note\":\"Markets taking a breather. Watch for volume patterns to signal the next directional move.\"}";
    }

    public String getTutorialInsight(String concept, String userContext) {
        // ✅ DETERMINISTIC: Tutorial insights based on concept
        if (concept != null && concept.toLowerCase().contains("pe")) {
            return "P/E Ratio compares price to earnings. Low P/E = cheap, High P/E = expensive. But cheaper isn't always better—growth stocks justify higher multiples.";
        }
        if (concept != null && concept.toLowerCase().contains("dividend")) {
            return "Dividends are profit distributions to shareholders. Yield = annual dividend / stock price. Higher yields can signal value or distress—investigate both.";
        }
        if (concept != null && concept.toLowerCase().contains("volatility")) {
            return "Volatility measures price swings. High volatility = bigger moves, bigger risk/reward. Use it to your advantage: panic selling creates buying opportunities.";
        }
        if (concept != null && concept.toLowerCase().contains("momentum")) {
            return "Momentum is the rate of price change. Strong momentum attracts more buyers. But nothing rises forever—watch for exhaustion signals.";
        }
        return "Stock markets reflect supply and demand dynamics. Understanding fundamental and technical factors helps predict price movements over time.";
    }

    public com.example.stockPortfolio.DecisionManagement.ArchetypeResponseDTO getBehavioralIdentity(List<Map<String, Object>> decisions) {
        // ✅ DETERMINISTIC: Classify trader archetype based on decision pattern
        if (decisions == null || decisions.isEmpty()) {
            return com.example.stockPortfolio.DecisionManagement.ArchetypeResponseDTO.builder()
                    .title("The Explorer")
                    .trait("Just starting the journey, open to learning and experimentation.")
                    .build();
        }

        int decisionCount = decisions.size();

        // Analyze decision frequency and patterns from decision count
        if (decisionCount < 5) {
            return com.example.stockPortfolio.DecisionManagement.ArchetypeResponseDTO.builder()
                    .title("The Cautious Learner")
                    .trait("Methodical approach, takes time to build conviction before acting.")
                    .build();
        }

        if (decisionCount < 15) {
            return com.example.stockPortfolio.DecisionManagement.ArchetypeResponseDTO.builder()
                    .title("The Active Trader")
                    .trait("Comfortable with frequent decisions, tests multiple setups to find what works.")
                    .build();
        }

        if (decisionCount < 30) {
            return com.example.stockPortfolio.DecisionManagement.ArchetypeResponseDTO.builder()
                    .title("The Consistent Operator")
                    .trait("Refined strategy with discipline, executing proven patterns repeatedly.")
                    .build();
        }

        return com.example.stockPortfolio.DecisionManagement.ArchetypeResponseDTO.builder()
                .title("The Market Veteran")
                .trait("Deep experience and refined instinct, knows when to act and when to wait.")
                .build();
    }

    private List<Map<String, Object>> buildFallbackScenarios(String marketType) {
        List<Map<String, Object>> scenarios = new ArrayList<>();
        Map<String, Object> s1 = new HashMap<>();
        s1.put("symbol", "RELIANCE");
        s1.put("name", "Reliance Industries");
        s1.put("price", 2950.0);
        s1.put("market", "IN");
        s1.put("change", "+1.2%");
        s1.put("situation", "Focusing on clean energy.");
        s1.put("context", "Building mega-factories for solar/wind.");
        s1.put("impact", "+2.3%");
        s1.put("isPositive", true);
        s1.put("explanation", "Green pivot is seen as a long-term growth driver.");
        scenarios.add(s1);
        return scenarios;
    }
}
