package com.example.stockPortfolio.HoldingsManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingResponseDTO {
    private List<HoldingStatusDTO> holdings;
    private BigDecimal totalValue;
    private int status;
    private String message;
}
