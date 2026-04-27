package com.example.stockPortfolio.AiManagement;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class OnboardingRequestDTO {
    private String userType;
    private String choice;
    private List<Map<String, Object>> decisions;
}
