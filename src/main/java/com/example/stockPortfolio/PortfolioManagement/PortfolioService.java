package com.example.stockPortfolio.PortfolioManagement;

import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepo portfolioRepo;
    private final com.example.stockPortfolio.HoldingsManagement.HoldingRepo holdingRepo;

    public PortfolioDTO addPortfolio(Portfolio portfolio, User user) {
        portfolio.setUser(user);
        if (portfolio.getBalance() == null) {
            portfolio.setBalance(java.math.BigDecimal.valueOf(100000.0));
            portfolio.setInitialBalance(java.math.BigDecimal.valueOf(100000.0));
        }
        return PortfolioDTO.fromEntity(portfolioRepo.save(portfolio));
    }

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

    @org.springframework.transaction.annotation.Transactional
    public PortfolioDTO updateBalance(Long id, java.math.BigDecimal amount, Long userId) {
        Portfolio portfolio = portfolioRepo.findByPortfolioIdAndUser_UserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Portfolio not found or unauthorized"));
        
        java.math.BigDecimal newBalance = portfolio.getBalance().add(amount);
        if (newBalance.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Insufficient funds in the portfolio.");
        }
        
        portfolio.setBalance(newBalance);
        return PortfolioDTO.fromEntity(portfolioRepo.save(portfolio));
    }

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

    public PortfolioDTO getPortfolioById(Long portfolioId, Long userId) {
        return portfolioRepo.readByPortfolioIdAndUser_UserId(portfolioId, userId)
                .map(PortfolioDTO::fromEntity)
                .orElseThrow(() -> new RuntimeException("Portfolio not found"));
    }

    public void validatePortfolioOwnership(Long portfolioId, Long userId) {
        portfolioRepo.readByPortfolioIdAndUser_UserId(portfolioId, userId)
                .orElseThrow(() -> new RuntimeException("Unauthorized: Portfolio not found or does not belong to user"));
    }
}
