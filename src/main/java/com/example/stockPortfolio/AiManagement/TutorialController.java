package com.example.stockPortfolio.AiManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tutorial")
@RequiredArgsConstructor
public class TutorialController {

    private final com.example.stockPortfolio.MarketManagement.MarketGateway marketGateway;
    private final com.example.stockPortfolio.AiManagement.service.AiService aiService;

    @GetMapping("/insight")
    public ResponseEntity<ApiResponse<TutorialInsightResponseDTO>> getTutorialInsight(
            @RequestParam(defaultValue = "investing") String topic,
            @RequestParam(required = false) String context) {
        
        // READ PRECOMPUTED FROM GATEWAY
        String message = marketGateway.getPrecomputedInsight("tutorial", topic);
        
        if (message == null) {
            String fallbackMessage = aiService.getTutorialInsight(topic, context != null ? context : "general");
            TutorialInsightResponseDTO data = TutorialInsightResponseDTO.builder()
                    .message(fallbackMessage)
                    .build();

            return ResponseEntity.ok(ApiResponse.syncing(data, "Tutorial insight is being prepared", "fallback"));
        }
        
        TutorialInsightResponseDTO data = TutorialInsightResponseDTO.builder().message(message).build();
        return ResponseEntity.ok(ApiResponse.ok(data, "Tutorial insight fetched from cache", "cache"));
    }
}
