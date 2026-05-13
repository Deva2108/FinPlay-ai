package com.example.stockPortfolio.MarketManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/charts")
@RequiredArgsConstructor
public class ChartController {
    private final ChartService chartService;

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChart(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1Y") String range) {
        
        Map<String, Object> response = chartService.getChartData(symbol, range);
        return ResponseEntity.ok(ApiResponse.ok(response, "Historical data loaded"));
    }
}
