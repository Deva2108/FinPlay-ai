package com.example.stockPortfolio.AiManagement;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class MarketScenarioResponseDTO {
    private List<Map<String, Object>> scenarios;
}
