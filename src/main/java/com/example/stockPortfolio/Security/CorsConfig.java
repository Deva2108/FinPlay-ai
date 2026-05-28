package com.example.stockPortfolio.Security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * Origin patterns. Supports `*` wildcards so Vercel preview deploys (which
     * generate a unique URL per push, e.g. https://finplay-git-feature-x-user.vercel.app)
     * are accepted without redeploying the backend each time. Override via the
     * CORS_ALLOWED_ORIGINS env var; comma-separated.
     */
    @Value("${cors.allowed.origins:http://localhost:5173,http://localhost:5174,http://localhost:3000,https://*.vercel.app}")
    private String[] allowedOriginPatterns;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // setAllowedOriginPatterns (not setAllowedOrigins) is REQUIRED when
        // allowCredentials=true is combined with wildcard origins. Spring will
        // throw at startup if you mix wildcards into setAllowedOrigins.
        List<String> patterns = (allowedOriginPatterns != null && allowedOriginPatterns.length > 0)
                ? Arrays.asList(allowedOriginPatterns)
                : List.of("http://localhost:5173");
        configuration.setAllowedOriginPatterns(patterns);

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Explicitly allow headers used by common clients/Axios. X-Request-Id
        // is added so the request-correlation interceptor (added in this pass)
        // survives the CORS preflight.
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // 1 hour for preflight cache

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
