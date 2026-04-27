package com.example.stockPortfolio.AlertManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDataDTO {
    private Long id;
    private String symbol;
    private java.math.BigDecimal targetPrice;
    private String gainOrLoss;
    private String triggerReason;
    private LocalDateTime lastTriggeredAt;
    private String alertType;

    public static AlertDataDTO fromEntity(Alert alert) {
        return AlertDataDTO.builder()
                .id(alert.getId())
                .symbol(alert.getSymbol())
                .targetPrice(alert.getTargetPrice())
                .gainOrLoss(alert.getGainOrLoss())
                .triggerReason(alert.getTriggerReason())
                .lastTriggeredAt(alert.getLastTriggeredAt())
                .alertType(alert.getAlertType().name())
                .build();
    }
}
