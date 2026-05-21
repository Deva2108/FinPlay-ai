package com.example.stockPortfolio.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global API Rate Limiter to protect against Economic DoS and Quota Drainage.
 * Limits all authenticated API requests to 60 per minute per user (IP-based).
 */
@Component
@Order(1) // Runs after LoginRateLimitFilter
@Slf4j
public class GlobalApiRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, TokenBucket> userBuckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Public endpoints already have specific limiters or are very cheap
        return path.startsWith("/api/auth/login") || 
               path.startsWith("/api/auth/register") ||
               path.startsWith("/actuator/") ||
               path.contains("swagger") ||
               path.contains("api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String identifier = clientIdentifier(req);
        TokenBucket bucket = userBuckets.computeIfAbsent(identifier, k -> new TokenBucket());

        if (!bucket.tryConsume()) {
            log.warn("Global API rate limit hit: identifier={} path={}", identifier, req.getRequestURI());
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write(
                "{\"success\":false,\"message\":\"Too many requests. Please slow down to protect our market data feeds.\"," +
                "\"meta\":{\"status\":\"ERROR\",\"source\":\"global-rate-limit\"}}");
            return;
        }

        chain.doFilter(req, res);
    }

    private String clientIdentifier(HttpServletRequest req) {
        // Fallback to IP. In a production system with high horizontal scale, 
        // this should be Redis-backed and use the Principal (Username).
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr() == null ? "unknown" : req.getRemoteAddr();
    }

    private static final class TokenBucket {
        private long lastRefillTimestamp = Instant.now().toEpochMilli();
        private double tokens = MAX_REQUESTS_PER_MINUTE;

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = Instant.now().toEpochMilli();
            double elapsedMinutes = (now - lastRefillTimestamp) / (double) WINDOW.toMillis();
            tokens = Math.min(MAX_REQUESTS_PER_MINUTE, tokens + (elapsedMinutes * MAX_REQUESTS_PER_MINUTE));
            lastRefillTimestamp = now;
        }
    }
}
