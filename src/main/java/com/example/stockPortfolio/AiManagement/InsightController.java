package com.example.stockPortfolio.AiManagement;

import com.example.stockPortfolio.AiManagement.service.InsightAsyncService;
import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Async insight endpoints.
 *
 * Flow:
 *   POST /api/insights/request?topic=...   → returns {status: PENDING, topicId}
 *   GET  /api/insights/status?topicId=...  → returns {status: READY|PENDING|ERROR, insight?}
 *
 * The frontend polls /status every ~2s until READY.
 */
@RestController
@RequestMapping("/api/insights")
@Tag(name = "12. Async Insights", description = "Two-phase async FinPlay insight generation")
@RequiredArgsConstructor
public class InsightController {

    private final InsightAsyncService insightAsyncService;

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<InsightStatusDTO>> request(
            @RequestParam String topic,
            @RequestParam(required = false, defaultValue = "") String context) {
        InsightStatusDTO status = insightAsyncService.requestInsight(topic, context);
        return ResponseEntity.ok(ApiResponse.ok(status, "Insight request accepted"));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<InsightStatusDTO>> status(@RequestParam String topicId) {
        InsightStatusDTO status = insightAsyncService.pollStatus(topicId);
        return ResponseEntity.ok(ApiResponse.ok(status, "Insight status"));
    }
}
