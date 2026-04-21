package com.example.stockPortfolio.PortfolioManagement;

import com.example.stockPortfolio.ExceptionManagement.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PortfolioService {
    @Autowired
    private PortfolioRepo portfolioRepo;

    public Portfolio addPortfolio(Portfolio portfolio){
        if (portfolio.getBalance() == null) {
            portfolio.setBalance(100000.0);
        }
        if (portfolio.getInitialBalance() == null) {
            portfolio.setInitialBalance(portfolio.getBalance());
        }
        return portfolioRepo.save(portfolio);
    }

    public Portfolio updateBalance(Long portfolioId, Double amount) {
        Portfolio portfolio = portfolioRepo.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with ID: " + portfolioId));
        portfolio.setBalance(portfolio.getBalance() + amount);
        return portfolioRepo.save(portfolio);
    }

    public PortfolioResponseDTO getPortfoliosByUserId(Long userId){
        List<Portfolio> portfolios = portfolioRepo.findByUserId(userId);
        if(portfolios.isEmpty()){
            throw new ResourceNotFoundException("No portfolios found for user ID: "+userId);
        }
        return new PortfolioResponseDTO(
                portfolios,
                200,
                "Your portfolios are brought successfully!"
        );
    }

    public PortfolioResponseDTO getAllPortfolios(){
        List<Portfolio> portfolios = portfolioRepo.findAll();
        if (portfolios.isEmpty()) {
            throw new ResourceNotFoundException("No portfolios found!");
        }
        return new PortfolioResponseDTO(
                portfolios,
                200,
                "All Portfolios brought successfully"
        );
    }

}
