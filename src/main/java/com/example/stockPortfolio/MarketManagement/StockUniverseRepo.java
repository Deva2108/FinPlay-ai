package com.example.stockPortfolio.MarketManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockUniverseRepo extends JpaRepository<StockUniverse, Long> {
    Optional<StockUniverse> findBySymbol(String symbol);
    List<StockUniverse> findByMarket(String market);
    List<StockUniverse> findByIsIndexTrue();
}
