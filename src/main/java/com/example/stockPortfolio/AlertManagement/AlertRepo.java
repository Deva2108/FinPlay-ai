package com.example.stockPortfolio.AlertManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface AlertRepo extends JpaRepository<Alert, Long> {
    List<Alert> findByUserId(Long userId);
    List<Alert> findBySymbol(String symbol);

    @Query("SELECT DISTINCT a.symbol FROM Alert a")
    List<String> findAllDistinctSymbols();

    @Query("SELECT a FROM Alert a WHERE a.symbol = :symbol " +
           "AND (a.lastTriggeredAt IS NULL OR a.lastTriggeredAt < :cooldownCutoff)")
    List<Alert> findActiveBySymbol(@Param("symbol") String symbol,
                                   @Param("cooldownCutoff") LocalDateTime cooldownCutoff);
}