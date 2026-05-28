package com.example.stockPortfolio.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Adds `Cache-Control` headers to public, user-agnostic market endpoints so
 * Vercel's edge CDN can cache them. This is the single highest-leverage
 * scaling change in the codebase:
 *
 *   - /api/market/indices, /trending, /vibe, /gainers, /losers, /news return
 *     identical bytes for every user during their cache window.
 *   - With `s-maxage=30, stale-while-revalidate=60` Vercel serves up to ~60s
 *     of those responses from the edge without ever touching Render.
 *   - Per-user endpoints (/api/portfolios/*, /api/holdings/*, /api/decision/*)
 *     are NEVER cached — they go through the standard auth path.
 *
 * If a request carries an Authorization header it is still NOT cached at the
 * shared edge — the `private` directive on those routes prevents leakage.
 */
@Component
@Order(50)
public class PublicCacheHeadersFilter extends OncePerRequestFilter {

    // Path prefixes that are safe to share across users. All are GET-only
    // market data; none accept a user identity.
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/market/indices",
            "/api/market/trending",
            "/api/market/vibe",
            "/api/market/gainers",
            "/api/market/losers",
            "/api/market/news",
            "/api/market/quote",
            "/api/market/quotes",
            "/api/market/insights/famous",
            "/api/market/sector"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        chain.doFilter(req, res);

        // Apply only to successful GETs we haven't already cache-tagged.
        if (!"GET".equalsIgnoreCase(req.getMethod())) return;
        if (res.getStatus() < 200 || res.getStatus() >= 300) return;
        if (res.containsHeader(HttpHeaders.CACHE_CONTROL)) return;

        String path = req.getRequestURI();
        if (path == null) return;

        boolean isPublic = false;
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) { isPublic = true; break; }
        }
        if (!isPublic) return;

        // s-maxage applies to shared/CDN caches (Vercel). max-age is for the
        // browser. SWR lets the edge serve stale while it refetches in the
        // background — the user never sees the cache-miss latency.
        res.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=10, s-maxage=30, stale-while-revalidate=60");
        // Vary on Origin so the CDN doesn't serve a CORS response back to a
        // non-CORS client (or vice versa) from cache.
        res.addHeader(HttpHeaders.VARY, "Origin");
    }
}
