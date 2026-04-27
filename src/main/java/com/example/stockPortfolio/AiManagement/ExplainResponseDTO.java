package com.example.stockPortfolio.AiManagement;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExplainResponseDTO {
    private String explanation;
    private String observation;
    private String symbol;
}
