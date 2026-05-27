package com.example.stockPortfolio.PortfolioManagement;

import com.example.stockPortfolio.HoldingsManagement.HoldingResponseDTO;
import com.example.stockPortfolio.HoldingsManagement.HoldingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Manages daily portfolio value snapshots for growth charting.
 *
 * Lightweight design: one DB row per portfolio per day, no event sourcing,
 * no separate time-series store. snapshotAllPortfolios() is called by the
 * scheduler near market close; the growth history is served directly from
 * PostgreSQL (small table, indexed on portfolio_id + date).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioHistoryService {

    private final PortfolioHistoryRepo historyRepo;
    private final PortfolioRepo portfolioRepo;
    private final HoldingService holdingService;

    /**
     * Snapshots every portfolio's current value. Safe to call multiple times per
     * day — duplicate writes are guarded by the unique constraint check. Runs
     * inside a transaction so lazy-loaded User associations work without a
     * separate entityManager.getReference() call.
     */
    @Transactional
    public void snapshotAllPortfolios() {
        LocalDate today = LocalDate.now();
        List<Portfolio> portfolios;
        try {
            portfolios = portfolioRepo.findAll();
        } catch (Exception e) {
            log.error("Could not load portfolios for snapshot: {}", e.getMessage());
            return;
        }

        // UPSERT semantics: if a snapshot already exists for today (e.g. India close
        // already fired), update it so the last market close of the day wins.
        // Previously the exists-check caused the second close (US) to be silently
        // skipped — US market movement was permanently lost from the growth chart.
        int saved = 0;
        int updated = 0;
        for (Portfolio portfolio : portfolios) {
            Long pid = portfolio.getPortfolioId();
            try {
                Long userId = portfolio.getUser().getUserId(); // safe inside @Transactional
                HoldingResponseDTO holdingsResult = holdingService.getHoldingsWithDetails(userId, pid);
                BigDecimal holdingsValue = holdingsResult.getTotalValue() != null
                        ? holdingsResult.getTotalValue() : BigDecimal.ZERO;
                BigDecimal cash = portfolio.getBalance() != null ? portfolio.getBalance() : BigDecimal.ZERO;

                Optional<PortfolioHistory> existing = historyRepo.findByPortfolioIdAndSnapshotDate(pid, today);
                if (existing.isPresent()) {
                    // Update in place — preserves the row id, cash balance, and date;
                    // only refreshes the market-price-dependent fields.
                    PortfolioHistory h = existing.get();
                    h.setHoldingsValue(holdingsValue);
                    h.setTotalValue(cash.add(holdingsValue));
                    historyRepo.save(h);
                    updated++;
                } else {
                    historyRepo.save(PortfolioHistory.builder()
                            .portfolioId(pid)
                            .cashBalance(cash)
                            .holdingsValue(holdingsValue)
                            .totalValue(cash.add(holdingsValue))
                            .snapshotDate(today)
                            .build());
                    saved++;
                }
            } catch (Exception e) {
                log.warn("Snapshot skipped for portfolio {}: {}", pid, e.getMessage());
            }
        }
        if (saved + updated > 0)
            log.info("Daily portfolio snapshot: {} new, {} updated.", saved, updated);
    }

    /**
     * Returns the growth series for a single portfolio over the last {@code days}
     * calendar days, sorted oldest-first (ready for the chart component).
     */
    public List<PortfolioHistory> getGrowthHistory(Long portfolioId, int days) {
        LocalDate cutoff = LocalDate.now().minusDays(days);
        return historyRepo.findByPortfolioIdAndSnapshotDateAfterOrderBySnapshotDateAsc(portfolioId, cutoff);
    }
}
