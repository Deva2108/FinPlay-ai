package com.example.stockPortfolio.WatchlistManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WatchlistRepo extends JpaRepository<Watchlist, Long> {
    List<Watchlist> findByUser_UserId(Long userId);
    Optional<Watchlist> findByUser_UserIdAndSymbol(Long userId, String symbol);
    void deleteByUser_UserIdAndSymbol(Long userId, String symbol);
}
