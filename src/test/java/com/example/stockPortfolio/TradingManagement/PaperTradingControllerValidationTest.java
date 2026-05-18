package com.example.stockPortfolio.TradingManagement;

import com.example.stockPortfolio.Security.JwtRequestFilter;
import com.example.stockPortfolio.UserManagement.User;
import com.example.stockPortfolio.UserManagement.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice-level test ensuring controller-side @Valid produces a clean 400 for the
 * exploit cases we patched in Phase 2 (C1 negative qty, C3 zero qty).
 */
@WebMvcTest(controllers = PaperTradingController.class,
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = JwtRequestFilter.class))
@org.springframework.boot.autoconfigure.ImportAutoConfiguration(
        exclude = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class PaperTradingControllerValidationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockBean private PaperTradingService paperTradingService;
    @MockBean private UserService userService;

    @Test
    @DisplayName("BUY with quantity=-100 → 400 from @Valid")
    @WithMockUser(username = "qa@finplay.test")
    void rejectsNegativeQuantity() throws Exception {
        User u = new User(); u.setUserId(1L); u.setEmail("qa@finplay.test");
        when(userService.getUserByEmail(any())).thenReturn(u);

        Map<String,Object> body = Map.of(
            "portfolioId", 10,
            "symbol", "AAPL",
            "side", "BUY",
            "quantity", new BigDecimal("-100"));

        mvc.perform(post("/api/trading/paper/orders")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("quantity")));
    }

    @Test
    @DisplayName("BUY with side='HACK' → 400 from @Pattern")
    @WithMockUser(username = "qa@finplay.test")
    void rejectsInvalidSide() throws Exception {
        User u = new User(); u.setUserId(1L); u.setEmail("qa@finplay.test");
        when(userService.getUserByEmail(any())).thenReturn(u);

        Map<String,Object> body = Map.of(
            "portfolioId", 10,
            "symbol", "AAPL",
            "side", "HACK",
            "quantity", new BigDecimal("1"));

        mvc.perform(post("/api/trading/paper/orders")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("side")));
    }
}
