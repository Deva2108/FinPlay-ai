package com.example.stockPortfolio.MarketManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockHistoryRepo extends JpaRepository<StockHistory, Long> {
    List<StockHistory> findBySymbolAndDateAfterOrderByDateAsc(String symbol, LocalDate date);
    boolean existsBySymbol(String symbol);
    boolean existsBySymbolAndDate(String symbol, LocalDate date);
    
    @Query("SELECT MAX(h.date) FROM StockHistory h WHERE h.symbol = :symbol")
    LocalDate findLatestDateBySymbol(@Param("symbol") String symbol);
}
