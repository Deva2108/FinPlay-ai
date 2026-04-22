package com.example.stockPortfolio.AiManagement;

import com.example.stockPortfolio.AiManagement.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/onboarding")
public class OnboardingController {

    @Autowired
    private GeminiService geminiService;

    @PostMapping("/scenario")
    public Map<String, String> getScenario(@RequestBody Map<String, String> request) {
        String userType = request.getOrDefault("userType", "STUDENT");
        String scenario = geminiService.getOnboardingScenario(userType);
        
        Map<String, String> response = new HashMap<>();
        response.put("scenario", scenario);
        return response;
    }

    @PostMapping("/feedback")
    public Map<String, String> getFeedback(@RequestBody Map<String, String> request) {
        String choice = request.getOrDefault("choice", "SPEND");
        String userType = request.getOrDefault("userType", "STUDENT");
        String feedback = geminiService.getOnboardingFeedback(choice, userType);
        
        Map<String, String> response = new HashMap<>();
        response.put("feedback", feedback);
        return response;
    }

    @PostMapping("/summary")
    public Map<String, String> getSummary(@RequestBody Map<String, Object> request) {
        java.util.List<Map<String, Object>> decisions = (java.util.List<Map<String, Object>>) request.get("decisions");
        String diagnosis = geminiService.getArenaSummary(decisions);
        
        Map<String, String> response = new HashMap<>();
        response.put("diagnosis", diagnosis);
        return response;
    }
}
