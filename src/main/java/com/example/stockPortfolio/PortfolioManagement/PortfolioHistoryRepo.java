package com.example.stockPortfolio.PortfolioManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PortfolioHistoryRepo extends JpaRepository<PortfolioHistory, Long> {

    /** Full growth series for a portfolio, oldest-first for charting. */
    List<PortfolioHistory> findByPortfolioIdOrderBySnapshotDateAsc(Long portfolioId);

    /** Windowed query: growth over the last N days. */
    List<PortfolioHistory> findByPortfolioIdAndSnapshotDateAfterOrderBySnapshotDateAsc(
            Long portfolioId, LocalDate after);

    /** Duplicate guard — called before each daily snapshot write. */
    boolean existsByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate date);
}
