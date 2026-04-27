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
public class StockDetailsDTO {
    private String symbol;
    private BigDecimal price;
    private BigDecimal changesPercentage;
    private String marketCap;
    private String peRatio;
    private String dividendYield;
    private String revenue;
    private BigDecimal high52;
    private BigDecimal low52;
}
