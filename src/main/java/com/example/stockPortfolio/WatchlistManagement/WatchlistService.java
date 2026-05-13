package com.example.stockPortfolio.WatchlistManagement;

import com.example.stockPortfolio.UserManagement.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WatchlistService {
    private final WatchlistRepo watchlistRepo;

    public List<String> getWatchlistSymbols(Long userId) {
        return watchlistRepo.findByUser_UserId(userId).stream()
                .map(Watchlist::getSymbol)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addToWatchlist(User user, String symbol) {
        if (watchlistRepo.findByUser_UserIdAndSymbol(user.getUserId(), symbol).isEmpty()) {
            Watchlist watchlist = Watchlist.builder()
                    .user(user)
                    .symbol(symbol)
                    .build();
            watchlistRepo.save(watchlist);
        }
    }

    @Transactional
    public void removeFromWatchlist(Long userId, String symbol) {
        watchlistRepo.deleteByUser_UserIdAndSymbol(userId, symbol);
    }
    
    public boolean isInWatchlist(Long userId, String symbol) {
        return watchlistRepo.findByUser_UserIdAndSymbol(userId, symbol).isPresent();
    }
}
