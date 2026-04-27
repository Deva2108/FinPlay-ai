package com.example.stockPortfolio.PortfolioManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponseDTO {
    private List<PortfolioDTO> result;
    private int status;
    private String message;
}
