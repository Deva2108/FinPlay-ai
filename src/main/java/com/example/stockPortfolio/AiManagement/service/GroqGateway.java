package com.example.stockPortfolio.AiManagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin Groq Chat Completions client. Uses a dedicated RestTemplate (`groqRestTemplate`)
 * with tight timeouts so a stalled LLM never blocks Tomcat threads.
 *
 * Token diet: this gateway no longer uses Groq's `json_schema` strict mode —
 * the schema itself was ~500 tokens of overhead on every call. We use the
 * lighter `json_object` mode and rely on the prompt + downstream
 * {@link InsightSchemaValidator} for shape enforcement.
 */
@Service
@Slf4j
public class GroqGateway {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    public GroqGateway(@Qualifier("groqRestTemplate") RestTemplate restTemplate,
                       ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate JSON content. The {@code schema} parameter is now ignored at the
     * wire level — kept on the signature for caller compatibility. We always
     * send `response_format: { type: "json_object" }` instead of the heavier
     * `json_schema` payload.
     */
    public String generateWithSchema(String prompt, String schema) {
        return generateContent(SHORT_JSON_SYSTEM_PROMPT, prompt, true);
    }

    public String generateContent(String systemPrompt, String userPrompt) {
        // Auto-detect JSON intent from prompt content (back-compat behavior).
        boolean wantsJson = (systemPrompt != null && systemPrompt.toLowerCase().contains("json"))
                || (userPrompt != null && userPrompt.toLowerCase().contains("json"));
        return generateContent(systemPrompt, userPrompt, wantsJson);
    }

    /** Kept for source compatibility; schema arg ignored. */
    public String generateContent(String systemPrompt, String userPrompt, String schemaIgnored) {
        boolean wantsJson = schemaIgnored != null && !schemaIgnored.isBlank();
        if (!wantsJson) {
            wantsJson = (systemPrompt != null && systemPrompt.toLowerCase().contains("json"))
                    || (userPrompt != null && userPrompt.toLowerCase().contains("json"));
        }
        return generateContent(systemPrompt, userPrompt, wantsJson);
    }

    /** ~30-token system prompt; was ~120+ before. */
    private static final String SHORT_JSON_SYSTEM_PROMPT =
            "Financial analyst. Output one JSON object only. No prose, no markdown.";

    private String generateContent(String systemPrompt, String userPrompt, boolean wantsJson) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Groq API key is missing. Skipping request.");
            return null;
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("Groq API URL is missing. Skipping request.");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.1);
            // Cap output to keep responses compact and cheap.
            requestBody.put("max_tokens", 600);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt == null ? SHORT_JSON_SYSTEM_PROMPT : systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
            requestBody.put("messages", messages);

            if (wantsJson) {
                // Lightweight JSON mode — no schema serialization, ~500 fewer tokens per call.
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
        } catch (ResourceAccessException timeout) {
            // Connect/read timeout — compact warning, no stack trace. Caller falls back.
            log.warn("Groq timeout (will fall back to deterministic): {}", timeout.getMessage());
        } catch (RestClientException client) {
            log.warn("Groq HTTP error (will fall back): {}", client.getMessage());
        } catch (Exception e) {
            log.warn("Groq call failed: {}", e.getMessage());
        }
        return null;
    }
}
