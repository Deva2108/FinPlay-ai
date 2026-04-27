package com.example.stockPortfolio.AlertManagement;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AlertHistoryResponseDTO {
    private Long id;
    private String symbol;
    private String message;
    private String alertType;
    private LocalDateTime triggeredAt;

    public static AlertHistoryResponseDTO fromEntity(AlertHistory history) {
        return AlertHistoryResponseDTO.builder()
                .id(history.getId())
                .symbol(history.getSymbol())
                .message(history.getMessage())
                .alertType(history.getAlertType())
                .triggeredAt(history.getTriggeredAt())
                .build();
    }
}
