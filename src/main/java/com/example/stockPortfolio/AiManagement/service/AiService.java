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

            RichInsightDTO dto = null;
            try {
                Object result = deterministicInsightService.generateInsight(symbol, null);
                if (result instanceof RichInsightDTO) {
                    dto = (RichInsightDTO) result;
                } else if (result instanceof Map) {
                    dto = objectMapper.convertValue(result, RichInsightDTO.class);
                }
            } catch (Exception cacheEx) {
                log.warn("Deterministic insight unavailable (non-fatal), continuing with fallback: {}", cacheEx.getMessage());
            }

            if (dto == null) {
                return "{\"whatHappened\":\"Market is currently in a stable consolidation phase.\", \"whyItMatters\":\"Data is being synced.\", \"action\":\"WAIT\", \"confidence\": 0.5}";
            }

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
        if (decisions == null || decisions.isEmpty()) {
            return "0 decisions logged.";
        }
        int buys = 0;
        java.util.Set<String> symbols = new java.util.HashSet<>();
        for (Map<String, Object> d : decisions) {
            if ("BUY".equalsIgnoreCase(String.valueOf(d.get("action")))) buys++;
            Object s = d.get("symbol");
            if (s != null) symbols.add(String.valueOf(s));
        }
        int total = decisions.size();
        double convictionPct = (buys * 100.0) / total;
        return String.format("%d decisions across %d symbols. %.0f%% conviction (%d BUY / %d SKIP).",
                total, symbols.size(), convictionPct, buys, total - buys);
    }

    public String getExplanation(String symbol, String trend, Map<String, Object> metrics) {
        // Factual one-liner — no metaphors. If the caller passes a `price` metric
        // we surface it; otherwise we just describe the direction.
        Double price = metrics == null ? null : (metrics.get("price") instanceof Number n ? n.doubleValue() : null);
        String dir;
        if (trend != null && trend.toLowerCase().contains("up")) dir = "up";
        else if (trend != null && trend.toLowerCase().contains("down")) dir = "down";
        else dir = "flat";
        if (price != null) {
            return String.format("%s trending %s at %.2f. Direction reflects net buyer/seller imbalance over the prior point.", symbol, dir, price);
        }
        return String.format("%s trending %s. Direction reflects net buyer/seller imbalance over the prior point.", symbol, dir);
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

    /**
     * Quantitative portfolio commentary. Computes concentration, win rate, and
     * cash ratio directly from the caller's data — no metaphors, no motivational
     * filler. Every clause is derived from a numeric input.
     */
    public String getPortfolioMentorAdvice(List<Map<String, Object>> holdings, Double balance) {
        if (holdings == null || holdings.isEmpty()) {
            return "No open positions. Cash balance is your full risk budget — sizing the first position at 5–10% of capital caps single-name exposure.";
        }
        int n = holdings.size();

        double totalValue = 0.0;
        double topValue = 0.0;
        String topSymbol = null;
        int winners = 0;
        for (Map<String, Object> h : holdings) {
            double cv = toDouble(h.get("currentValue"));
            if (cv <= 0) cv = toDouble(h.get("invested"));
            totalValue += cv;
            if (cv > topValue) { topValue = cv; topSymbol = String.valueOf(h.get("symbol")); }
            if (toDouble(h.get("gain")) > 0 || toDouble(h.get("gainVal")) > 0) winners++;
        }
        double concentrationPct = totalValue > 0 ? (topValue / totalValue) * 100.0 : 0.0;
        double winRatePct = n > 0 ? (winners * 100.0 / n) : 0.0;
        double cash = balance == null ? 0.0 : balance;
        double cashRatioPct = (cash + totalValue) > 0 ? (cash / (cash + totalValue)) * 100.0 : 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d open positions worth ₹%,.0f. ", n, totalValue));
        if (topSymbol != null && !"null".equals(topSymbol)) {
            sb.append(String.format("Largest is %s at %.0f%% of book", topSymbol, concentrationPct));
            if (concentrationPct >= 50) sb.append(" — single-name risk dominates returns.");
            else if (concentrationPct >= 35) sb.append(" — moderately concentrated.");
            else sb.append(" — within balanced range.");
            sb.append(' ');
        }
        sb.append(String.format("%d/%d positions in profit (%.0f%% win rate). ", winners, n, winRatePct));
        sb.append(String.format("Cash ratio %.0f%% of total capital.", cashRatioPct));
        return sb.toString();
    }

    /**
     * Quantitative mentor explanation. The caller passes a metric-bearing
     * context map (rangePosition, volatility, breadth, etc.); we surface the
     * actual numbers rather than generic wisdom.
     */
    public String getMentorExplanation(String type, String topic, String action, String lang, String behavior, Map<String, String> context) {
        Map<String, String> ctx = context == null ? Map.of() : context;
        String t = topic == null ? "" : topic.toLowerCase();

        // Range position / volatility (numeric)
        Double rangePos = parseDouble(ctx.get("rangePosition"));
        Double volatility = parseDouble(ctx.get("volatility"));
        Double breadth = parseDouble(ctx.get("breadth"));
        Double pctOffHigh = parseDouble(ctx.get("pctOffHigh"));

        StringBuilder sb = new StringBuilder();
        if (rangePos != null) {
            sb.append(String.format("Price sits %.0f%% into today's range%s. ",
                    rangePos, rangePos >= 70 ? " (upper third)" : rangePos <= 30 ? " (lower third)" : ""));
        }
        if (volatility != null) {
            sb.append(String.format("Recent volatility reads %.2f%%. ", volatility));
        }
        if (pctOffHigh != null) {
            sb.append(String.format("Currently %.1f%% off its recent high. ", pctOffHigh));
        }
        if (breadth != null) {
            sb.append(String.format("Market breadth: %.0f%% of tracked names advancing. ", breadth));
        }
        if (sb.length() > 0) return sb.toString().trim();

        // No numeric context provided — return a topic-aware but factual line
        // (still no motivational filler).
        if (t.contains("portfolio")) return "Portfolio commentary needs holdings data. Pass current positions to receive concentration, win-rate, and drift metrics.";
        if (t.contains("risk"))      return "Risk commentary needs position-size and volatility inputs. Pass `volatility` or per-position weights in context.";
        if (t.contains("technical")) return "Technical commentary needs price-series inputs. Pass `rangePosition` or `pctOffHigh` in context.";
        return "No numeric context provided. Pass `rangePosition`, `volatility`, `breadth`, or `pctOffHigh` to receive a quantitative read.";
    }

    /**
     * Market pulse driven by actual counts, not boolean flags. Compares the
     * advancers/decliners proxy from the two quote arrays.
     */
    public String getMarketPulseInsights(List<Object> usQuotes, List<Object> indiaQuotes) {
        int us = usQuotes == null ? 0 : usQuotes.size();
        int in_ = indiaQuotes == null ? 0 : indiaQuotes.size();
        int total = us + in_;
        if (total == 0) {
            return "{\"pulse\":\"No data\",\"note\":\"No quote sample available. Market cache may be cold.\"}";
        }
        double usShare = (us * 100.0) / total;
        String pulse;
        if (us > 0 && in_ > 0) {
            if (Math.abs(usShare - 50.0) < 10.0) pulse = "Balanced participation";
            else if (usShare > 50.0) pulse = String.format("US-led (%.0f%% of sample)", usShare);
            else pulse = String.format("India-led (%.0f%% of sample)", 100.0 - usShare);
        } else if (us > 0) {
            pulse = "US-only sample";
        } else {
            pulse = "India-only sample";
        }
        return String.format("{\"pulse\":\"%s\",\"note\":\"Sample size %d (US=%d, IN=%d).\"}", pulse, total, us, in_);
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0.0; }
    }
    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
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

    /**
     * Archetype derived from real signal — not just count buckets. Computes the
     * BUY/SKIP ratio (conviction) and decision cadence (frequency), then returns
     * a label + a quantitative trait line. No motivational wording.
     */
    public com.example.stockPortfolio.DecisionManagement.ArchetypeResponseDTO getBehavioralIdentity(List<Map<String, Object>> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return com.example.stockPortfolio.DecisionManagement.ArchetypeResponseDTO.builder()
                    .title("No pattern yet")
                    .trait("0 decisions logged. Pattern unlocks after the first 3 entries.")
                    .build();
        }

        int total = decisions.size();
        int buys = 0;
        long firstTs = Long.MAX_VALUE, lastTs = 0L;
        for (Map<String, Object> d : decisions) {
            Object a = d.get("action");
            if (a != null && "BUY".equalsIgnoreCase(String.valueOf(a))) buys++;
            long ts = (long) toDouble(d.get("timestamp"));
            if (ts > 0) {
                if (ts < firstTs) firstTs = ts;
                if (ts > lastTs) lastTs = ts;
            }
        }
        int skips = total - buys;
        double convictionPct = total > 0 ? (buys * 100.0 / total) : 0.0;

        // Cadence: decisions per day across the window. Returns -1 if no usable timestamps.
        double perDay = -1;
        if (firstTs != Long.MAX_VALUE && lastTs > firstTs) {
            double days = Math.max(1.0, (lastTs - firstTs) / 86_400_000.0);
            perDay = total / days;
        }

        String label;
        if (total < 5) label = "Sample too small";
        else if (convictionPct >= 70) label = "Decisive";
        else if (convictionPct >= 40) label = "Balanced";
        else label = "Selective";

        String trait = String.format("%d decisions logged: %d BUY / %d SKIP (%.0f%% conviction).",
                total, buys, skips, convictionPct);
        if (perDay > 0) trait += String.format(" Average cadence: %.1f decisions/day.", perDay);

        return com.example.stockPortfolio.DecisionManagement.ArchetypeResponseDTO.builder()
                .title(label)
                .trait(trait)
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
