package com.example.stockPortfolio.MarketManagement;

import com.example.stockPortfolio.AiManagement.RichInsightDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketVibeResponseDTO {
    private RichInsightDTO richInsight;
    private String simpleVibe; // Keeping for backward compatibility
}
