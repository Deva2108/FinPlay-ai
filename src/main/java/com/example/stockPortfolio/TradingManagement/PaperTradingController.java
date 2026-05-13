package com.example.stockPortfolio.TradingManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * In-house paper trading endpoints. Drop-in replacement for the dropped Alpaca
 * integration — same conceptual API shape (account / positions / orders) but
 * fully simulated against the existing Portfolio + Holding domain. No external
 * brokerage signup required.
 *
 * <pre>
 *   GET  /api/trading/paper/account?portfolioId=X
 *   GET  /api/trading/paper/positions?portfolioId=X
 *   GET  /api/trading/paper/orders?portfolioId=X
 *   POST /api/trading/paper/orders
 * </pre>
 */
@RestController
@RequestMapping("/api/trading/paper")
@Tag(name = "9. Paper Trading", description = "In-house simulated trading (no external broker)")
@RequiredArgsConstructor
@Slf4j
public class PaperTradingController {

    private final PaperTradingService paperTradingService;
    private final UserService userService;

    private Long getLoggedInUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getUserByEmail(email);
        return user.getUserId();
    }

    // Errors fall through to GlobalExceptionHandler which maps them to proper
    // 4xx/5xx responses. We intentionally do NOT try/catch here — that would
    // mask failures as HTTP 200 and break frontend error UX, k6 load tests,
    // and Sentry/monitoring later.

    @GetMapping("/account")
    public ResponseEntity<ApiResponse<SimulatedAccountDTO>> getAccount(@RequestParam Long portfolioId) {
        SimulatedAccountDTO account = paperTradingService.getAccount(portfolioId, getLoggedInUserId());
        return ResponseEntity.ok(ApiResponse.ok(account, "Paper account fetched", "live"));
    }

    @GetMapping("/positions")
    public ResponseEntity<ApiResponse<List<SimulatedPositionDTO>>> getPositions(@RequestParam Long portfolioId) {
        List<SimulatedPositionDTO> positions = paperTradingService.getPositions(portfolioId, getLoggedInUserId());
        return ResponseEntity.ok(ApiResponse.ok(positions, "Paper positions fetched", "live"));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<SimulatedOrderDTO>>> getOrders(@RequestParam Long portfolioId) {
        List<SimulatedOrderDTO> orders = paperTradingService.getOrders(portfolioId, getLoggedInUserId());
        return ResponseEntity.ok(ApiResponse.ok(orders, "Paper orders fetched", "live"));
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<SimulatedOrderDTO>> placeOrder(@Valid @RequestBody SimulatedOrderRequestDTO request) {
        SimulatedOrderDTO order = paperTradingService.placeOrder(request, getLoggedInUserId());
        return ResponseEntity.ok(ApiResponse.ok(order, "Paper order filled"));
    }
}
