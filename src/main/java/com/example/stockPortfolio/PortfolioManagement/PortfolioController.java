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
    private final com.example.stockPortfolio.AiManagement.service.GeminiService geminiService;

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

    public static class BalanceUpdateDTO {
        @jakarta.validation.constraints.NotNull
        public java.math.BigDecimal amount;
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
    }

    @PostMapping("/{id}/balance")
    public ResponseEntity<ApiResponse<PortfolioDTO>> updateBalance(@PathVariable Long id, @Valid @RequestBody BalanceUpdateDTO payload) {
        java.math.BigDecimal amount = payload.getAmount();
        PortfolioDTO updated = portfolioService.updateBalance(id, amount, getLoggedInUser().getUserId());
        return ResponseEntity.ok(ApiResponse.ok(updated, "Balance updated successfully"));
    }

    @GetMapping("/{id}/mentor")
    public ResponseEntity<ApiResponse<MentorAdviceResponseDTO>> getMentorAdvice(@PathVariable Long id) {
        User user = getLoggedInUser();
        List<Map<String, Object>> holdings = portfolioService.getHoldingsByPortfolioId(id, user.getUserId());
        PortfolioDTO portfolio = portfolioService.getPortfolioById(id, user.getUserId());
        
        String advice = geminiService.getPortfolioMentorAdvice(holdings, portfolio.getBalance() != null ? portfolio.getBalance().doubleValue() : 0.0);
        
        MentorAdviceResponseDTO response = MentorAdviceResponseDTO.builder().advice(advice).build();
        return ResponseEntity.ok(ApiResponse.ok(response, "AI Mentor advice generated"));
    }
}
