package com.example.stockPortfolio.MarketManagement;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class StockChartResponseDTO {
    private List<Map<String, Object>> chartData;
}
