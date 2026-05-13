package com.example.stockPortfolio.HoldingsManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepo extends org.springframework.data.jpa.repository.JpaRepository<Transaction, Long> {
    List<Transaction> findByUserIdAndPortfolioIdOrderByTransactionDateDesc(Long userId, Long portfolioId);
    org.springframework.data.domain.Page<Transaction> findByUserIdAndPortfolioId(Long userId, Long portfolioId, org.springframework.data.domain.Pageable pageable);
    List<Transaction> findByUserId(Long userId);

    /** Count of trades placed since the given timestamp. Used by admin stats. */
    long countByTransactionDateAfter(java.time.LocalDateTime since);
}
