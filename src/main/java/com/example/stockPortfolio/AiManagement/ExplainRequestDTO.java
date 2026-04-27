package com.example.stockPortfolio.AiManagement;

import lombok.Data;
import java.util.Map;

@Data
public class ExplainRequestDTO {
    private String symbol;
    private String trend;
    private String action;
    private String lang;
    private String behavior;
    private String type; // general or graph_point
    private Map<String, Object> metrics;
}
