package com.example.stockPortfolio.HoldingsManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HoldingStatusDTO {
    private String symbol;
    private String companyName;
    private String sector;
    private String market;
    private String currency; // Always "INR" for the final value, but good to have
    private java.math.BigDecimal quantity;
    private java.math.BigDecimal buyPrice;

    private java.math.BigDecimal currentPrice;
    private java.math.BigDecimal gain;
    private java.math.BigDecimal gainPercentage;
}
