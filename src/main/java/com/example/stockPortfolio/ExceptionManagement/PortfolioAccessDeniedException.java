package com.example.stockPortfolio.ExceptionManagement;

public class PortfolioAccessDeniedException extends RuntimeException {
    public PortfolioAccessDeniedException(String message) {
        super(message);
    }
}