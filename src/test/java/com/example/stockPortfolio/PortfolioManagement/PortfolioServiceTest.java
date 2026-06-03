package com.example.stockPortfolio.PortfolioManagement;

import com.example.stockPortfolio.UserManagement.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioServiceTest {

    @Mock
    private PortfolioRepo portfolioRepo;

    @Mock
    private com.example.stockPortfolio.HoldingsManagement.HoldingRepo holdingRepo;

    // PortfolioService gained a TransactionRepo constructor param after this test
    // was written. Without this mock, openMocks() cannot satisfy the constructor
    // and InjectMocks silently injects null, causing NPEs downstream.
    @Mock
    private com.example.stockPortfolio.HoldingsManagement.TransactionRepo transactionRepo;

    @InjectMocks
    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetPortfoliosByUserId_Success() {
        Long userId = 1L;
        User user = new User();
        user.setUserId(userId);
        
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(10L);
        portfolio.setUser(user);
        portfolio.setBalance(new BigDecimal("1000.00"));

        when(portfolioRepo.findByUser_UserId(userId)).thenReturn(Collections.singletonList(portfolio));

        PortfolioResponseDTO result = portfolioService.getPortfoliosByUserId(userId);

        assertNotNull(result);
        assertEquals(1, result.getResult().size());
        assertEquals(new BigDecimal("1000.00"), result.getResult().get(0).getBalance());
        verify(portfolioRepo, times(1)).findByUser_UserId(userId);
    }

    @Test
    void testUpdateBalance_Success() {
        Long portfolioId = 10L;
        Long userId = 1L;
        BigDecimal amount = new BigDecimal("500.00");

        // updateBalance() now uses an atomic SQL UPDATE (updateBalanceAtomic) rather
        // than a load-modify-save cycle. The old mock for findByPortfolioIdAndUser_UserId
        // was stale — updateBalanceAtomic() returned 0 (Mockito default), which caused
        // the service to throw ResourceNotFoundException("Portfolio not found: 10").
        //
        // New contract:
        //   1. updateBalanceAtomic returns 1  → update succeeded in DB
        //   2. getPortfolioById reads back via readByPortfolioIdAndUser_UserId (no @Lock)
        //      → returns the post-update state we synthesise here.
        Portfolio updatedPortfolio = new Portfolio();
        updatedPortfolio.setPortfolioId(portfolioId);
        updatedPortfolio.setBalance(new BigDecimal("1500.00"));

        when(portfolioRepo.updateBalanceAtomic(portfolioId, amount, userId)).thenReturn(1);
        when(portfolioRepo.readByPortfolioIdAndUser_UserId(portfolioId, userId))
                .thenReturn(Optional.of(updatedPortfolio));

        PortfolioDTO result = portfolioService.updateBalance(portfolioId, amount, userId);

        // Assert on the returned DTO, not the in-memory entity; the atomic update
        // modifies the DB row, not the Java object.
        assertEquals(new BigDecimal("1500.00"), result.getBalance());
        verify(portfolioRepo, times(1)).updateBalanceAtomic(portfolioId, amount, userId);
    }
}
