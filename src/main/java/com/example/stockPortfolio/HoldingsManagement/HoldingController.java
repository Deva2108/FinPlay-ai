package com.example.stockPortfolio.HoldingsManagement;

import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserService;
import com.example.stockPortfolio.PortfolioManagement.PortfolioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holdings")
@Tag(name="3. Holding", description = "Holding Management Controller")
@RequiredArgsConstructor
@Slf4j
public class HoldingController {

    private final HoldingService holdingService;
    private final UserService userService;
    private final PortfolioService portfolioService;
    private final com.example.stockPortfolio.MarketManagement.MarketGateway marketGateway;
    private final com.example.stockPortfolio.MarketManagement.SymbolNormalizer symbolNormalizer;

    private Long getLoggedInUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getUserByEmail(email);
        return user.getUserId();
    }

    private final com.example.stockPortfolio.MarketManagement.ForexService forexService;

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionDTO>> recordTransaction(@Valid @RequestBody TransactionDTO dto) {
        Long userId = getLoggedInUserId();
        
        // Security Check: Ensure portfolio belongs to user
        portfolioService.validatePortfolioOwnership(dto.getPortfolioId(), userId);
        
        log.info("Processing {} transaction for user {} symbol {}", dto.getType(), userId, dto.getSymbol());
        
        String normalized = symbolNormalizer.normalize(dto.getSymbol());
        if (normalized == null) {
            normalized = dto.getSymbol().toUpperCase();
        }

        // FETCH LIVE PRICE - DO NOT TRUST CLIENT
        ApiResponse<Map<String, Object>> quoteResp = marketGateway.getLatestQuote(normalized);
        java.math.BigDecimal rawPrice;
        if (quoteResp.getData() != null && quoteResp.getData().containsKey("price")) {
            rawPrice = new java.math.BigDecimal(quoteResp.getData().get("price").toString());
        } else {
            throw new IllegalStateException("Market price currently unavailable for " + normalized + ". Please try again.");
        }

        // CONVERSION LOGIC: Convert USD to INR if necessary
        java.math.BigDecimal fillPriceInInr = rawPrice;
        if (!symbolNormalizer.isIndian(normalized)) {
            fillPriceInInr = forexService.convertUsdToInr(rawPrice);
            log.debug("Converted US stock price {} USD to {} INR", rawPrice, fillPriceInInr);
        }

        Transaction txn = new Transaction();
        txn.setUserId(userId);
        txn.setPortfolioId(dto.getPortfolioId());
        txn.setSymbol(normalized);
        txn.setQuantity(dto.getQuantity());
        txn.setPrice(fillPriceInInr.setScale(2, java.math.RoundingMode.HALF_UP));
        txn.setTransactionDate(LocalDateTime.now());
        txn.setType(Transaction.TransactionType.valueOf(dto.getType().toUpperCase()));
        txn.setNotes(dto.getNotes());
        if (dto.getPaymentStatus() != null) {
            txn.setPaymentStatus(Transaction.PaymentStatus.valueOf(dto.getPaymentStatus().toUpperCase()));
        }

        holdingService.processTransaction(txn);

        TransactionDTO responseDto = new TransactionDTO();
        responseDto.setTransactionId(txn.getTransactionId());
        responseDto.setUserId(txn.getUserId());
        responseDto.setPortfolioId(txn.getPortfolioId());
        responseDto.setSymbol(txn.getSymbol());
        responseDto.setQuantity(txn.getQuantity());
        responseDto.setPrice(txn.getPrice());
        responseDto.setCurrency("INR");
        responseDto.setTransactionDate(txn.getTransactionDate());
        responseDto.setType(txn.getType().name());
        responseDto.setNotes(txn.getNotes());
        responseDto.setPaymentStatus(txn.getPaymentStatus() != null ? txn.getPaymentStatus().name() : null);

        return ResponseEntity.ok(ApiResponse.ok(responseDto, "Transaction recorded successfully"));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<TransactionDTO>>> getTransactions(
            @RequestParam Long portfolioId,
            @org.springframework.data.web.PageableDefault(size = 50) org.springframework.data.domain.Pageable pageable) {
        Long userId = getLoggedInUserId();
        portfolioService.validatePortfolioOwnership(portfolioId, userId);
        
        org.springframework.data.domain.Page<TransactionDTO> transactions = holdingService.getPagedTransactions(userId, portfolioId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(transactions, "Transactions fetched successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HoldingResponseDTO>> getHoldings(@RequestParam Long portfolioId) {
        Long userId = getLoggedInUserId();
        portfolioService.validatePortfolioOwnership(portfolioId, userId);
        
        HoldingResponseDTO response = holdingService.getHoldingsWithDetails(userId, portfolioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Holdings fetched successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteHolding(@PathVariable Long id) {
        holdingService.deleteHolding(id, getLoggedInUserId());
        return ResponseEntity.ok(ApiResponse.ok("Holding deleted successfully", "Success"));
    }
}
