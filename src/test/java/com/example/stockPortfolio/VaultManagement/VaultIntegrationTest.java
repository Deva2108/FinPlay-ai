package com.example.stockPortfolio.VaultManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import com.example.stockPortfolio.MarketManagement.MarketGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // Disable security for simple integration test
@ActiveProfiles("h2")
class VaultIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketGateway marketGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetDailyScenario_Syncing() throws Exception {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        when(marketGateway.getPrecomputedInsight("vault", "daily:" + date)).thenReturn(null);

        mockMvc.perform(get("/api/vault/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonHelper -> {
                    String content = jsonHelper.getResponse().getContentAsString();
                    assertTrue(content.contains("SYNCING"));
                });
    }

    @Test
    void testGetDailyScenario_Ok() throws Exception {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        VaultScenarioDTO scenario = VaultScenarioDTO.builder()
                .scenario("Test Scenario")
                .options(List.of("BUY", "SKIP"))
                .correctAnswer("BUY")
                .build();
        
        when(marketGateway.getPrecomputedInsight("vault", "daily:" + date))
                .thenReturn(objectMapper.writeValueAsString(scenario));

        mockMvc.perform(get("/api/vault/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.status").value("OK"))
                .andExpect(jsonPath("$.data.scenario").value("Test Scenario"));
    }

    private void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
