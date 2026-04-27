package com.example.stockPortfolio.MarketManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockNewsDTO {
    private String headline;
    private String source;
    private String datetime;
    private String url;
}
