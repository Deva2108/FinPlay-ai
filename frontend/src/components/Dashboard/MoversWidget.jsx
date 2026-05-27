import React, { useState, useEffect, useRef } from 'react';
import { TrendingUp } from 'lucide-react';
import { getTrending, swrRead, swrWrite } from '../../services/api';

/**
 * MoversWidget — isolated, self-polling leaf component.
 *
 * Owns trending state so 60s polling never propagates a re-render
 * to Dashboard or its siblings.
 *
 * Data flow:
 *   1. Instant paint from SWR cache (`trending`) on mount
 *   2. getTrending() → fresh data, written back to SWR
 *   3. Reports breadth counts to parent via onBreadthChange({ up, down })
 *   4. setInterval(60 000) repeats steps 2-3
 *
 * Props:
 *   openStockPanel(stock)          — open stock detail panel on row click
 *   onBreadthChange({ up, down })  — called after each fetch so parent can
 *                                    flash the breadth indicator in the header
 */
const MoversWidget = React.memo(function MoversWidget({ openStockPanel, onBreadthChange }) {
  const [trending, setTrending] = useState(() => swrRead('trending') || []);
  const cancelledRef = useRef(false);
  // Keep a stable ref to the callback so the interval closure never goes stale
  const onBreadthChangeRef = useRef(onBreadthChange);
  onBreadthChangeRef.current = onBreadthChange;

  useEffect(() => {
    cancelledRef.current = false;

    const fetchTrending = async () => {
      try {
        const res = await getTrending();
        const d = res?.data;
        if (Array.isArray(d) && !cancelledRef.current) {
          setTrending(d);
          swrWrite('trending', d);
          const up   = d.filter(s => (s?.change || '').startsWith('+')).length;
          const down = d.filter(s => (s?.change || '').startsWith('-')).length;
          onBreadthChangeRef.current?.({ up, down });
        }
      } catch (_) { /* swallow — previous data stays displayed */ }
    };

    fetchTrending();
    const id = setInterval(fetchTrending, 60000);
    return () => { cancelledRef.current = true; clearInterval(id); };
  }, []); // intentionally empty — marketMode-agnostic, trending is global

  const gainers = trending.filter(s => (s?.change || '').startsWith('+'));
  const losers  = trending.filter(s => (s?.change || '').startsWith('-'));

  return (
    <section className="space-y-4">
      <div className="flex items-center gap-2 px-2">
        <TrendingUp size={16} className="text-emerald-500" />
        <h3 className="text-[10px] font-black text-white uppercase tracking-widest">Top Movers</h3>
      </div>
      <div className="space-y-3">
        <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-4 space-y-3">
          <p className="text-[9px] font-black text-emerald-500 uppercase tracking-[0.2em] mb-2 flex items-center gap-2">🔥 Top Gainers</p>
          {gainers.slice(0, 2).map(stock => (
            <div
              key={stock?.symbol}
              onClick={() => openStockPanel(stock)}
              className="flex items-center justify-between p-3 bg-white/5 [@media(hover:hover)]:hover:bg-white/10 rounded-2xl border border-white/5 cursor-pointer transition-colors group"
            >
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-emerald-500/10 flex items-center justify-center font-black text-emerald-500 text-[10px]">
                  {(stock?.symbol || '')[0]}
                </div>
                <span className="text-xs font-black text-white group-hover:text-emerald-400 transition-colors">{stock?.symbol}</span>
              </div>
              <span className="text-xs font-black text-emerald-500">{stock?.change}</span>
            </div>
          ))}
        </div>
        <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-4 space-y-3">
          <p className="text-[9px] font-black text-rose-500 uppercase tracking-[0.2em] mb-2 flex items-center gap-2">📉 Top Losers</p>
          {losers.slice(0, 2).map(stock => (
            <div
              key={stock?.symbol}
              onClick={() => openStockPanel(stock)}
              className="flex items-center justify-between p-3 bg-white/5 [@media(hover:hover)]:hover:bg-white/10 rounded-2xl border border-white/5 cursor-pointer transition-colors group"
            >
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-rose-500/10 flex items-center justify-center font-black text-rose-500 text-[10px]">
                  {(stock?.symbol || '')[0]}
                </div>
                <span className="text-xs font-black text-white group-hover:text-rose-400 transition-colors">{stock?.symbol}</span>
              </div>
              <span className="text-xs font-black text-rose-500">{stock?.change}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
});

export default MoversWidget;
