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

    /**
     * Prompt focused on quantified, data-derived output. No metaphors, no
     * famous quotes, no motivational filler.
     *
     * Field semantics:
     *   analogy            → volatility / range context expressed as a number
     *                        (e.g. "ATR is 1.8% of price, above 30-day avg of 1.2%")
     *   investorPerspective → range-position or exposure note expressed as a number
     *                        (e.g. "Price at 68% of 52-week range, 4% below resistance")
     *
     * These two fields reuse existing DTO/schema keys to avoid a migration,
     * but the semantic contract is now quantitative, not anecdotal.
     */
    private String buildPrompt(String topic, String context) {
        String ctx = (context == null || context.isBlank()) ? "(none)" : context.length() > 400 ? context.substring(0, 400) : context;
        return """
                Topic: %s
                Context: %s

                Return ONE JSON object with these string keys (all required, ≤2 sentences each):
                whatHappened     – factual description of the event or price move
                whyItMatters     – direct market consequence, no filler
                globalImpact     – specific cross-market effect
                indiaImpact      – specific India/NSE effect
                whatYouCanLearn  – one actionable takeaway for a retail trader
                analogy          – quantified volatility context (e.g. "30-day realized vol is 18%%, 3pp above its 90-day avg of 15%%")
                investorPerspective – quantified range/exposure note (e.g. "Price at 72%% of 52-week range, 5%% below the 3-month resistance at 19,850")
                action           – one of: BUY | SELL | HOLD | WAIT | RESEARCH
                confidence       – decimal 0.0–1.0

                Rules: No metaphors. No famous quotes. No markdown. No surrounding prose. No nulls.
                """.formatted(topic == null ? "general market" : topic, ctx);
    }
}
