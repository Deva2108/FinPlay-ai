package com.example.stockPortfolio.MarketManagement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stock_universe")
public class StockUniverse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String sector;

    @Column(nullable = false)
    private String market; // US, INDIA

    @Column(nullable = false, length = 3)
    private String currency; // USD, INR

    @Column(nullable = false)
    private String marketCap; // Mega Cap, Large Cap, Mid Cap, Small Cap

    private boolean isIndex;
}
