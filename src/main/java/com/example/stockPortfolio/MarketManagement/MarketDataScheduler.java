package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MarketDataScheduler {

    private final FinnhubService finnhubService;
    private final NewsApiService newsApiService;
    private final MarketAnalysisService marketAnalysisService;

    /**
     * Runs every 30 minutes to pre-warm the cache for common market data.
     * This ensures users get fast responses for trending stocks and major news.
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void refreshMarketDataCache() {
        log.info("Starting periodic market data refresh...");
        
        try {
            // 1. Refresh major indices quotes
            List<String> indices = List.of("SPY", "QQQ", "DIA", "^NSEI", "^BSESN", "^NSEBANK");
            indices.stream().forEach(symbol -> {
                try {
                    finnhubService.getStockQuote(symbol);
                } catch (Exception e) {
                    log.warn("Failed to refresh quote for {}: {}", symbol, e.getMessage());
                }
            });

            // 2. Refresh trending stocks (MarketAnalysisService already has its own logic, 
            // but calling getTrending() will trigger it and cache the results in the service map)
            marketAnalysisService.getTrending();

            // 3. Refresh global market news
            newsApiService.getStockNews("stock market");
            
            log.info("Market data refresh completed successfully.");
        } catch (Exception e) {
            log.error("Error during scheduled market data refresh: {}", e.getMessage());
        }
    }
}
