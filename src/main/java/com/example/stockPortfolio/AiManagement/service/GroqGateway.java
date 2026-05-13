package com.example.stockPortfolio.AiManagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GroqGateway {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    public String generateWithSchema(String prompt, String schema) {
        String systemPrompt = "You are a professional financial analyst. You must output JSON that strictly follows the provided schema.";
        return generateContent(systemPrompt, prompt, schema);
    }

    public String generateContent(String systemPrompt, String userPrompt) {
        return generateContent(systemPrompt, userPrompt, null);
    }

    public String generateContent(String systemPrompt, String userPrompt, String schema) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Groq API key is missing. Skipping request.");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));

            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.1);

            // Structured Outputs or JSON Mode
            if (schema != null && !schema.isBlank()) {
                Map<String, Object> jsonSchema = new HashMap<>();
                jsonSchema.put("name", "finplay_insight");
                jsonSchema.put("strict", true);
                jsonSchema.put("schema", objectMapper.readTree(schema));

                requestBody.put("response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", jsonSchema
                ));
            } else if (systemPrompt.toLowerCase().contains("json") || userPrompt.toLowerCase().contains("json")) {
                requestBody.put("response_format", Map.of("type", "json_object"));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List choices = (List) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map choice = (Map) choices.get(0);
                    if (choice == null) return null;
                    Map message = (Map) choice.get("message");
                    if (message == null) return null;
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            log.error("Groq API request failed: {}", e.getMessage());
        }
        return null;
    }
}
