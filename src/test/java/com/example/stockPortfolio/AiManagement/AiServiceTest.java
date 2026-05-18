package com.example.stockPortfolio.AiManagement;

import com.example.stockPortfolio.AiManagement.service.AiService;
import com.example.stockPortfolio.AiManagement.service.GroqGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AiServiceTest {

    @Mock
    private com.example.stockPortfolio.AiManagement.service.DeterministicInsightService deterministicInsightService;

    @Mock
    private GroqGateway groqGateway;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AiService aiService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGenerateMarketScenarios_Success() throws Exception {
        // Since AiService is now deterministic, it returns RELIANCE regardless of Groq
        List<Map<String, Object>> result = aiService.generateMarketScenarios("IN");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("RELIANCE", result.get(0).get("symbol"));
    }

    @Test
    void testGenerateMarketScenarios_Fallback() throws Exception {
        List<Map<String, Object>> result = aiService.generateMarketScenarios("US");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("RELIANCE", result.get(0).get("symbol")); // Based on buildFallbackScenarios
    }
}
