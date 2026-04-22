package com.example.stockPortfolio.AlertManagement;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class AlertHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String symbol;
    private String message;
    private String alertType;
    private LocalDateTime triggeredAt;
}
