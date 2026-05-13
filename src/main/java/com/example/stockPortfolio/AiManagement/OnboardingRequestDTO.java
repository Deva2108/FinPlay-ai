package com.example.stockPortfolio.AiManagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRequestDTO {
    private String userType;
    private String choice;
    private String sessionId;
    private List<Map<String, Object>> decisions;
}
