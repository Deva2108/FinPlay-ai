package com.example.stockPortfolio.PortfolioManagement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioRequestDTO {
    @NotBlank(message = "PortfolioName is Required!")
    @Size(max = 60, message = "Maximum 60 letters!")
    private String portfolioName;

    private BigDecimal initialBalance;
}
