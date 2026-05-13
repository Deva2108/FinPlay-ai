package com.example.stockPortfolio.WatchlistManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {
    private final WatchlistService watchlistService;
    private final UserService userService;

    private User getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.getUserByEmail(email);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> getWatchlist() {
        User user = getLoggedInUser();
        List<String> symbols = watchlistService.getWatchlistSymbols(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(symbols, "Watchlist fetched successfully"));
    }

    @PostMapping("/{symbol}")
    public ResponseEntity<ApiResponse<Void>> addToWatchlist(@PathVariable String symbol) {
        User user = getLoggedInUser();
        watchlistService.addToWatchlist(user, symbol);
        return ResponseEntity.ok(ApiResponse.ok(null, "Added to watchlist"));
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<ApiResponse<Void>> removeFromWatchlist(@PathVariable String symbol) {
        User user = getLoggedInUser();
        watchlistService.removeFromWatchlist(user.getUserId(), symbol);
        return ResponseEntity.ok(ApiResponse.ok(null, "Removed from watchlist"));
    }

    @GetMapping("/check/{symbol}")
    public ResponseEntity<ApiResponse<Boolean>> checkWatchlist(@PathVariable String symbol) {
        User user = getLoggedInUser();
        boolean exists = watchlistService.isInWatchlist(user.getUserId(), symbol);
        return ResponseEntity.ok(ApiResponse.ok(exists, "Check completed"));
    }
}
