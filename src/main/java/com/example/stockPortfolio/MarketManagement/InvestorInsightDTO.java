package com.example.stockPortfolio.MarketManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestorInsightDTO {
    private String investor;
    private String stock;
    private String title;
    private String podcastUrl;
    private String message;
}
