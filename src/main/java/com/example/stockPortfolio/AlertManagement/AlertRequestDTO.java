package com.example.stockPortfolio.AlertManagement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertRequestDTO {
    @NotBlank(message = "Symbol is required")
    private String symbol;
    
    @NotNull(message = "Target Price is required")
    @Positive(message = "Target Price must be positive")
    private BigDecimal targetPrice;
    
    private String alertType;
}
