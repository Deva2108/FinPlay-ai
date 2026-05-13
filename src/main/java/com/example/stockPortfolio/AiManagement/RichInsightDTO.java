package com.example.stockPortfolio.AiManagement;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * FinPlay Insight DTO (MANDATORY).
 * Strictly follows Section 4.1 of the Production Requirements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RichInsightDTO {
    private String whatHappened;
    private String whyItMatters;
    private String globalImpact;
    private String indiaImpact;
    private String whatYouCanLearn;
    private String analogy;
    private String investorPerspective;
    private String action;
    private Double confidence; // 0.0 - 1.0
    private String topic; // Multimedia search topic
    private List<Map<String, String>> resources;
}
