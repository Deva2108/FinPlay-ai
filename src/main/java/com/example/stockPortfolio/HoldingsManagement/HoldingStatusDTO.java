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
    private double buyPrice;
    private double currentPrice;
    private double gain;
    private double gainPercentage;
}
