import { useEffect, useRef } from 'react';
import { getIndices, getTrending } from '../../services/api';

/**
 * LiveTicker — isolated polling leaf component.
 *
 * This component owns the live-refresh interval for market indices and trending
 * symbols. It renders nothing (returns null) — its only job is to call the
 * parent-supplied callbacks when fresh data arrives.
 *
 * Isolation benefit: the setInterval lives here, not in Dashboard. When the tick
 * fires it calls stable ref-wrapped callbacks so Dashboard state updates are
 * batched into a single re-render. React.memo'd siblings that don't consume
 * indices/trending won't be disturbed.
 *
 * Interval is 60 000 ms (1 min) — aligned with the backend MarketDataScheduler
 * cadence. Polling faster than the scheduler refresh rate wastes Redis bandwidth
 * and yields no new data.
 *
 * Props:
 *   marketMode      — passed to getIndices(); re-creates interval on change.
 *   onIndicesUpdate — called with Array<IndexDTO> on each successful tick.
 *   onTrendingUpdate — called with Array<TrendDTO> on each successful tick.
 *   onRefresh       — called (no args) after each successful tick so the parent
 *                     can stamp lastRefresh for the FreshnessLabel.
 */
export default function LiveTicker({ marketMode, onIndicesUpdate, onTrendingUpdate, onRefresh }) {
  // Stable refs prevent the interval from seeing stale closures when the parent
  // re-renders and passes new callback instances (e.g. after setIndices call).
  const onIndicesRef = useRef(onIndicesUpdate);
  const onTrendingRef = useRef(onTrendingUpdate);
  const onRefreshRef = useRef(onRefresh);

  useEffect(() => { onIndicesRef.current = onIndicesUpdate; }, [onIndicesUpdate]);
  useEffect(() => { onTrendingRef.current = onTrendingUpdate; }, [onTrendingUpdate]);
  useEffect(() => { onRefreshRef.current = onRefresh; }, [onRefresh]);

  useEffect(() => {
    let cancelled = false;

    const tick = async () => {
      // Skip when tab is backgrounded — no visible benefit, saves API credits.
      if (document.hidden) return;
      try {
        const [indicesRes, trendingRes] = await Promise.all([
          getIndices(marketMode),
          getTrending()
        ]);
        if (cancelled) return;
        if (Array.isArray(indicesRes?.data)) onIndicesRef.current?.(indicesRes.data);
        if (Array.isArray(trendingRes?.data)) onTrendingRef.current?.(trendingRes.data);
        onRefreshRef.current?.();
      } catch (_) {
        // Swallow — transient failures are expected; next tick will retry.
      }
    };

    // 60s matches backend scheduler cadence — polling faster yields stale cache hits.
    const id = setInterval(tick, 60000);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [marketMode]); // Re-register when market mode flips (IN ↔ US)

  return null; // Pure side-effect leaf — renders nothing into the DOM.
}
