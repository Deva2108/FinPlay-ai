package com.example.stockPortfolio.MarketManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockQuoteDTO {
    private String symbol;
    private BigDecimal price;
    private BigDecimal changesPercentage;
    private String companyName;
}
