package com.example.stockPortfolio.PortfolioManagement;

import com.example.stockPortfolio.UserManagement.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Entity
@Data
public class Portfolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long portfolioId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotBlank(message = "PortfolioName is Required!")
    @Size(max = 60, message = "Maximum 60 letters!")
    private String portfolioName;

    private java.math.BigDecimal balance;
    private java.math.BigDecimal initialBalance;
}
