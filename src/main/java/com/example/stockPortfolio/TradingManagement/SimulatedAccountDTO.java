package com.example.stockPortfolio.TradingManagement;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Account-level summary for the in-house FinPlay paper trading simulator.
 * Computed from the existing PortfolioService balance + mark-to-market sum
 * of holdings via MarketGateway. No external broker is involved.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimulatedAccountDTO {
    private Long portfolioId;
    private String currency;        // "INR" by default
    private BigDecimal cash;            // available cash (= portfolio.balance)
    private BigDecimal positionsValue;  // mark-to-market sum of holdings
    private BigDecimal equity;          // cash + positionsValue
    private BigDecimal portfolioValue;  // alias for equity, kept for UI parity
    private BigDecimal buyingPower;     // = cash (no margin in the simulator)
    private BigDecimal initialBalance;  // starting capital
    private BigDecimal totalReturn;     // equity - initialBalance
    private BigDecimal totalReturnPct;  // %
}
