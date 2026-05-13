package com.example.stockPortfolio.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Tags every request with a short correlation ID so log lines from the same
 * HTTP call share an identifier ("rid=ab12cd34"). The ID is also echoed back
 * in the X-Request-Id response header for client-side debugging.
 */
@Component
@Order(-1) // runs before everything else, including auth filters
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "rid";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String rid = req.getHeader(HEADER);
        if (rid == null || rid.isBlank() || rid.length() > 64) {
            rid = UUID.randomUUID().toString().substring(0, 8);
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
