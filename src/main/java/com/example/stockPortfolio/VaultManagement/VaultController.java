package com.example.stockPortfolio.VaultManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Placeholder Vault endpoints. Returns the static "Coming Soon" deck described
 * in the FinPlay exec summary so the frontend can wire up the page now and
 * we'll back it with real content + state in a later pass.
 */
@RestController
@RequestMapping("/api/vault")
@Tag(name = "13. Vault", description = "Gamified retention layer")
@lombok.RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<VaultScenarioDTO>> getDailyScenario() {
        VaultScenarioDTO scenario = vaultService.getDailyScenario();
        
        if (scenario == null) {
            return ResponseEntity.ok(ApiResponse.syncing(null, "Daily challenge is being prepared", "fallback"));
        }
        
        return ResponseEntity.ok(ApiResponse.ok(scenario, "Daily challenge fetched from cache", "cache"));
    }

    @GetMapping("/cards")
    public ResponseEntity<ApiResponse<List<VaultCardDTO>>> getCards() {
        // Static cards representing the feature set, but the "Daily Challenge" is now backed by /daily
        List<VaultCardDTO> cards = List.of(
                VaultCardDTO.builder()
                        .id("daily-challenge")
                        .title("Daily Challenge")
                        .subtitle("1 Decision, 60 seconds")
                        .teaser("Test your market instinct with today's scenario.")
                        .category("CHALLENGE")
                        .locked(false)
                        .ctaLabel("Play Now")
                        .build(),
                VaultCardDTO.builder()
                        .id("market-vibes-101")
                        .title("Market Vibes 101")
                        .subtitle("Beginner")
                        .teaser("Learn to read the market's psychological pulse.")
                        .category("LESSON")
                        .locked(true)
                        .ctaLabel("Coming Soon")
                        .build()
        );
        return ResponseEntity.ok(ApiResponse.ok(cards, "Vault deck loaded", "cache"));
    }
}
