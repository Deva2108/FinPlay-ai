package com.example.stockPortfolio.AiManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplainRequestDTO {

    private String symbol;
    private String trend;
    private String action;
    private String lang;
    private String behavior;
    private String type; // general or graph_point
    private Map<String, Object> metrics;
}
