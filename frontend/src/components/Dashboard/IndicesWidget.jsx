import React, { useState, useEffect, useRef } from 'react';
import { getIndices, swrRead, swrWrite } from '../../services/api';
import IndexCard from '../IndexCard';
import WidgetErrorBoundary from '../WidgetErrorBoundary';

/**
 * IndicesWidget — isolated, self-polling leaf component.
 *
 * Owns its own indices state so 60s polling never propagates a re-render
 * to Dashboard or its siblings. Mirrors the WatchlistWidget pattern.
 *
 * Data flow:
 *   1. Instant paint from SWR cache (`idx_<marketMode>`) on mount
 *   2. getIndices(marketMode) → fresh data, written back to SWR
 *   3. setInterval(60 000) repeats step 2 — aligned with backend scheduler
 *
 * Props:
 *   marketMode  — "INDIA" | "US" — controls which index set to fetch
 *   onIndexClick(idx) — called when user clicks an index card
 */
const IndicesWidget = React.memo(function IndicesWidget({ marketMode, onIndexClick }) {
  const [indices, setIndices] = useState(() => swrRead(`idx_${marketMode}`) || []);
  const cancelledRef = useRef(false);

  useEffect(() => {
    cancelledRef.current = false;
    const idxKey = `idx_${marketMode}`;

    // Seed from cache first so the widget is immediately useful
    const cached = swrRead(idxKey);
    if (cached && !cancelledRef.current) setIndices(cached);

    const fetchIndices = async () => {
      try {
        const res = await getIndices(marketMode);
        const d = res?.data;
        if (Array.isArray(d) && !cancelledRef.current) {
          setIndices(d);
          swrWrite(idxKey, d);
        }
      } catch (_) { /* swallow — SWR seed keeps widget usable */ }
    };

    fetchIndices();
    const id = setInterval(fetchIndices, 60000);
    return () => { cancelledRef.current = true; clearInterval(id); };
  }, [marketMode]);

  return (
    <section className="bg-slate-900/40 p-1 rounded-2xl border border-slate-800/50">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-1">
        <WidgetErrorBoundary name="Market Indices">
          {indices.map((idx) => (
            <IndexCard key={idx.symbol} {...idx} onClick={() => onIndexClick(idx)} />
          ))}
          {indices.length === 0 && (
            <div className="col-span-3 p-4 text-center text-[10px] font-black text-slate-500 uppercase tracking-widest animate-pulse">
              Loading Indices...
            </div>
          )}
        </WidgetErrorBoundary>
      </div>
    </section>
  );
});

export default IndicesWidget;
