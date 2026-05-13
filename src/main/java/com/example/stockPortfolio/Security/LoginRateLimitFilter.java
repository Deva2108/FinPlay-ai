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
 * Lightweight per-IP token bucket guarding {@code /api/auth/login} and
 * {@code /api/auth/register}. Prevents credential-stuffing without pulling in
 * a heavy gateway. Limits: 10 attempts per IP per 60 seconds.
 *
 * <p>Single-node only — fine for the Render free dyno target. If we ever
 * scale horizontally, swap this for a Redis-backed counter.
 */
@Component
@Order(0)
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/api/auth/login") || path.equals("/api/auth/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String ip = clientIp(req);
        Bucket b = buckets.computeIfAbsent(ip, k -> new Bucket());
        if (!b.allow()) {
            log.warn("Auth rate limit hit: ip={} path={}", ip, req.getRequestURI());
            res.setStatus(429); // SC_TOO_MANY_REQUESTS
            res.setContentType("application/json");
            res.getWriter().write(
                "{\"success\":false,\"message\":\"Too many auth attempts. Try again in a minute.\"," +
                "\"meta\":{\"status\":\"ERROR\",\"source\":\"rate-limit\"}}");
            return;
        }
        chain.doFilter(req, res);
    }

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return req.getRemoteAddr() == null ? "unknown" : req.getRemoteAddr();
    }

    /** Sliding-window-ish bucket. Cheap, GC-friendly, no external deps. */
    private static final class Bucket {
        private long windowStartEpochMs = Instant.now().toEpochMilli();
        private int count = 0;

        synchronized boolean allow() {
            long now = Instant.now().toEpochMilli();
            if (now - windowStartEpochMs > WINDOW.toMillis()) {
                windowStartEpochMs = now;
                count = 0;
            }
            if (count >= MAX_ATTEMPTS) return false;
            count++;
            return true;
        }
    }
}
