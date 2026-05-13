package com.example.stockPortfolio.HoldingsManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface HoldingRepo extends JpaRepository<Holding, Long>{
    List<Holding> findByUserIdAndPortfolioId(Long userId, Long portfolioId);
    List<Holding> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Holding h WHERE h.userId = :userId AND h.portfolioId = :portfolioId AND UPPER(h.symbol) = UPPER(:symbol)")
    java.util.Optional<Holding> findByUserIdAndPortfolioIdAndSymbolWithLock(Long userId, Long portfolioId, String symbol);

    @Query("SELECT DISTINCT h.symbol FROM Holding h")
    List<String> findAllDistinctSymbols();
}
