package com.example.stockPortfolio.DecisionManagement;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class Decision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long userId;
    private String symbol;
    private String action; // BUY, SELL, HOLD, WAIT, SKIP
    private Double price;
    private LocalDateTime timestamp;
    private String market; // IN, US
}
