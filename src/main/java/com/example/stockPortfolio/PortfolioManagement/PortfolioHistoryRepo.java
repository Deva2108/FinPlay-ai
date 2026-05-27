package com.example.stockPortfolio.PortfolioManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioHistoryRepo extends JpaRepository<PortfolioHistory, Long> {

    /** Full growth series for a portfolio, oldest-first for charting. */
    List<PortfolioHistory> findByPortfolioIdOrderBySnapshotDateAsc(Long portfolioId);

    /** Windowed query: growth over the last N days. */
    List<PortfolioHistory> findByPortfolioIdAndSnapshotDateAfterOrderBySnapshotDateAsc(
            Long portfolioId, LocalDate after);

    /** Upsert lookup — fetch the existing row for a given portfolio + date if present. */
    Optional<PortfolioHistory> findByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate date);

    /** Kept for backward-compatibility; prefer findByPortfolioIdAndSnapshotDate for upsert logic. */
    boolean existsByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate date);
}
