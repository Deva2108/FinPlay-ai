package com.example.stockPortfolio.DecisionManagement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRequestDTO {
    @NotBlank(message = "Symbol is required")
    private String symbol;
    
    @NotBlank(message = "Action is required")
    private String action; // BUY, SELL, HOLD, WAIT, SKIP
    
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;
    
    private String market; // IN, US
    private LocalDateTime timestamp;
}
