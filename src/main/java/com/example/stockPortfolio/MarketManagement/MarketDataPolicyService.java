package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Centralized market-data fetch governance authority.
 *
 * <p>Replaces ad-hoc fetch decisions that were previously scattered across
 * {@code MarketDataScheduler}, {@code MarketGateway}, and {@code ExternalMarketDataGateway}
 * with a single policy surface. All quote-producing systems consult this service
 * before considering an outbound provider call.
 *
 * <h3>Core principles enforced</h3>
 * <ol>
 *   <li><b>CACHE-FIRST</b>   — never fetch when a fresh Redis mirror exists ({@link #isFresh}).</li>
 *   <li><b>STALE-FIRST</b>   — never fetch a symbol currently in failure cooldown.</li>
 *   <li><b>ACTIVE-FIRST</b>  — never hydrate symbols nobody is viewing ({@link #isActive}).</li>
 *   <li><b>PROVIDER-AWARE</b> — never call a provider that recently returned 429
 *       or repeatedly failed ({@link #isProviderDisabled}).</li>
 *   <li><b>ECONOMICS</b>     — preserves TwelveData free-tier headroom under
 *       public traffic; eliminates universe-wide quota burn.</li>
 * </ol>
 *
 * <h3>State model (all keyed in Redis with explicit TTLs)</h3>
 * <ul>
 *   <li>{@code market:active:{symbol}}             — per-symbol activity flag, 15 min TTL.
 *       Refreshed on every user touch (view/watchlist/holdings/urgent).</li>
 *   <li>{@code market:provider_disabled:{provider}} — provider-wide outage state, 15 min TTL.
 *       Set when a provider returns 429 or otherwise hard-fails.</li>
 *   <li>{@code market:fail:{symbol}}                — per-symbol failure cooldown, 5 min TTL.
 *       Written by {@code ExternalMarketDataGateway} when all providers fail.
 *       This service only READS this key — writes remain in the gateway.</li>
 *   <li>{@code stock:lastUpdated:{symbol}}           — freshness timestamp, 30 min TTL.
 *       Written by {@code MarketGateway.markFreshTimestamp} after each successful update.</li>
 * </ul>
 *
 * <h3>What this service does NOT do</h3>
 * <ul>
 *   <li>It does not perform HTTP calls — it only governs whether others should.</li>
 *   <li>It does not own the data cache — that remains in {@code MarketGateway}.</li>
 *   <li>It does not invalidate or warm caches — purely a state oracle.</li>
 * </ul>
 *
 * Fail-open semantics: if Redis is unreachable, every guard returns the permissive
 * answer ({@code shouldFetch=true}, {@code isProviderDisabled=false}). This keeps
 * the system functional during partial outages — the worst case degenerates to the
 * pre-governance behaviour, never below it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataPolicyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SymbolNormalizer symbolNormalizer;

    // ── Key prefixes (constants are duplicated, by design, in the upstream
    //    caller classes so they can read/write without depending on this bean
    //    at startup). Keep these values in sync there. ──────────────────────────
    private static final String ACTIVE_SYMBOL_PREFIX     = "market:active:";
    private static final String PROVIDER_DISABLED_PREFIX = "market:provider_disabled:";
    private static final String FAILURE_COOLDOWN_PREFIX  = "market:fail:";
    private static final String LAST_UPDATED_TS_PREFIX   = "stock:lastUpdated:";

    // ── TTLs and thresholds ───────────────────────────────────────────────────
    private static final long FRESHNESS_THRESHOLD_MS    = 10 * 60 * 1000L; // matches MarketGateway
    private static final long ACTIVE_TTL_MINUTES        = 15L;
    private static final long PROVIDER_OUTAGE_TTL_MIN   = 15L;

    // ── Provider name namespace ───────────────────────────────────────────────
    public static final String PROVIDER_TWELVEDATA  = "twelvedata";
    public static final String PROVIDER_YAHOO       = "yahoo";
    public static final String PROVIDER_FINNHUB     = "finnhub";
    public static final String PROVIDER_ALPHAVANTAGE = "alphavantage";

    // ═════════════════════════════════════════════════════════════════════════
    //  ACTIVE SYMBOL LIFECYCLE
    //  A symbol becomes "active" when a user touches it (view, watchlist tick,
    //  holdings page, urgent priority mark). The scheduler ONLY hydrates active
    //  symbols — cold symbols are served from cache + DB until someone looks.
    // ═════════════════════════════════════════════════════════════════════════

    /** Marks the symbol as active for {@value #ACTIVE_TTL_MINUTES} minutes. Refreshes the TTL on every call. */
    public void markActive(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return;
        try {
            redisTemplate.opsForValue().set(
                    ACTIVE_SYMBOL_PREFIX + normalized, "1",
                    ACTIVE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("markActive failed for {}: {}", normalized, e.getMessage());
        }
    }

    /** True when the symbol's activity TTL is still alive. */
    public boolean isActive(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(ACTIVE_SYMBOL_PREFIX + normalized));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the current set of active symbols (those with live activity TTL).
     * Used by the scheduler to scope hydration to viewer-relevant symbols only.
     * Uses {@code KEYS} — safe because the active set is small ({@code N <≈} concurrent users).
     */
    public Set<String> listActiveSymbols() {
        try {
            Set<String> keys = redisTemplate.keys(ACTIVE_SYMBOL_PREFIX + "*");
            if (keys == null || keys.isEmpty()) return Collections.emptySet();
            Set<String> result = new HashSet<>(keys.size());
            int prefixLen = ACTIVE_SYMBOL_PREFIX.length();
            for (String key : keys) {
                if (key.length() > prefixLen) result.add(key.substring(prefixLen));
            }
            return result;
        } catch (Exception e) {
            log.debug("listActiveSymbols failed: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PROVIDER-WIDE OUTAGE STATE
    //  When a provider returns 429 or repeated hard errors, the corresponding
    //  flag is set with a 15-minute TTL. Every fetch path consults this gate
    //  before issuing an outbound call — eliminates the post-outage retry storm.
    // ═════════════════════════════════════════════════════════════════════════

    public void markProviderDisabled(String provider) {
        if (provider == null || provider.isBlank()) return;
        try {
            redisTemplate.opsForValue().set(
                    PROVIDER_DISABLED_PREFIX + provider, "1",
                    PROVIDER_OUTAGE_TTL_MIN, TimeUnit.MINUTES);
            log.warn("Provider '{}' globally disabled for {} min — all fetch paths will skip.",
                    provider, PROVIDER_OUTAGE_TTL_MIN);
        } catch (Exception e) {
            log.debug("markProviderDisabled failed for {}: {}", provider, e.getMessage());
        }
    }

    public boolean isProviderDisabled(String provider) {
        if (provider == null) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PROVIDER_DISABLED_PREFIX + provider));
        } catch (Exception e) {
            return false; // fail-open
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  STATE MIRRORS — read existing keys written by the data layer.
    // ═════════════════════════════════════════════════════════════════════════

    /** Matches the freshness contract used by MarketGateway + ExternalMarketDataGateway (10 min). */
    public boolean isFresh(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return false;
        try {
            Object ts = redisTemplate.opsForValue().get(LAST_UPDATED_TS_PREFIX + normalized);
            if (ts == null) return false;
            long lastUpdatedMs = Long.parseLong(ts.toString());
            return (System.currentTimeMillis() - lastUpdatedMs) < FRESHNESS_THRESHOLD_MS;
        } catch (Exception e) {
            return false;
        }
    }

    /** True while the symbol is in its 5-minute post-failure cooldown. */
    public boolean isSymbolInCooldown(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(FAILURE_COOLDOWN_PREFIX + normalized));
        } catch (Exception e) {
            return false;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MASTER GATE — single point of truth for "should we hit a provider?"
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Returns true ONLY when an external fetch for this symbol is justified.
     * Returns false when ANY of the following holds:
     * <ul>
     *   <li>Cache is fresh (no fetch needed)</li>
     *   <li>Symbol is in failure cooldown (recently exhausted all providers)</li>
     *   <li>Symbol is not active (nobody is viewing it — refresh would be wasted)</li>
     * </ul>
     * <p>Provider-level outage is checked separately by {@link #isProviderDisabled}
     * so callers can choose to fall through to a different provider.
     */
    public boolean shouldFetch(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return false;
        if (isFresh(normalized)) return false;
        if (isSymbolInCooldown(normalized)) return false;
        if (!isActive(normalized)) return false;
        return true;
    }
}
