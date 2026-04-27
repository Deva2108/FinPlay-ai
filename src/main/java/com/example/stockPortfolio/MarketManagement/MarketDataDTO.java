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
public class MarketDataDTO {
    private String symbol;
    private BigDecimal price;
    private BigDecimal changesPercentage;
    private String sector;
    private String marketCap;
}
