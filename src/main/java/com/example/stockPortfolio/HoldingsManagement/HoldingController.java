package com.example.stockPortfolio.HoldingsManagement;

import com.example.stockPortfolio.UserManagement.UserModel;
import com.example.stockPortfolio.UserManagement.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/holdings")
@Tag(name="3. Holding", description = "3rd Controller, User can add Holdings")
@RequiredArgsConstructor
@Slf4j
public class HoldingController {

    private final HoldingService holdingService;
    private final UserService userService;

    private Long getLoggedInUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserModel user = userService.getUserByEmail(email);
        return user.getUserId();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Transaction>> recordTransaction(@Valid @RequestBody TransactionDTO dto) {
        Long userId = getLoggedInUserId();
        log.info("Processing {} transaction for user {} symbol {}", dto.getType(), userId, dto.getSymbol());
        
        Transaction txn = new Transaction();
        txn.setUserId(userId);
        txn.setPortfolioId(dto.getPortfolioId());
        txn.setSymbol(dto.getSymbol().toUpperCase());
        txn.setQuantity(dto.getQuantity());
        txn.setPrice(dto.getPrice());
        txn.setTransactionDate(LocalDateTime.now());
        txn.setType(Transaction.TransactionType.valueOf(dto.getType().toUpperCase()));

        holdingService.processTransaction(txn);
        return ResponseEntity.ok(new ApiResponse<>(txn, 200, "Transaction recorded successfully"));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactions(@RequestParam Long portfolioId) {
        List<TransactionDTO> transactions = holdingService.getAllTransactions(getLoggedInUserId(), portfolioId);
        return ResponseEntity.ok(new ApiResponse<>(transactions, 200, "Transactions fetched successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HoldingResponseDTO>> getHoldings(@RequestParam Long portfolioId) {
        HoldingResponseDTO response = holdingService.getHoldingsWithDetails(getLoggedInUserId(), portfolioId);
        return ResponseEntity.ok(new ApiResponse<>(response, 200, "Holdings fetched successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteHolding(@PathVariable Long id) {
        holdingService.deleteHolding(id);
        return ResponseEntity.ok(new ApiResponse<>("Holding deleted successfully", 200, "Success"));
    }
}
