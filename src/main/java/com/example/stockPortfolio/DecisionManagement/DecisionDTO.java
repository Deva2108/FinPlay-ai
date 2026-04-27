package com.example.stockPortfolio.DecisionManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionDTO {
    private Long id;
    private String symbol;
    private String action;
    private java.math.BigDecimal price;
    private LocalDateTime timestamp;
    private String market;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DecisionDTO fromEntity(Decision decision) {
        return DecisionDTO.builder()
                .id(decision.getId())
                .symbol(decision.getSymbol())
                .action(decision.getAction())
                .price(decision.getPrice())
                .timestamp(decision.getTimestamp())
                .market(decision.getMarket())
                .createdAt(decision.getCreatedAt())
                .updatedAt(decision.getUpdatedAt())
                .build();
    }
}
