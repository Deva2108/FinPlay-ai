package com.example.stockPortfolio.MarketManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockUniverseRepo extends JpaRepository<StockUniverse, Long> {
    Optional<StockUniverse> findBySymbol(String symbol);
    List<StockUniverse> findByMarket(String market);
    List<StockUniverse> findByIsIndexTrue();

    /**
     * Bulk lookup for N+1 elimination. Issues a single
     * {@code SELECT ... FROM stock_universe WHERE symbol IN (:symbols)} query
     * instead of one {@link #findBySymbol(String)} roundtrip per symbol.
     *
     * <p>Used by {@code HoldingService.getHoldingsWithDetails} and
     * {@code getUserExposure} (via {@code MarketAnalysisService#getUniverseBySymbols})
     * to fetch sector/market metadata for an entire portfolio in one query.
     *
     * <p>Symbols not present in the table are simply absent from the result list —
     * callers should handle missing entries with a sensible fallback.
     */
    @Query("SELECT s FROM StockUniverse s WHERE s.symbol IN :symbols")
    List<StockUniverse> findAllBySymbolIn(@Param("symbols") Collection<String> symbols);
}
