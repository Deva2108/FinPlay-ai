package com.example.stockPortfolio.TradingManagement;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A historical or just-placed paper trading order. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimulatedOrderDTO {
    private Long id;                    // transactionId
    private String symbol;
    private String side;                // BUY | SELL
    private String type;                // MARKET | LIMIT (always MARKET for now)
    private String status;              // FILLED (orders fill instantly in this simulator)
    private String currency;            // "INR"
    private java.math.BigDecimal quantity;
    private BigDecimal price;
    private LocalDateTime placedAt;
    private LocalDateTime filledAt;
    private BigDecimal gain;            // populated for SELL orders
    private BigDecimal gainPercentage;  // populated for SELL orders
    private String notes;
}
