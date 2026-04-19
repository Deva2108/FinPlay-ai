package com.example.stockPortfolio.PortfolioManagement;

import com.example.stockPortfolio.UserManagement.UserModel;
import com.example.stockPortfolio.UserManagement.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/portfolios")
@Tag(name="2. Portfolio", description = "2nd Controller, After User Register & Login")
@Slf4j
public class PortfolioController {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private UserService userService;

    private Long getLoggedInUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserModel user = userService.getUserByEmail(email);
        return user.getUserId();
    }

    @PostMapping
    public ResponseEntity<Portfolio> addPortfolio(@Valid @RequestBody Portfolio portfolio) {
        Long userId = getLoggedInUserId();
        log.info("Creating portfolio {} for user {}", portfolio.getPortfolioName(), userId);
        portfolio.setUserId(userId);
        Portfolio saved = portfolioService.addPortfolio(portfolio);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<PortfolioResponseDTO> getPortfolios() {
        System.out.println("Portfolio API hit");
        return ResponseEntity.ok(portfolioService.getPortfoliosByUserId(getLoggedInUserId()));
    }

    @GetMapping("/all")
    public ResponseEntity<PortfolioResponseDTO> getAllPortfolios(){
        System.out.println("Portfolio API hit - /all");
        return ResponseEntity.ok(portfolioService.getAllPortfolios());
    }
}
