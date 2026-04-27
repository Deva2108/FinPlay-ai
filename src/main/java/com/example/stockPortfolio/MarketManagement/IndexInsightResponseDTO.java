package com.example.stockPortfolio.MarketManagement;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IndexInsightResponseDTO {
    private String explanation;
    private String observation;
}
