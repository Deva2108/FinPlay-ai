package com.example.stockPortfolio.Security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity(prePostEnabled = true)
@lombok.RequiredArgsConstructor
public class SecurityConfig {

    private final JwtRequestFilter jwtRequestFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final GlobalApiRateLimitFilter globalApiRateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // 1. ALWAYS permit OPTIONS for CORS preflight
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // 2. Public auth endpoints. `/api/auth/me` is intentionally NOT here —
                        // it needs the authentication context to identify the caller.
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/tutorial/insight").authenticated()
                        .requestMatchers("/api/content/**").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**", "/swagger-resources/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(c -> {})
                        .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                );

        // Order matters: rate-limit BEFORE jwt (cheap reject on brute force);
        // both BEFORE the standard username/password filter.
        http.addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(globalApiRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components().addSecuritySchemes("Bearer Authentication", createSecurityScheme()));
    }

    private SecurityScheme createSecurityScheme() {
        return new SecurityScheme()
                .name("Bearer Authentication")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }

    /**
     * BCrypt strength 12 — ~250 ms/login on a modest server. Stronger than the
     * default (10) without becoming a DoS vector. Existing strength-10 hashes
     * still verify correctly because bcrypt encodes the cost in the hash itself.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * {@link LoginRateLimitFilter} and {@link JwtRequestFilter} are {@code @Component}
     * beans, so Spring Boot would register them as global servlet filters AND we add
     * them to the SecurityFilterChain via {@code addFilterBefore}. Without these two
     * disablers each filter would run twice per request — for the rate limiter that
     * would halve the user-visible budget (5/min instead of 10/min).
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<LoginRateLimitFilter>
            disableLoginRateLimitAutoRegistration(LoginRateLimitFilter f) {
        var reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<GlobalApiRateLimitFilter>
            disableGlobalApiRateLimitAutoRegistration(GlobalApiRateLimitFilter f) {
        var reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtRequestFilter>
            disableJwtRequestFilterAutoRegistration(JwtRequestFilter f) {
        var reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }
}
