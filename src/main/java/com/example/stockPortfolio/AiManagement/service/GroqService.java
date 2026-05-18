package com.example.stockPortfolio.AiManagement.service;

import com.example.stockPortfolio.AiManagement.RichInsightDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * High-level Groq-backed insight service.
 *
 * Returns a fully-validated {@link RichInsightDTO} or null. Safe to call from
 * the scheduler (never from a request thread directly).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GroqService {

    private final GroqGateway groqGateway;
    private final InsightSchemaValidator validator;
    private final ObjectMapper objectMapper;

    /**
     * Generate a strict-schema FinPlay insight for a topic. The prompt is built
     * with the provided context and the canonical {@link InsightSchema}
     * instructions are appended.
     */
    public RichInsightDTO generateInsight(String topic, String context) {
        String prompt = buildPrompt(topic, context);
        String raw = groqGateway.generateWithSchema(prompt, InsightSchema.JSON_SCHEMA);
        if (raw == null) {
            log.warn("Groq returned no payload for topic '{}'", topic);
            return new RichInsightDTO();
        }
        try {
            return validator.parseStrict(raw);
        } catch (InsightSchemaValidator.InsightValidationException ex) {
            log.warn("Strict validation failed for topic '{}': {}. Falling back to lenient.", topic, ex.getMessage());
            return validator.parseLenient(raw);
        }
    }

    /** Returns the raw JSON string (validated). Useful when you want to cache the wire format. */
    public String generateInsightJson(String topic, String context) {
        RichInsightDTO dto = generateInsight(topic, context);
        if (dto == null) return null;
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            log.error("Failed to serialize validated insight: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(String topic, String context) {
        return """
                Topic: %s

                Context (may be empty): %s

                Write a FinPlay insight that explains the topic to a curious Indian beginner.
                Connect the global picture to the Indian market where relevant.
                Always answer 'so what?' for the user.

                %s
                """.formatted(topic == null ? "general market" : topic,
                        context == null ? "" : context,
                        InsightSchema.PROMPT_INSTRUCTIONS);
    }
}
