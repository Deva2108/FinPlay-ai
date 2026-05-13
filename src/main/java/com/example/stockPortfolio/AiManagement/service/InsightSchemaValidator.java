package com.example.stockPortfolio.AiManagement.service;

import com.example.stockPortfolio.AiManagement.RichInsightDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, dependency-free validator for FinPlay Insight JSON payloads.
 *
 * Does not depend on a full JSON Schema library — it just enforces the things
 * that actually matter at runtime so we can safely deserialize into
 * {@link RichInsightDTO}:
 *   - payload is a JSON object
 *   - every required key in {@link InsightSchema#REQUIRED_KEYS} is present
 *   - confidence (when present) is numeric and in [0,1]
 *
 * Use {@link #parseStrict(String)} for the happy path and
 * {@link #parseLenient(String)} when you want a best-effort parse for legacy
 * payloads (the four FinPlay-spec fields are filled with "Data not available").
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InsightSchemaValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Validates and returns the parsed DTO, or throws InsightValidationException. */
    public RichInsightDTO parseStrict(String json) {
        if (json == null || json.isBlank()) {
            throw new InsightValidationException("Insight payload was null/blank");
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception ex) {
            // Fallback: extract between first { and last } if the API leaked conversational text
            try {
                int start = json.indexOf('{');
                int end = json.lastIndexOf('}');
                if (start != -1 && end != -1 && start < end) {
                    node = objectMapper.readTree(json.substring(start, end + 1));
                } else {
                    throw new InsightValidationException("No JSON object found in payload");
                }
            } catch (Exception fallbackEx) {
                throw new InsightValidationException("Insight payload is not valid JSON: " + ex.getMessage());
            }
        }

        if (!node.isObject()) {
            throw new InsightValidationException("Insight payload must be a JSON object");
        }

        List<String> missing = new ArrayList<>();
        for (String key : InsightSchema.REQUIRED_KEYS) {
            if (!node.hasNonNull(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new InsightValidationException("Insight payload is missing required keys: " + missing);
        }

        JsonNode confidence = node.get("confidence");
        if (!confidence.isNumber()) {
            throw new InsightValidationException("'confidence' must be a number");
        }
        double c = confidence.asDouble();
        if (c < 0.0 || c > 1.0) {
            throw new InsightValidationException("'confidence' must be between 0.0 and 1.0 (got " + c + ")");
        }

        try {
            return objectMapper.treeToValue(node, RichInsightDTO.class);
        } catch (Exception ex) {
            throw new InsightValidationException("Could not deserialize Insight: " + ex.getMessage());
        }
    }

    /**
     * Best-effort parse — fills missing FinPlay fields with placeholder text so
     * legacy Redis payloads (with only the original five fields) still load.
     */
    public RichInsightDTO parseLenient(String json) {
        try {
            return parseStrict(json);
        } catch (InsightValidationException ex) {
            log.warn("Strict insight parse failed ({}); falling back to lenient parse", ex.getMessage());
            try {
                int start = json.indexOf('{');
                int end = json.lastIndexOf('}');
                String safeJson = (start != -1 && end != -1 && start < end) ? json.substring(start, end + 1) : json;
                RichInsightDTO dto = objectMapper.readValue(safeJson, RichInsightDTO.class);
                if (dto.getGlobalImpact() == null) dto.setGlobalImpact("Data not available");
                if (dto.getIndiaImpact() == null) dto.setIndiaImpact("Data not available");
                if (dto.getAction() == null) dto.setAction("Observe");
                if (dto.getConfidence() == null) dto.setConfidence(0.5);
                return dto;
            } catch (Exception fatal) {
                log.error("Lenient insight parse also failed: {}", fatal.getMessage());
                return null;
            }
        }
    }

    public static class InsightValidationException extends RuntimeException {
        public InsightValidationException(String message) {
            super(message);
        }
    }
}
