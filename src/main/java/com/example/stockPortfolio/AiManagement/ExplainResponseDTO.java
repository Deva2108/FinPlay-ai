package com.example.stockPortfolio.AiManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplainResponseDTO {
    private String explanation;
    private String observation;
    private String symbol;
    private String source;
    private RichInsightDTO richInsight;
}
