package com.example.stockPortfolio.HoldingsManagement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HoldingStatusDTO {
    private String symbol;
    private String companyName;
    private String sector;
    private int quantity;
    private java.math.BigDecimal buyPrice;
    private java.math.BigDecimal currentPrice;
    private java.math.BigDecimal gain;
    private java.math.BigDecimal gainPercentage;
}
