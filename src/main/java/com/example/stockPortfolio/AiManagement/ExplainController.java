package com.example.stockPortfolio.AiManagement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/explain")
public class ExplainController {

    @Autowired
    private com.example.stockPortfolio.AiManagement.service.GeminiService geminiService;

    @PostMapping
    public Map<String, String> getExplanation(@RequestBody Map<String, Object> request) {
        String symbol = (String) request.getOrDefault("symbol", "the stock");
        String trend = (String) request.getOrDefault("trend", "stable");
        String action = (String) request.getOrDefault("action", "observing");
        String lang = (String) request.getOrDefault("lang", "en");
        Map<String, Object> metrics = (Map<String, Object>) request.getOrDefault("metrics", new HashMap<>());
        
        String response = geminiService.getMentorExplanation(symbol, trend, action, lang, metrics);
        
        // Structured splitting for frontend
        String[] parts = response.split("👀 Watch this:");
        Map<String, String> result = new HashMap<>();
        result.put("explanation", parts[0].trim());
        result.put("observation", parts.length > 1 ? parts[1].trim() : ("en".equals(lang) ? "Observe how volume reacts to this price level." : "Observe karein ki volume kaise react karta hai."));
        result.put("symbol", symbol);
        
        return result;
    }
}
