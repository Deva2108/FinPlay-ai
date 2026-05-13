package com.example.stockPortfolio.MarketManagement;

import com.example.stockPortfolio.AiManagement.RichInsightDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IndexInsightResponseDTO {
    private String explanation;
    private String observation;
    private RichInsightDTO richInsight;
}
