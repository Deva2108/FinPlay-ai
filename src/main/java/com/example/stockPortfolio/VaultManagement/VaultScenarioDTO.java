package com.example.stockPortfolio.VaultManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * FinPlay Vault Scenario (MANDATORY).
 * Strictly follows Section 4.4 of the Production Requirements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultScenarioDTO {
    private String scenario;
    private List<String> options; // ["BUY","WATCHLIST","SKIP"]
    private String correctAnswer;
    private String explanation;
    private String learning;
}
