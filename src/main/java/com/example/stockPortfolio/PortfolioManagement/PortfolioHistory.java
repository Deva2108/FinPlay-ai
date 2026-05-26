package com.example.stockPortfolio.PortfolioManagement;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily end-of-session portfolio value snapshot.
 *
 * Written once per portfolio per trading day — at ~5 min after market close
 * by the scheduled snapshot task in MarketDataScheduler. Provides the data
 * series used for portfolio growth charts without needing a separate
 * time-series database or complex event sourcing.
 *
 * Uniqueness: one record per (portfolio_id, snapshot_date). Duplicate writes
 * are guarded by existsByPortfolioIdAndSnapshotDate before saving.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "portfolio_history",
       uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "snapshot_date"}),
       indexes = @Index(columnList = "portfolio_id, snapshot_date"))
public class PortfolioHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    /** Cash balance at snapshot time (uninvested capital). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance;

    /** Market value of all open positions at snapshot time. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal holdingsValue;

    /** Total portfolio value = cashBalance + holdingsValue. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    /** Calendar date of this snapshot (one per trading day). */
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;
}
