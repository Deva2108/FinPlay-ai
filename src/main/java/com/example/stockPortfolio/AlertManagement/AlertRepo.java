package com.example.stockPortfolio.AlertManagement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRepo extends JpaRepository<Alert, Long> {
    List<Alert> findByUserId(Long userId);
}