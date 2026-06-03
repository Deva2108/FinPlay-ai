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

@SpringBootTest(properties = {
    // LettuceConnectionFactory implements both RedisConnectionFactory AND
    // ReactiveRedisConnectionFactory.  When @MockBean RedisConnectionFactory
    // replaces the Lettuce factory, the mock only exposes the non-reactive
    // type. RedisReactiveHealthContributorAutoConfiguration still activates
    // (its @ConditionalOnBean saw the LettuceConnectionFactory *definition*
    // which satisfies ReactiveRedisConnectionFactory before the mock swapped
    // in), then calls redisHealthContributor(Map<...> factories) with an
    // empty map → "Beans must not be empty".  Disabling the health indicator
    // short-circuits both reactive and non-reactive Redis health contributors.
    "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false) // Disable security for simple integration test
@ActiveProfiles("h2")
class VaultIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // application.properties sets spring.data.redis.url=${REDIS_URL} with no
    // default. When REDIS_URL is absent from the environment, Lettuce receives
    // the literal string "${REDIS_URL}" which it rejects as an invalid URL,
    // crashing the full Spring context before any beans are wired.
    //
    // LettuceConnectionFactory implements BOTH RedisConnectionFactory AND
    // ReactiveRedisConnectionFactory. We must mock both interfaces so that
    // all consumers (RedisTemplate, ReactiveRedisTemplate, health indicators)
    // find their required beans. Without the reactive mock, RedisReactiveAuto-
    // Configuration.reactiveRedisTemplate() fails with "No qualifying bean of
    // type ReactiveRedisConnectionFactory".
    //
    // LettuceConnectionConfiguration and LettuceReactiveConnectionConfiguration
    // are both @ConditionalOnMissingBean on their respective factory types, so
    // they skip instantiation when mocks are already registered. The invalid
    // ${REDIS_URL} factory method therefore never runs.
    //
    // All Redis/ReactiveRedis operations return Mockito defaults (null/empty),
    // which is acceptable here because MarketGateway (the only Redis-reading
    // component exercised by the test) is itself @MockBean below.
    @MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private org.springframework.data.redis.connection.ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

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
