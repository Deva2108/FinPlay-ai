package com.example.stockPortfolio.PortfolioManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioDTO {
    private Long portfolioId;
    private Long userId;
    private String portfolioName;
    private java.math.BigDecimal balance;
    private java.math.BigDecimal initialBalance;

    public static PortfolioDTO fromEntity(Portfolio portfolio) {
        return PortfolioDTO.builder()
                .portfolioId(portfolio.getPortfolioId())
                .userId(portfolio.getUser() != null ? portfolio.getUser().getUserId() : null)
                .portfolioName(portfolio.getPortfolioName())
                .balance(portfolio.getBalance())
                .initialBalance(portfolio.getInitialBalance())
                .build();
    }
}
