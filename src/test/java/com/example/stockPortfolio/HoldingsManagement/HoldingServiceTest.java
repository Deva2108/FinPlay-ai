package com.example.stockPortfolio.HoldingsManagement;

import com.example.stockPortfolio.MarketManagement.MarketAnalysisService;
import com.example.stockPortfolio.MarketManagement.MarketGateway;
import com.example.stockPortfolio.PortfolioManagement.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the trading core. Focuses on the bug classes we patched:
 * negative quantities, zero quantities, missing fields, and balance flow.
 */
@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {

    @Mock private HoldingRepo holdingRepo;
    @Mock private TransactionRepo transactionRepo;
    @Mock private MarketGateway marketGateway;
    @Mock private PortfolioService portfolioService;
    @Mock private MarketAnalysisService marketAnalysisService;

    @InjectMocks private HoldingService holdingService;

    private Transaction buy(BigDecimal qty, BigDecimal price) {
        Transaction t = new Transaction();
        t.setUserId(1L);
        t.setPortfolioId(10L);
        t.setSymbol("AAPL");
        t.setQuantity(qty);
        t.setPrice(price);
        t.setType(Transaction.TransactionType.BUY);
        t.setTransactionDate(LocalDateTime.now());
        return t;
    }

    @Test
    @DisplayName("BUY: negative quantity is rejected without touching balance or holdings (C1 regression)")
    void rejectsNegativeQuantity() {
        Transaction t = buy(new BigDecimal("-100"), new BigDecimal("200"));

        assertThatThrownBy(() -> holdingService.processTransaction(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be > 0");

        verify(portfolioService, never()).updateBalance(anyLong(), any(), anyLong());
        verify(holdingRepo, never()).save(any());
    }

    @Test
    @DisplayName("BUY: zero quantity is rejected (C3 regression)")
    void rejectsZeroQuantity() {
        Transaction t = buy(BigDecimal.ZERO, new BigDecimal("200"));

        assertThatThrownBy(() -> holdingService.processTransaction(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be > 0");
    }

    @Test
    @DisplayName("BUY: zero or negative price is rejected")
    void rejectsBadPrice() {
        Transaction t = buy(new BigDecimal("1"), BigDecimal.ZERO);

        assertThatThrownBy(() -> holdingService.processTransaction(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price must be > 0");
    }

    @Test
    @DisplayName("BUY: missing symbol is rejected")
    void rejectsBlankSymbol() {
        Transaction t = buy(new BigDecimal("1"), new BigDecimal("200"));
        t.setSymbol("   ");

        assertThatThrownBy(() -> holdingService.processTransaction(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbol is required");
    }

    @Test
    @DisplayName("BUY: symbol is normalized to uppercase before lookup")
    void normalizesSymbol() {
        Transaction t = buy(new BigDecimal("1"), new BigDecimal("200"));
        t.setSymbol("aapl");
        when(holdingRepo.findByUserIdAndPortfolioIdAndSymbolWithLock(1L, 10L, "AAPL"))
                .thenReturn(Optional.empty());

        holdingService.processTransaction(t);

        verify(holdingRepo).findByUserIdAndPortfolioIdAndSymbolWithLock(1L, 10L, "AAPL");
        assertThat(t.getSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("BUY: first purchase saves a new Holding and debits cash")
    void firstBuyCreatesHolding() {
        Transaction t = buy(new BigDecimal("3"), new BigDecimal("100"));
        when(holdingRepo.findByUserIdAndPortfolioIdAndSymbolWithLock(1L, 10L, "AAPL"))
                .thenReturn(Optional.empty());

        holdingService.processTransaction(t);

        // 3 × 100 = 300 charged to cash → updateBalance called with -300
        verify(portfolioService).updateBalance(
                eq(10L),
                argThat(amt -> amt.compareTo(new BigDecimal("-300")) == 0),
                eq(1L));
        verify(holdingRepo).save(any(Holding.class));
        verify(transactionRepo).save(t);
    }

    @Test
    @DisplayName("BUY: second purchase rolls average cost correctly")
    void secondBuyRollsAverageCost() {
        Holding existing = new Holding();
        existing.setUserId(1L); existing.setPortfolioId(10L); existing.setSymbol("AAPL");
        existing.setQuantity(new BigDecimal("2"));
        existing.setBuyPrice(new BigDecimal("100"));
        when(holdingRepo.findByUserIdAndPortfolioIdAndSymbolWithLock(1L, 10L, "AAPL"))
                .thenReturn(Optional.of(existing));

        // BUY 4 @ 150  →  total cost = 200 + 600 = 800  / total qty = 6  →  avg 133.3333
        Transaction t = buy(new BigDecimal("4"), new BigDecimal("150"));
        holdingService.processTransaction(t);

        assertThat(existing.getQuantity()).isEqualByComparingTo("6");
        assertThat(existing.getBuyPrice()).isEqualByComparingTo(new BigDecimal("133.3333"));
    }

    @Test
    @DisplayName("SELL: more than held is rejected")
    void rejectsOversell() {
        Holding existing = new Holding();
        existing.setQuantity(new BigDecimal("1"));
        when(holdingRepo.findByUserIdAndPortfolioIdAndSymbolWithLock(1L, 10L, "AAPL"))
                .thenReturn(Optional.of(existing));

        Transaction t = buy(new BigDecimal("5"), new BigDecimal("100"));
        t.setType(Transaction.TransactionType.SELL);

        assertThatThrownBy(() -> holdingService.processTransaction(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient quantity");

        verify(portfolioService, never()).updateBalance(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("SELL: full position deletes the Holding row")
    void fullSellDeletesHolding() {
        Holding existing = new Holding();
        existing.setUserId(1L); existing.setPortfolioId(10L); existing.setSymbol("AAPL");
        existing.setQuantity(new BigDecimal("3"));
        existing.setBuyPrice(new BigDecimal("100"));
        when(holdingRepo.findByUserIdAndPortfolioIdAndSymbolWithLock(1L, 10L, "AAPL"))
                .thenReturn(Optional.of(existing));

        Transaction t = buy(new BigDecimal("3"), new BigDecimal("120"));
        t.setType(Transaction.TransactionType.SELL);

        holdingService.processTransaction(t);

        // proceeds = 3 × 120 = 360 credited
        verify(portfolioService).updateBalance(
                eq(10L),
                argThat(amt -> amt.compareTo(new BigDecimal("360")) == 0),
                eq(1L));
        verify(holdingRepo).delete(existing);
        verify(holdingRepo, never()).save(any(Holding.class));
    }
}
