package com.example.stockPortfolio.Security;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Default RestTemplate: generous timeouts for slow upstream market data
     * providers (Finnhub, Yahoo, Alpha Vantage). DO NOT use this for Groq —
     * a stalled LLM would block Tomcat threads indefinitely.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .defaultHeader(org.springframework.http.HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build();
    }

    /**
     * Dedicated RestTemplate for Groq calls. Tight timeouts so a stalled LLM
     * fails fast and returns the deterministic fallback instead of holding the
     * request thread. Connect 2s / Read 4s — Groq's typical p99 is well under
     * this when healthy; when it's not healthy we want to bail.
     *
     * Inject by bean name from GroqGateway.
     */
    @Bean(name = "groqRestTemplate")
    public RestTemplate groqRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(4))
                .build();
    }
}
