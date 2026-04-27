package com.example.stockPortfolio.DecisionManagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionRepo extends JpaRepository<Decision, Long> {
    List<Decision> findByUser_UserId(Long userId);
    List<Decision> findTop10ByUser_UserIdOrderByTimestampDesc(Long userId);
}
