package com.example.stockPortfolio.AiManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/explain")
@Slf4j
@lombok.RequiredArgsConstructor
public class ExplainController {

    private final CacheManager cacheManager;
    private final com.example.stockPortfolio.MarketManagement.MarketGateway marketGateway;
    private final com.example.stockPortfolio.AiManagement.service.DeterministicInsightService deterministicInsightService;

    @PostMapping
    public ResponseEntity<ApiResponse<ExplainResponseDTO>> getExplanation(@RequestBody ExplainRequestDTO request) {
        // Direct call to hand-written engine (deterministic)
        ExplainResponseDTO response = deterministicInsightService.getStructuredExplanation(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Smart explanation generated instantly", "engine"));
    }

    private ExplainResponseDTO buildFallback(ExplainRequestDTO request) {
        String symbol = request != null && request.getSymbol() != null ? request.getSymbol() : "the stock";
        String trend = request != null && request.getTrend() != null ? request.getTrend() : "stable";
        String action = request != null && request.getAction() != null ? request.getAction() : "observing";
        String behavior = request != null && request.getBehavior() != null ? request.getBehavior() : "Balanced";

        com.example.stockPortfolio.AiManagement.RichInsightDTO fallback =
                com.example.stockPortfolio.AiManagement.RichInsightDTO.builder()
                        .whatHappened(String.format("The market is moving %s for %s.", trend, symbol))
                        .whyItMatters("Price movements indicate shifts in supply and demand balance.")
                        .whatYouCanLearn("Observe how the stock reacts to key support and resistance levels.")
                        .analogy("Like a tide coming in or going out, market trends show the collective direction of all participants.")
                        .investorPerspective("\"The stock market is a device for transferring money from the impatient to the patient.\" — Warren Buffett")
                        .resources(java.util.Arrays.asList(
                                java.util.Map.of("title", "Understanding Market Trends", "url", "https://www.investopedia.com/terms/t/trend.asp"),
                                java.util.Map.of("title", "The Psychology of Investing", "url", "https://www.youtube.com/watch?v=Xun73T7r4vE")
                        ))
                        .build();

        return ExplainResponseDTO.builder()
                .explanation(fallback.getWhatHappened())
                .observation(fallback.getWhatYouCanLearn())
                .symbol(symbol)
                .richInsight(fallback)
                .build();
    }

    private String buildCacheKey(ExplainRequestDTO request) {
        if (request == null) {
            return "nullnullnullnullnull";
        }
        return String.valueOf(request.getSymbol())
                + String.valueOf(request.getTrend())
                + String.valueOf(request.getAction())
                + String.valueOf(request.getLang())
                + String.valueOf(request.getBehavior());
    }
}
