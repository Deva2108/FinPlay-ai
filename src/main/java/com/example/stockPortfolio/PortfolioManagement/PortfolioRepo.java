package com.example.stockPortfolio.PortfolioManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
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

    @Query(value = "SELECT * FROM portfolio ORDER BY portfolio_id DESC LIMIT 10", nativeQuery = true)
    List<Portfolio> findTop10Recent();

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "UPDATE portfolio SET balance = balance + :amount WHERE portfolio_id = :id AND user_id = :userId AND (balance + :amount) >= 0", nativeQuery = true)
    int updateBalanceAtomic(@org.springframework.data.repository.query.Param("id") Long id, 
                            @org.springframework.data.repository.query.Param("amount") java.math.BigDecimal amount,
                            @org.springframework.data.repository.query.Param("userId") Long userId);
}
