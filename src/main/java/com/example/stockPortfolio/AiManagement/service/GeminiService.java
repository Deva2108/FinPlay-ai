package com.example.stockPortfolio.AiManagement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

@Service
public class GeminiService {

    @Value("${GEMINI_API_KEY:${gemini.api.key:}}")
    private String apiKey;

    private final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    @Cacheable(value = "aiExplanations", key = "#symbol + #trend + #action + #lang")
    public String getMentorExplanation(String symbol, String trend, String action, String lang, Map<String, Object> metrics) {
        if (apiKey == null || apiKey.isEmpty()) {
            return getDefaultFallback(symbol, trend, action, lang);
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            
            String languageRule = "en".equalsIgnoreCase(lang) 
                ? "Use simple, clean English. Global tone."
                : "Use natural Hinglish (spoken tone, Latin script). No pure Hindi. Relatable for Indian users.";

            String prompt = String.format(
                "Act as a supportive Market Mentor. Explain what is happening with %s stock. " +
                "User chose to: %s. Market trend is: %s. " +
                "Requirements: %s " +
                "Short explanation (max 2 lines). " +
                "No judgement. No 'wrong decision'. No jargon. " +
                "End with exactly '👀 Watch this:' followed by one specific technical observation. " +
                "Max 3 lines total. Context: %s",
                symbol, action, trend, languageRule, metrics.toString()
            );

            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            Map<String, Object> content = new HashMap<>();
            content.put("parts", Collections.singletonList(part));
            requestBody.put("contents", Collections.singletonList(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(API_URL + apiKey, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                List candidates = (List) response.getBody().get("candidates");
                Map candidate = (Map) candidates.get(0);
                Map contentResult = (Map) candidate.get("content");
                List parts = (List) contentResult.get("parts");
                Map partResult = (Map) parts.get(0);
                return (String) partResult.get("text");
            }
        } catch (Exception e) {
            System.err.println("Gemini Mentor API Error: " + e.getMessage());
        }

        return getDefaultFallback(symbol, trend, action, lang);
    }

    private String getDefaultFallback(String symbol, String trend, String action, String lang) {
        if ("en".equalsIgnoreCase(lang)) {
            return "The market is showing " + trend + " signs for " + symbol + ". Your move to " + action + " helps in observing price stability. 👀 Watch this: Look for a breakout above the recent high.";
        } else {
            return "Market " + trend + " signs dikha raha hai " + symbol + " ke liye. Aapka " + action + " ka decision price levels observe karne mein help karega. 👀 Watch this: Agla major resistance level break hota hai ya nahi.";
        }
    }

    public String getExplanation(String symbol, String trend, Map<String, Object> metrics) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "AI Insight: " + symbol + " is showing " + trend + " momentum. Our analysis suggests high market engagement based on current volume.";
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            
            String prompt = String.format(
                "Explain in very simple words why %s stock is moving %s. Use a real-life analogy. Avoid jargon. Max 3 lines. Context: %s",
                symbol, trend, metrics.toString()
            );

            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            
            Map<String, Object> content = new HashMap<>();
            content.put("parts", Collections.singletonList(part));
            
            requestBody.put("contents", Collections.singletonList(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(API_URL + apiKey, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                List candidates = (List) response.getBody().get("candidates");
                Map candidate = (Map) candidates.get(0);
                Map contentResult = (Map) candidate.get("content");
                List parts = (List) contentResult.get("parts");
                Map partResult = (Map) parts.get(0);
                return (String) partResult.get("text");
            }
        } catch (Exception e) {
            System.err.println("Gemini API Error: " + e.getMessage());
        }

        return "AI Insight: Unable to connect to live brain. Based on patterns, " + symbol + " is in a " + trend + " phase.";
    }
}
