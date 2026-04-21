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
        String behavior = (String) request.getOrDefault("behavior", "Balanced");
        String type = (String) request.getOrDefault("type", "general");
        Map<String, Object> metrics = (Map<String, Object>) request.getOrDefault("metrics", new HashMap<>());
        
        String response;
        if ("graph_point".equals(type)) {
            double price = Double.valueOf(metrics.getOrDefault("price", 0.0).toString());
            response = geminiService.getGraphPointExplanation(symbol, price, trend);
        } else {
            response = geminiService.getMentorExplanation(symbol, trend, action, lang, behavior, metrics);
        }
        
        // Structured splitting for frontend
        String delimiter = response.contains("Next Step:") ? "Next Step:" : "👀 Watch this:";
        String[] parts = response.split(delimiter);
        Map<String, String> result = new HashMap<>();
        result.put("explanation", parts[0].trim());
        result.put("observation", parts.length > 1 ? parts[1].trim() : ("en".equals(lang) ? "Observe how volume reacts to this price level." : "Observe karein ki volume kaise react karta hai."));
        result.put("symbol", symbol);
        
        return result;
    }
}
