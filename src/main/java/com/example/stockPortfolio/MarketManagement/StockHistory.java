package com.example.stockPortfolio.MarketManagement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stock_history", 
       uniqueConstraints = {@UniqueConstraint(columnNames = {"symbol", "date", "market"})},
       indexes = {@Index(columnList = "symbol, date, market")})
public class StockHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String market; // "INDIA" or "US"

    @Column(nullable = false)
    private LocalDate date;

    @Column(precision = 19, scale = 4)
    private BigDecimal open;
    @Column(precision = 19, scale = 4)
    private BigDecimal high;
    @Column(precision = 19, scale = 4)
    private BigDecimal low;
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal close;

    private boolean isSimulated;
}
