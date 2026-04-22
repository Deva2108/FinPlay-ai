package com.example.stockPortfolio.AlertManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AlertHistoryRepo extends JpaRepository<AlertHistory, Long> {
    List<AlertHistory> findByUserIdOrderByTriggeredAtDesc(Long userId);
}
