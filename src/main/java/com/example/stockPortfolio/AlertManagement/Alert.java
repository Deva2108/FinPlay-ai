package com.example.stockPortfolio.AlertManagement;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long userId;
    
    @NotBlank(message = "Symbol should not be blank!")
    private String symbol;
    
    private java.math.BigDecimal targetPrice; // Changed from String to Double for consistency
    private String gainOrLoss;
    private String triggerReason; 
    
    private LocalDateTime lastTriggeredAt;

    @Enumerated(EnumType.STRING)
    private AlertType alertType = AlertType.USER;

    public enum AlertType {
        USER, AUTO_PRICE_SWING, AUTO_52W_LIMIT, AUTO_PORTFOLIO_SWING
    }
}
