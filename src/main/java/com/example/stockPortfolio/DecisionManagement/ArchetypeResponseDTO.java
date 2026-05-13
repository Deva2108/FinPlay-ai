package com.example.stockPortfolio.DecisionManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchetypeResponseDTO {
    private String title;
    private String trait;
}
