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

import java.util.Locale;
import java.util.Set;

/**
 * Async insight endpoints.
 *
 * Flow:
 *   POST /api/insights/request?topic=...   → returns {status: PENDING, topicId}
 *   GET  /api/insights/status?topicId=...  → returns {status: READY|PENDING|ERROR, insight?}
 *
 * Topic whitelist: only the six topics below are honored. Anything else collapses
 * to "global_market" — this caps the Redis cache-key space to a small, bounded
 * set per context-hash, preventing arbitrary client input from exploding the
 * insight namespace and starving the core market cache.
 */
@RestController
@RequestMapping("/api/insights")
@Tag(name = "12. Async Insights", description = "Two-phase async FinPlay insight generation")
@RequiredArgsConstructor
public class InsightController {

    private static final Set<String> ALLOWED_TOPICS = Set.of(
            "global_market",
            "market_open",
            "market_close",
            "portfolio_summary",
            "sector_rotation",
            "volatility_pulse"
    );
    private static final String DEFAULT_TOPIC = "global_market";

    private final InsightAsyncService insightAsyncService;

    private static String normalizeTopic(String topic) {
        if (topic == null) return DEFAULT_TOPIC;
        String t = topic.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return ALLOWED_TOPICS.contains(t) ? t : DEFAULT_TOPIC;
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<InsightStatusDTO>> request(
            @RequestParam String topic,
            @RequestParam(required = false, defaultValue = "") String context) {
        String normalized = normalizeTopic(topic);
        InsightStatusDTO status = insightAsyncService.requestInsight(normalized, context);
        return ResponseEntity.ok(ApiResponse.ok(status, "Insight request accepted"));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<InsightStatusDTO>> status(@RequestParam String topicId) {
        InsightStatusDTO status = insightAsyncService.pollStatus(topicId);
        return ResponseEntity.ok(ApiResponse.ok(status, "Insight status"));
    }
}
