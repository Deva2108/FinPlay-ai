package com.example.stockPortfolio.PortfolioManagement;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AggregatedSyncDTO {
    private List<PortfolioDTO> portfolios;
    private com.example.stockPortfolio.HoldingsManagement.HoldingResponseDTO holdings;
    private Object behaviorInsights;
    private java.math.BigDecimal totalBalance;
}
