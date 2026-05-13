package com.example.stockPortfolio.TradingManagement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Body for POST /api/trading/paper/orders. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulatedOrderRequestDTO {

    @NotNull(message = "portfolioId is required")
    private Long portfolioId;

    @NotBlank(message = "symbol is required")
    @Size(max = 20, message = "symbol too long")
    private String symbol;

    /** BUY | SELL — case-insensitive, validated explicitly to give a clean 400. */
    @NotBlank(message = "side is required")
    @Pattern(regexp = "(?i)^(BUY|SELL)$", message = "side must be BUY or SELL")
    private String side;

    /**
     * Quantity must be strictly positive AND within sane bounds.
     * Negative or zero values used to be the gateway to a money-printing exploit
     * (negative qty × positive price = negative transactionAmount = free balance top-up).
     */
    @NotNull(message = "quantity is required")
    @DecimalMin(value = "0.0001", inclusive = true, message = "quantity must be at least 0.0001")
    @Positive(message = "quantity must be positive")
    private BigDecimal quantity;

    /**
     * Optional override price (currently ignored — the server always fills at the
     * latest MarketGateway quote). Validated only to reject obviously malformed input.
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "price must be positive when supplied")
    private BigDecimal price;

    @Size(max = 280, message = "notes too long")
    private String notes;
}
