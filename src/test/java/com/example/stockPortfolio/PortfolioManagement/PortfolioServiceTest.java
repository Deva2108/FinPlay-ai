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
        
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(portfolioId);
        portfolio.setBalance(new BigDecimal("1000.00"));

        when(portfolioRepo.findByPortfolioIdAndUser_UserId(portfolioId, userId)).thenReturn(Optional.of(portfolio));
        when(portfolioRepo.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioDTO result = portfolioService.updateBalance(portfolioId, amount, userId);

        assertEquals(new BigDecimal("1500.00"), portfolio.getBalance());
        assertEquals(new BigDecimal("1500.00"), result.getBalance());
        verify(portfolioRepo, times(1)).save(portfolio);
    }
}
