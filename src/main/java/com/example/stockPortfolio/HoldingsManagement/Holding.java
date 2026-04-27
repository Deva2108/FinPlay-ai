package com.example.stockPortfolio.HoldingsManagement;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"userId", "portfolioId", "symbol"})})
public class Holding{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long holdingId;
    @NotNull(message = "User Id is Required" )
    private Long userId;
    @NotNull(message = "Portfolio Id is Required" )
    private Long portfolioId;
    @NotBlank(message = "Symbol is Required")
    private String symbol;
    @NotNull(message = "Quantity is Required" )
    private int quantity;
    @DecimalMin(value = "0.01",inclusive = true,message = "Value must be greater than or equal to 0.01")
    private BigDecimal buyPrice;

    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }

    public Holding(long l, long l1, long l2, String aapl, int i, double v) {
    }
}
