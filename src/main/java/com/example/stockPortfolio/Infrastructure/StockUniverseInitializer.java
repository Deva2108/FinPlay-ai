package com.example.stockPortfolio.Infrastructure;

import com.example.stockPortfolio.MarketManagement.StockUniverse;
import com.example.stockPortfolio.MarketManagement.StockUniverseRepo;
import com.example.stockPortfolio.MarketManagement.SymbolNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockUniverseInitializer {

    private final StockUniverseRepo stockUniverseRepo;
    private final SymbolNormalizer symbolNormalizer;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        CompletableFuture.runAsync(() -> {
            log.info("[warmup] Async stock universe seeding started...");

            List<StockUniverse> stocks = List.of(
                // US Stocks
                StockUniverse.builder().symbol("AAPL").name("Apple Inc.").sector("Technology").market("US").currency("USD").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("MSFT").name("Microsoft Corporation").sector("Technology").market("US").currency("USD").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("GOOGL").name("Alphabet Inc.").sector("Technology").market("US").currency("USD").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("AMZN").name("Amazon.com Inc.").sector("Consumer Discretionary").market("US").currency("USD").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("META").name("Meta Platforms Inc.").sector("Technology").market("US").currency("USD").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("TSLA").name("Tesla Inc.").sector("Consumer Discretionary").market("US").currency("USD").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("NVDA").name("NVIDIA Corporation").sector("Technology").market("US").currency("USD").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("NFLX").name("Netflix Inc.").sector("Communication Services").market("US").currency("USD").marketCap("Large Cap").build(),
                StockUniverse.builder().symbol("WMT").name("Walmart Inc.").sector("Consumer Staples").market("US").currency("USD").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("MCD").name("McDonald's Corporation").sector("Consumer Discretionary").market("US").currency("USD").marketCap("Large Cap").build(),

                // India Stocks
                StockUniverse.builder().symbol("RELIANCE.NS").name("Reliance Industries Limited").sector("Energy").market("INDIA").currency("INR").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("TCS.NS").name("Tata Consultancy Services Limited").sector("Technology").market("INDIA").currency("INR").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("INFY.NS").name("Infosys Limited").sector("Technology").market("INDIA").currency("INR").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("HDFCBANK.NS").name("HDFC Bank Limited").sector("Financial Services").market("INDIA").currency("INR").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("ICICIBANK.NS").name("ICICI Bank Limited").sector("Financial Services").market("INDIA").currency("INR").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("SBIN.NS").name("State Bank of India").sector("Financial Services").market("INDIA").currency("INR").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("BHARTIARTL.NS").name("Bharti Airtel Limited").sector("Communication Services").market("INDIA").currency("INR").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("ITC.NS").name("ITC Limited").sector("Consumer Staples").market("INDIA").currency("INR").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("HINDUNILVR.NS").name("Hindustan Unilever Limited").sector("Consumer Staples").market("INDIA").currency("INR").marketCap("Mega Cap").build(),
                StockUniverse.builder().symbol("LT.NS").name("Larsen & Toubro Limited").sector("Construction").market("INDIA").currency("INR").marketCap("Large Cap").build()
            );

            for (StockUniverse stock : stocks) {
                if (stockUniverseRepo.findBySymbol(stock.getSymbol()).isEmpty()) {
                    stockUniverseRepo.save(stock);
                    log.info("[warmup] Seeded stock: {}", stock.getSymbol());
                }
            }

            symbolNormalizer.refreshCache();
            log.info("[warmup] Async stock universe seeding completed. Symbol normalizer cache refreshed.");
        });
    }
}
