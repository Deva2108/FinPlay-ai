package com.example.stockPortfolio.AiManagement;

import com.example.stockPortfolio.AiManagement.service.GeminiService;
import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/ai/onboarding")
@lombok.RequiredArgsConstructor
public class OnboardingController {

    private final GeminiService geminiService;

    @PostMapping("/scenario")
    public ResponseEntity<ApiResponse<ScenarioResponseDTO>> getScenario(@RequestBody OnboardingRequestDTO request) {
        String userType = request.getUserType() != null ? request.getUserType() : "STUDENT";
        String scenario = geminiService.getOnboardingScenario(userType);
        
        ScenarioResponseDTO response = ScenarioResponseDTO.builder().scenario(scenario).build();
        return ResponseEntity.ok(ApiResponse.ok(response, "Scenario generated"));
    }

    @PostMapping("/feedback")
    public ResponseEntity<ApiResponse<FeedbackResponseDTO>> getFeedback(@RequestBody OnboardingRequestDTO request) {
        String choice = request.getChoice() != null ? request.getChoice() : "SPEND";
        String userType = request.getUserType() != null ? request.getUserType() : "STUDENT";
        String feedback = geminiService.getOnboardingFeedback(choice, userType);
        
        FeedbackResponseDTO response = FeedbackResponseDTO.builder().feedback(feedback).build();
        return ResponseEntity.ok(ApiResponse.ok(response, "Feedback generated"));
    }

    @GetMapping("/scenarios")
    public ResponseEntity<ApiResponse<MarketScenarioResponseDTO>> getScenarios(@RequestParam(defaultValue = "INDIA") String marketType) {
        List<Map<String, Object>> scenarios = geminiService.generateMarketScenarios(marketType);
        MarketScenarioResponseDTO response = MarketScenarioResponseDTO.builder().scenarios(scenarios).build();
        return ResponseEntity.ok(ApiResponse.ok(response, "Dynamic scenarios generated"));
    }

    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<DiagnosisResponseDTO>> getSummary(@RequestBody OnboardingRequestDTO request) {
        List<Map<String, Object>> decisions = request.getDecisions();
        String diagnosis = geminiService.getArenaSummary(decisions);
        
        DiagnosisResponseDTO response = DiagnosisResponseDTO.builder().diagnosis(diagnosis).build();
        return ResponseEntity.ok(ApiResponse.ok(response, "Arena summary generated"));
    }
}
