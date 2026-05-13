package com.example.stockPortfolio.VaultManagement;

import com.example.stockPortfolio.MarketManagement.MarketGateway;
import com.example.stockPortfolio.AiManagement.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
public class VaultService {

    private final MarketGateway marketGateway;
    private final ObjectMapper objectMapper;

    public VaultScenarioDTO getDailyScenario() {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String json = marketGateway.getPrecomputedInsight("vault", "daily:" + date);
        
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, VaultScenarioDTO.class);
        } catch (Exception e) {
            log.error("Failed to parse vault scenario from Redis: {}", e.getMessage());
            return null;
        }
    }
}
