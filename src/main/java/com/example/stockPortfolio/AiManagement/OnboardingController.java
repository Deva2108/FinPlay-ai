package com.example.stockPortfolio.AiManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import com.example.stockPortfolio.MarketManagement.MarketGateway;
import com.example.stockPortfolio.UserManagement.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/ai/onboarding")
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class OnboardingController {

    private final MarketGateway marketGateway;
    private final UserService userService;
    private final com.example.stockPortfolio.AiManagement.service.AiService aiService;

    @PostMapping("/scenario")
    public ResponseEntity<ApiResponse<ScenarioResponseDTO>> getScenario(@RequestBody OnboardingRequestDTO request) {
        String userType = request.getUserType() != null ? request.getUserType() : "STUDENT";

        String scenario = marketGateway.getOnboardingScenario(userType);
        if (scenario == null) {
            scenario = aiService.getOnboardingScenario(userType);
            return ResponseEntity.ok(ApiResponse.syncing(
                    ScenarioResponseDTO.builder().scenario(scenario).build(),
                    "Your personalized onboarding is being prepared",
                    "fallback"
            ));
        }

        return ResponseEntity.ok(ApiResponse.ok(
                ScenarioResponseDTO.builder().scenario(scenario).build(),
                "Scenario fetched from cache",
                "cache"
        ));
    }

    @PostMapping("/feedback")
    public ResponseEntity<ApiResponse<FeedbackResponseDTO>> getFeedback(@RequestBody OnboardingRequestDTO request) {
        String choice = request.getChoice() != null ? request.getChoice() : "SPEND";
        String userType = request.getUserType() != null ? request.getUserType() : "STUDENT";

        String userKey = resolveUserKey(request);
        String feedback = marketGateway.getOnboardingFeedback(userKey);
        
        // OPTIMIZATION: Check global key if user-specific feedback is empty (H9)
        if (feedback == null) {
            feedback = marketGateway.getOnboardingFeedback("global");
        }

        if (feedback == null) {
            feedback = aiService.getOnboardingFeedback(choice, userType);
            return ResponseEntity.ok(ApiResponse.syncing(
                    FeedbackResponseDTO.builder().feedback(feedback).build(),
                    "Your personalized onboarding is being prepared",
                    "fallback"
            ));
        }

        return ResponseEntity.ok(ApiResponse.ok(
                FeedbackResponseDTO.builder().feedback(feedback).build(),
                "Feedback fetched from cache",
                "cache"
        ));
    }

    @GetMapping("/scenarios")
    public ResponseEntity<ApiResponse<MarketScenarioResponseDTO>> getScenarios(@RequestParam(defaultValue = "INDIA") String marketType) {
        List<Map<String, Object>> scenarios = aiService.generateMarketScenarios(marketType);
        MarketScenarioResponseDTO response = MarketScenarioResponseDTO.builder().scenarios(scenarios).build();
        return ResponseEntity.ok(ApiResponse.ok(response, "Market scenarios loaded"));
    }

    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<DiagnosisResponseDTO>> getSummary(@RequestBody OnboardingRequestDTO request) {
        List<Map<String, Object>> decisions = request.getDecisions();

        String sessionKey = resolveUserKey(request);
        String diagnosis = marketGateway.getOnboardingSummary(sessionKey);
        if (diagnosis == null) {
            diagnosis = buildFallbackDiagnosis(decisions);
            return ResponseEntity.ok(ApiResponse.syncing(
                    DiagnosisResponseDTO.builder().diagnosis(diagnosis).build(),
                    "Your trading summary is being prepared",
                    "fallback"
            ));
        }

        DiagnosisResponseDTO response = DiagnosisResponseDTO.builder().diagnosis(diagnosis).build();
        return ResponseEntity.ok(ApiResponse.ok(response, "Trading summary fetched from cache", "cache"));
    }

    private String resolveUserKey(OnboardingRequestDTO request) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            com.example.stockPortfolio.UserManagement.User user = userService.getUserByEmail(email);
            if (user != null && user.getUserId() != null) {
                return String.valueOf(user.getUserId());
            }
        } catch (Exception e) {
            log.debug("Unable to resolve user from security context: {}", e.getMessage());
        }

        if (request != null && request.getSessionId() != null && !request.getSessionId().isBlank()) {
            return request.getSessionId().trim().toLowerCase(Locale.ROOT);
        }

        return "default";
    }

    private String buildFallbackFeedback(String choice, String userType) {
        if ("GROW".equalsIgnoreCase(choice)) {
            return "Compounding works best when you stay patient and keep adding consistently. For a " + userType.toLowerCase(Locale.ROOT) + ", small steady steps beat quick wins.";
        }
        return "Spending now can feel rewarding, but every choice trains your future habits. For a " + userType.toLowerCase(Locale.ROOT) + ", think about what one small pause could protect.";
    }

    private String buildFallbackDiagnosis(List<Map<String, Object>> decisions) {
        int buyLike = 0;
        int sellLike = 0;
        int holdLike = 0;
        List<Map<String, Object>> safeDecisions = decisions != null ? decisions : Collections.<Map<String, Object>>emptyList();

        for (Map<String, Object> decision : safeDecisions) {
            String action = decision != null && decision.get("action") != null ? decision.get("action").toString().toUpperCase(Locale.ROOT) : "";
            if (action.contains("BUY") || action.contains("GROW")) {
                buyLike++;
            } else if (action.contains("SELL") || action.contains("SPEND")) {
                sellLike++;
            } else {
                holdLike++;
            }
        }

        return String.format(
                "You are balancing %d growth moves, %d spending impulses, and %d neutral moments. That usually means you're learning when to act and when to wait.",
                buyLike, sellLike, holdLike
        );
    }

    private List<Map<String, Object>> buildStaticScenarios(String marketType) {
        List<Map<String, Object>> scenarios = new ArrayList<>();

        Map<String, Object> marketCorrection = new HashMap<>();
        marketCorrection.put("title", marketType.toUpperCase(Locale.ROOT) + " Market Correction");
        marketCorrection.put("description", "Indices are down 5% today. How do you react?");
        marketCorrection.put("options", List.of("Buy Dip", "Sell All", "Hold"));
        scenarios.add(marketCorrection);

        Map<String, Object> cashFlow = new HashMap<>();
        cashFlow.put("title", "Unexpected Cash Flow");
        cashFlow.put("description", "You receive extra income this month. What do you do first?");
        cashFlow.put("options", List.of("Invest", "Spend", "Save"));
        scenarios.add(cashFlow);

        return scenarios;
    }
}
