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

    @Cacheable(value = "aiExplanations", key = "#symbol + #trend + #action + #lang + #behavior")
    public String getMentorExplanation(String symbol, String trend, String action, String lang, String behavior, Map<String, Object> metrics) {
        if (apiKey == null || apiKey.isEmpty()) {
            return getDefaultFallback(symbol, trend, action, behavior);
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            
            String prompt = String.format(
                "Act as a calm, intelligent market mentor. User has a %s investing style. " +
                "They chose to %s %s while the market is moving %s. " +
                "Requirements: 2-3 lines max. Explain the dynamic without judgment. " +
                "End with exactly '👀 Watch this:' followed by a specific technical or behavioral observation. " +
                "Tone: Slightly human, observant, calm. Context: %s",
                behavior, action, symbol, trend, metrics.toString()
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

        return getDefaultFallback(symbol, trend, action, behavior);
    }

    private String getDefaultFallback(String symbol, String trend, String action, String behavior) {
        if ("Aggressive".equalsIgnoreCase(behavior)) {
            return "Taking a position in " + symbol + " while the trend is " + trend + " shows your preference for momentum over safety. You're acting on the current energy rather than waiting for a confirmation. 👀 Watch this: If the buying volume sustains this move or starts to fade.";
        } else if ("Cautious".equalsIgnoreCase(behavior)) {
            return "By choosing to " + action + " " + symbol + ", you're prioritizing the protection of your capital during this " + trend + " phase. You prefer clarity over the risk of a false breakout. 👀 Watch this: A retest of the recent support level to confirm a safer entry.";
        } else {
            return "The market is moving " + trend + " for " + symbol + ". Your decision to " + action + " reflects a balanced observation of the current price action. 👀 Watch this: The next major resistance level for a potential trend shift.";
        }
    }

    public String getArenaSummary(List<Map<String, Object>> decisions) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "Your decisions show a mix of momentum and caution. You're learning to identify key market floors while remaining aware of current trends.";
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String prompt = String.format(
                "User made 5 trading decisions: %s. " +
                "Task: Identify their psychological pattern (e.g. FOMO buyer, Cautious observer, Value seeker, Impulsive). " +
                "Requirements: " +
                "1. Max 2 sentences. " +
                "2. No financial advice. " +
                "3. Tone: A veteran trader who just watched their screen. Slightly harsh but highly insightful.",
                decisions.toString()
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
                return (String) ((Map) parts.get(0)).get("text");
            }
        } catch (Exception e) {
            System.err.println("Gemini Summary API Error: " + e.getMessage());
        }
        return "You're consistently testing the market's limits. Your next phase is learning to wait for higher-probability setups.";
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

    public String getGraphPointExplanation(String symbol, double price, String trend) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "At this point, " + symbol + " was at " + price + " during a " + trend + " phase. Next Step: Usually, a " + trend + " trend invites further " + (trend.equalsIgnoreCase("Rising") ? "buying" : "selling") + " pressure.";
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String prompt = String.format(
                "In 2 simple sentences, explain what happened to %s when it hit %f during a %s trend. " +
                "Then, add 'Next Step:' and explain what usually happens after this pattern in 1 sentence. " +
                "Tone: Educational, simple, like a mentor.",
                symbol, price, trend
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
                return (String) ((Map) parts.get(0)).get("text");
            }
        } catch (Exception e) {
            System.err.println("Gemini Graph API Error: " + e.getMessage());
        }
        return "At this point, " + symbol + " was at " + price + " during a " + trend + " phase. Next Step: Usually, markets consolidate before the next major move.";
    }

    public String getOnboardingScenario(String userType) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "You just received ₹2,000 from a weekend freelance gig. The hostel mess food is terrible tonight and your friends are ordering a ₹500 pizza feast, but that one stock you’ve been tracking just hit its lowest price this month.";
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String prompt = String.format(
                "Create a realistic, short money situation for a %s. " +
                "Requirements: " +
                "1. Relatable to their lifestyle (e.g., student: hostel/mess, working: salary/bills). " +
                "2. Not generic. Include a specific amount and a real-life dilemma. " +
                "3. End with 'What would you do?'. " +
                "4. Max 3 sentences. Tone: Engaging, realistic.",
                userType
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
                return (String) ((Map) parts.get(0)).get("text");
            }
        } catch (Exception e) {
            System.err.println("Gemini Onboarding API Error: " + e.getMessage());
        }
        return "You found ₹500 in an old pair of jeans. It's enough for a nice dinner out, or you could add it to your seed capital.";
    }

    public String getOnboardingFeedback(String choice, String userType) {
        if (apiKey == null || apiKey.isEmpty()) {
            return choice.equalsIgnoreCase("SPEND") 
                ? "Choosing to spend prioritizes immediate utility over future growth. It feels good now, but tomorrow's self might have preferred the compounding."
                : "A disciplined choice. You're prioritizing long-term wealth over short-term gratification.";
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String prompt = String.format(
                "User is a %s. They chose to %s in a money situation. " +
                "Explain in 2 lines: " +
                "1. What this choice means for their wealth. " +
                "2. What could have been a smarter alternative or how to improve. " +
                "Tone: Calm, smart, not judging. End with a curious question.",
                userType, choice
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
                return (String) ((Map) parts.get(0)).get("text");
            }
        } catch (Exception e) {
            System.err.println("Gemini Feedback API Error: " + e.getMessage());
        }
        return "Decisions shape your financial future. What would you do differently next time?";
    }
}
