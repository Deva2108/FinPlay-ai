package com.example.stockPortfolio.PortfolioManagement;

import com.example.stockPortfolio.ExceptionManagement.PortfolioAccessDeniedException;
import com.example.stockPortfolio.ExceptionManagement.ResourceNotFoundException;
import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepo portfolioRepo;
    private final com.example.stockPortfolio.HoldingsManagement.HoldingRepo holdingRepo;
    private final com.example.stockPortfolio.HoldingsManagement.TransactionRepo transactionRepo;

    public PortfolioDTO addPortfolio(Portfolio portfolio, User user) {
        portfolio.setUser(user);
        if (portfolio.getBalance() == null) {
            portfolio.setBalance(java.math.BigDecimal.valueOf(100000.0));
            portfolio.setInitialBalance(java.math.BigDecimal.valueOf(100000.0));
        }
        return PortfolioDTO.fromEntity(portfolioRepo.save(portfolio));
    }

    @Transactional(readOnly = true)
    public PortfolioResponseDTO getPortfoliosByUserId(Long userId) {
        List<PortfolioDTO> portfolios = portfolioRepo.findByUser_UserId(userId).stream()
                .map(PortfolioDTO::fromEntity)
                .collect(Collectors.toList());
        return PortfolioResponseDTO.builder()
                .result(portfolios)
                .status(200)
                .message("Success")
                .build();
    }

    @Transactional
    public PortfolioDTO updateBalance(Long id, java.math.BigDecimal amount, Long userId) {
        // Atomic update in DB: avoids race conditions, negative balance, and verifies ownership in one shot.
        int rows = portfolioRepo.updateBalanceAtomic(id, amount, userId);

        if (rows == 0) {
            // Either portfolio doesn't exist, doesn't belong to user, or would result in negative balance.
            Portfolio p = portfolioRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + id));
            if (!p.getUser().getUserId().equals(userId)) {
                throw new PortfolioAccessDeniedException("Unauthorized balance modification.");
            }
            if (p.getBalance().add(amount).compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Insufficient funds in the portfolio.");
            }
            // Should theoretically not reach here given the conditions above
            throw new RuntimeException("Update failed for unknown reason.");
        }

        return getPortfolioById(id, userId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHoldingsByPortfolioId(Long portfolioId, Long userId) {
        return holdingRepo.findByUserIdAndPortfolioId(userId, portfolioId).stream()
                .map(h -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("symbol", h.getSymbol());
                    map.put("quantity", h.getQuantity());
                    map.put("buyPrice", h.getBuyPrice());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PortfolioDTO getPortfolioById(Long portfolioId, Long userId) {
        Portfolio portfolio = portfolioRepo.readByPortfolioIdAndUser_UserId(portfolioId, userId)
                .orElseGet(() -> {
                    if (!portfolioRepo.existsById(portfolioId)) {
                        throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
                    }
                    throw new PortfolioAccessDeniedException("Portfolio does not belong to user");
                });
        return PortfolioDTO.fromEntity(portfolio);
    }

    @Transactional(readOnly = true)
    public void validatePortfolioOwnership(Long portfolioId, Long userId) {
        portfolioRepo.readByPortfolioIdAndUser_UserId(portfolioId, userId)
                .orElseGet(() -> {
                    if (!portfolioRepo.existsById(portfolioId)) {
                        throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
                    }
                    throw new PortfolioAccessDeniedException("Portfolio does not belong to user");
                });
    }

    /**
     * Reset a portfolio: restore cash to {@code initialBalance} and remove every
     * holding row. Used by the "Start over" UI button. Idempotent, ownership-checked.
     * Cannot exceed the starting balance, so cannot be abused as a money tap.
     */
    @Transactional
    public PortfolioDTO resetPortfolio(Long portfolioId, Long userId) {
        Portfolio portfolio = portfolioRepo.findByPortfolioIdAndUser_UserId(portfolioId, userId)
                .orElseGet(() -> {
                    if (!portfolioRepo.existsById(portfolioId)) {
                        throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
                    }
                    throw new PortfolioAccessDeniedException("Portfolio does not belong to user");
                });

        java.math.BigDecimal seed = portfolio.getInitialBalance();
        if (seed == null) {
            seed = java.math.BigDecimal.valueOf(100000.0);
            portfolio.setInitialBalance(seed);
        }
        portfolio.setBalance(seed);
        portfolioRepo.save(portfolio);

        // 1. Remove all holdings under this portfolio for this user.
        List<com.example.stockPortfolio.HoldingsManagement.Holding> rows =
                holdingRepo.findByUserIdAndPortfolioId(userId, portfolioId);
        if (!rows.isEmpty()) {
            holdingRepo.deleteAll(rows);
        }

        // 2. Remove all transactions to maintain ledger integrity
        List<com.example.stockPortfolio.HoldingsManagement.Transaction> txns = 
                transactionRepo.findByUserIdAndPortfolioIdOrderByTransactionDateDesc(userId, portfolioId);
        if (!txns.isEmpty()) {
            transactionRepo.deleteAll(txns);
        }

        return PortfolioDTO.fromEntity(portfolio);
    }
}
