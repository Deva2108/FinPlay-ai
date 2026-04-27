package com.example.stockPortfolio.PortfolioManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepo extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUser_UserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Portfolio> findByPortfolioIdAndUser_UserId(Long portfolioId, Long userId);
    
    Optional<Portfolio> readByPortfolioIdAndUser_UserId(Long portfolioId, Long userId);
}
