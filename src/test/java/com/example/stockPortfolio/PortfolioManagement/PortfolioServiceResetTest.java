package com.example.stockPortfolio.PortfolioManagement;

import com.example.stockPortfolio.ExceptionManagement.PortfolioAccessDeniedException;
import com.example.stockPortfolio.ExceptionManagement.ResourceNotFoundException;
import com.example.stockPortfolio.HoldingsManagement.Holding;
import com.example.stockPortfolio.HoldingsManagement.HoldingRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies the user-facing reset path that replaces the dangerous open balance endpoint. */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceResetTest {

    @Mock private PortfolioRepo portfolioRepo;
    @Mock private HoldingRepo holdingRepo;

    @InjectMocks private PortfolioService service;

    private Portfolio newPortfolio() {
        Portfolio p = new Portfolio();
        p.setPortfolioId(7L);
        p.setBalance(new BigDecimal("42345.67"));
        p.setInitialBalance(new BigDecimal("100000"));
        return p;
    }

    @Test
    @DisplayName("reset: restores cash to initialBalance and deletes all holdings")
    void resetClearsHoldingsAndRestoresBalance() {
        Portfolio p = newPortfolio();
        when(portfolioRepo.findByPortfolioIdAndUser_UserId(7L, 1L)).thenReturn(Optional.of(p));

        Holding h1 = new Holding(); h1.setHoldingId(1L);
        Holding h2 = new Holding(); h2.setHoldingId(2L);
        when(holdingRepo.findByUserIdAndPortfolioId(1L, 7L)).thenReturn(List.of(h1, h2));

        PortfolioDTO out = service.resetPortfolio(7L, 1L);

        assertThat(out.getBalance()).isEqualByComparingTo("100000");
        verify(holdingRepo).deleteAll(List.of(h1, h2));
        verify(portfolioRepo).save(p);
    }

    @Test
    @DisplayName("reset: 403 when portfolio belongs to another user")
    void rejectsCrossUser() {
        when(portfolioRepo.findByPortfolioIdAndUser_UserId(7L, 99L)).thenReturn(Optional.empty());
        when(portfolioRepo.existsById(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.resetPortfolio(7L, 99L))
                .isInstanceOf(PortfolioAccessDeniedException.class);

        verify(holdingRepo, never()).deleteAll(any(List.class));
    }

    @Test
    @DisplayName("reset: 404 when portfolio does not exist")
    void notFound() {
        when(portfolioRepo.findByPortfolioIdAndUser_UserId(7L, 1L)).thenReturn(Optional.empty());
        when(portfolioRepo.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.resetPortfolio(7L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("reset: idempotent when no holdings exist")
    void noHoldingsIsFine() {
        Portfolio p = newPortfolio();
        p.setBalance(new BigDecimal("100000"));
        when(portfolioRepo.findByPortfolioIdAndUser_UserId(7L, 1L)).thenReturn(Optional.of(p));
        when(holdingRepo.findByUserIdAndPortfolioId(1L, 7L)).thenReturn(List.of());

        PortfolioDTO out = service.resetPortfolio(7L, 1L);

        assertThat(out.getBalance()).isEqualByComparingTo("100000");
        verify(holdingRepo, never()).deleteAll(any(List.class));
    }
}
