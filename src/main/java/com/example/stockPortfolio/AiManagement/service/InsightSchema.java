package com.example.stockPortfolio.AiManagement.service;

/**
 * Static JSON schema for the FinPlay Insight payload. Used both as a system
 * prompt fragment for LLMs that support strict JSON schemas (Groq json_schema,
 * Gemini response_json_schema) and by {@link InsightSchemaValidator} as a
 * lightweight check on returned payloads.
 */
public final class InsightSchema {

    private InsightSchema() {}

    /** Required keys for a valid FinPlay Insight payload. */
    public static final String[] REQUIRED_KEYS = new String[] {
            "whatHappened",
            "whyItMatters",
            "globalImpact",
            "indiaImpact",
            "whatYouCanLearn",
            "analogy",
            "investorPerspective",
            "action",
            "confidence"
    };

    /** JSON Schema (Draft-07) embedded as a string for prompt usage. */
    public static final String JSON_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": [
                "whatHappened","whyItMatters","globalImpact","indiaImpact",
                "whatYouCanLearn","analogy","investorPerspective","action","confidence"
              ],
              "properties": {
                "whatHappened":        {"type": "string"},
                "whyItMatters":        {"type": "string"},
                "globalImpact":        {"type": "string"},
                "indiaImpact":         {"type": "string"},
                "whatYouCanLearn":     {"type": "string"},
                "analogy":             {"type": "string"},
                "investorPerspective": {"type": "string"},
                "action":              {"type": "string"},
                "confidence":          {"type": "number", "minimum": 0, "maximum": 1},
                "resources": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "title": {"type": "string"},
                      "url":   {"type": "string"},
                      "type":  {"type": "string", "enum": ["video","podcast","article"]}
                    },
                    "required": ["title","url"]
                  }
                }
              }
            }
            """;

    /** Drop-in instruction block to append to any insight prompt. */
    public static final String PROMPT_INSTRUCTIONS = """
            You MUST respond with a single JSON object (no surrounding text, no
            markdown fences) that exactly matches this schema:

            %s

            Constraints:
              * confidence must be a decimal between 0.0 and 1.0
              * Use a friendly mentor tone in plain language
              * No nulls; if unknown say "Data not available"
            """.formatted(JSON_SCHEMA);
}
