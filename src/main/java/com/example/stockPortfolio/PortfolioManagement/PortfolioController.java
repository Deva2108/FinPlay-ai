package com.example.stockPortfolio.PortfolioManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolios")
@Tag(name="2. Portfolio", description = "Portfolio Management Controller")
@Slf4j
@lombok.RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final UserService userService;
    private final com.example.stockPortfolio.MarketManagement.MarketGateway marketGateway;
    private final com.example.stockPortfolio.AiManagement.service.AiService aiService;
    private final com.example.stockPortfolio.HoldingsManagement.HoldingService holdingService;
    private final PortfolioHistoryService portfolioHistoryService;

    private User getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.getUserByEmail(email);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortfolioDTO>> addPortfolio(@Valid @RequestBody PortfolioDTO request) {
        User user = getLoggedInUser();
        log.info("Creating portfolio {} for user {}", request.getPortfolioName(), user.getUserId());
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioName(request.getPortfolioName());
        PortfolioDTO saved = portfolioService.addPortfolio(portfolio, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(saved, "Portfolio created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PortfolioDTO>>> getPortfolios() {
        PortfolioResponseDTO response = portfolioService.getPortfoliosByUserId(getLoggedInUser().getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response.getResult(), "Portfolios fetched successfully"));
    }

    /**
     * Aggregated bootstrap call.
     *
     * Pass {@code light=true} for the critical-path hydration only (portfolios +
     * holdings + balance). Behavior insights are skipped — the frontend should
     * fire {@code /api/decision/insights} as a deferred second call after first
     * paint. This shaves a Redis read off the blocking path and lets the UI
     * render meaningful state ~50–200ms sooner on cold cache.
     *
     * Default ({@code light=false}) preserves the old behavior for callers that
     * have not been migrated.
     */
    @GetMapping("/sync")
    public ResponseEntity<ApiResponse<AggregatedSyncDTO>> syncAll(
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "false") boolean light) {
        User user = getLoggedInUser();
        Long userId = user.getUserId();

        List<PortfolioDTO> portfolios = portfolioService.getPortfoliosByUserId(userId).getResult();

        com.example.stockPortfolio.HoldingsManagement.HoldingResponseDTO holdings = null;
        java.math.BigDecimal balance = java.math.BigDecimal.ZERO;
        if (!portfolios.isEmpty()) {
            PortfolioDTO primary = portfolios.get(0);
            holdings = holdingService.getHoldingsWithDetails(userId, primary.getPortfolioId());
            balance = primary.getBalance();
        }

        Object insights = light ? null : marketGateway.getUserInsight(userId, "behavior");

        AggregatedSyncDTO sync = AggregatedSyncDTO.builder()
                .portfolios(portfolios)
                .holdings(holdings)
                .behaviorInsights(insights)
                .totalBalance(balance)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(sync, "Application state synchronized"));
    }

    /**
     * Admin-only manual balance adjustment. Regular users CANNOT call this — they
     * would be able to print free money and trivially top the leaderboard.
     * The previous open `/{id}/balance` endpoint was a critical money-printing bug.
     */
    public static class BalanceUpdateDTO {
        @jakarta.validation.constraints.NotNull
        public java.math.BigDecimal amount;
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
    }

    @PostMapping("/{id}/balance")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PortfolioDTO>> adminUpdateBalance(@PathVariable Long id,
                                                                        @Valid @RequestBody BalanceUpdateDTO payload) {
        java.math.BigDecimal amount = payload.getAmount();
        log.warn("ADMIN balance adjustment: portfolio={} amount={} actor={}", id, amount, getLoggedInUser().getUserId());
        PortfolioDTO updated = portfolioService.updateBalance(id, amount, getLoggedInUser().getUserId());
        return ResponseEntity.ok(ApiResponse.ok(updated, "Balance updated successfully"));
    }

    // NOTE: the user-facing self-reset endpoint was removed per launch decision.
    // Resets now go through `/api/admin/users/{id}/reset` (ROLE_ADMIN only).
    // Users who get stuck must DM an admin who triggers the reset on their behalf.
    // The PortfolioService.resetPortfolio method itself stays — it's invoked by AdminService.

    /**
     * Daily portfolio value series for the growth chart.
     *
     * Returns snapshots taken at each market close, oldest-first. One data point
     * per trading day — enough for a clean area/line chart showing portfolio
     * evolution over 30/90/180/365 days without any synthetic data fabrication.
     *
     * Ownership is verified by attempting getPortfolioById; 403 is thrown by the
     * PortfolioAccessDeniedException handler if the portfolio belongs to another user.
     *
     * Query param {@code days} defaults to 90 (3 months).
     */
    @GetMapping("/{id}/growth")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPortfolioGrowth(
            @PathVariable Long id,
            @RequestParam(defaultValue = "90") int days) {
        User user = getLoggedInUser();
        // Ownership check — throws PortfolioAccessDeniedException if not owned by user
        portfolioService.getPortfolioById(id, user.getUserId());

        List<PortfolioHistory> history = portfolioHistoryService.getGrowthHistory(id, Math.min(days, 365));

        List<Map<String, Object>> series = history.stream().map(h -> {
            Map<String, Object> point = new java.util.LinkedHashMap<>();
            point.put("date", h.getSnapshotDate().toString());
            point.put("value", h.getTotalValue());
            point.put("cash", h.getCashBalance());
            point.put("holdings", h.getHoldingsValue());
            return point;
        }).toList();

        String msg = series.isEmpty()
                ? "No snapshots yet — portfolio growth will appear after the first market close."
                : series.size() + " daily snapshots";
        return ResponseEntity.ok(ApiResponse.ok(series, msg));
    }

    @GetMapping("/{id}/mentor")

    public ResponseEntity<ApiResponse<MentorAdviceResponseDTO>> getMentorAdvice(@PathVariable Long id) {
        User user = getLoggedInUser();
        
        // READ PRECOMPUTED FROM GATEWAY
        String advice = (String) marketGateway.getUserInsight(user.getUserId(), "mentor:" + id);
        
        if (advice == null) {
            MentorAdviceResponseDTO response = MentorAdviceResponseDTO.builder()
                    .advice("Your portfolio mentor is reviewing your strategy. Check back shortly for deep insights.")
                    .build();
            return ResponseEntity.ok(ApiResponse.syncing(response, "Insight is being generated...", "fallback"));
        }
        
        MentorAdviceResponseDTO response = MentorAdviceResponseDTO.builder().advice(advice).build();
        return ResponseEntity.ok(ApiResponse.ok(response, "AI Mentor advice fetched from cache", "cache"));
    }
}
