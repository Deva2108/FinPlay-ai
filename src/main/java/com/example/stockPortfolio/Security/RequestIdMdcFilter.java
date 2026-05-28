package com.example.stockPortfolio.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Propagates the X-Request-Id header from the frontend into the SLF4J MDC so
 * the `[rid=%X{rid:-}]` slot in the configured logging pattern actually
 * populates. If the client didn't send one, we generate one and echo it back
 * — every log line emitted while processing the request is then traceable
 * back to the user's browser network tab.
 *
 * The header name is also exposed via CORS in {@link CorsConfig} so the
 * browser can read it from a fetch/axios response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdMdcFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "rid";

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String rid = req.getHeader(HEADER);
        if (rid == null || rid.isBlank() || rid.length() > 64) {
            rid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_KEY, rid);
        res.setHeader(HEADER, rid);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
