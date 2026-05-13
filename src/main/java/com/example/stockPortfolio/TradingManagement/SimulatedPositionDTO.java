package com.example.stockPortfolio.TradingManagement;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Per-symbol position with mark-to-market P&L. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimulatedPositionDTO {
    private String symbol;
    private String companyName;
    private String sector;
    private String currency; // "INR"
    private java.math.BigDecimal quantity;
    private BigDecimal avgEntryPrice;
    private BigDecimal currentPrice;
    private BigDecimal costBasis;        // avgEntryPrice * quantity
    private BigDecimal marketValue;      // currentPrice * quantity
    private BigDecimal unrealizedPl;     // marketValue - costBasis
    private BigDecimal unrealizedPlpc;   // gain percentage
}
