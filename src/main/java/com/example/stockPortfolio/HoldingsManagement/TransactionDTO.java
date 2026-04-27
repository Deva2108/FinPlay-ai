package com.example.stockPortfolio.HoldingsManagement;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {
    private Long transactionId;
    private Long userId;
    
    @NotNull(message = "Portfolio ID is required")
    private Long portfolioId;
    
    @NotBlank(message = "Symbol is required")
    private String symbol;
    
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1000, message = "Educational limit: Max 1000 shares per trade")
    private int quantity;
    
    @DecimalMin(value = "1.0", message = "Price must be at least $1.00")
    @DecimalMax(value = "100000.0", message = "Price exceeds playground limits")
    private java.math.BigDecimal price;
    
    @NotBlank(message = "Transaction type (BUY/SELL) is required")
    private String type;
    
    private java.time.LocalDateTime transactionDate;
    private String paymentStatus;
    private String notes;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    private java.math.BigDecimal gain;
    private java.math.BigDecimal gainPercentage;
    private java.math.BigDecimal loss;
    private java.math.BigDecimal lossPercentage;
}
