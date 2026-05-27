import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bookmark, TrendingUp, TrendingDown, ArrowUpRight } from 'lucide-react';
import { getWatchlist, getLiveQuotes, swrRead, swrWrite } from '../../services/api';
import { formatPrice } from '../../utils/formatters';

/**
 * WatchlistWidget — isolated, self-polling leaf component.
 *
 * Owns its own state (symbols + enriched quotes) so polling never propagates
 * a re-render to Dashboard or its siblings. Pattern mirrors <LiveTicker />:
 * the interval lives here, callbacks and state stay here.
 *
 * Data flow:
 *   1. On mount: paint instantly from SWR cache (symbols only)
 *   2. getWatchlist() → fresh symbol list, written back to SWR
 *   3. getLiveQuotes(symbols) → enriches each row with price + daily %
 *   4. setInterval(60 000) repeats step 3 — aligned with scheduler cadence
 *
 * No new endpoints. No websocket. No Dashboard state touched.
 */
const WatchlistWidget = React.memo(function WatchlistWidget() {
  const navigate = useNavigate();

  // Seed from SWR for instant paint — network call follows immediately.
  const [symbols, setSymbols] = useState(() => swrRead('watchlist') || []);
  const [quotes, setQuotes]   = useState({});
  const cancelledRef = useRef(false);

  // Fetch the authoritative symbol list once on mount.
  useEffect(() => {
    cancelledRef.current = false;
    (async () => {
      try {
        const res = await getWatchlist();
        const data = res?.data;
        if (Array.isArray(data) && !cancelledRef.current) {
          setSymbols(data);
          swrWrite('watchlist', data);
        }
      } catch (_) { /* swallow — SWR seed keeps widget usable */ }
    })();
    return () => { cancelledRef.current = true; };
  }, []);

  // Enrich with live quotes — re-runs whenever the symbol list changes.
  // The 60s interval only re-renders this component, never Dashboard.
  useEffect(() => {
    if (symbols.length === 0) return;
    cancelledRef.current = false;

    const fetchQuotes = async () => {
      try {
        const res = await getLiveQuotes(symbols);
        if (Array.isArray(res?.data) && !cancelledRef.current) {
          const map = {};
          res.data.forEach(q => { if (q?.symbol) map[q.symbol] = q; });
          setQuotes(map);
        }
      } catch (_) { /* swallow — previous quotes remain displayed */ }
    };

    fetchQuotes(); // immediate enrich on symbol change
    const id = setInterval(fetchQuotes, 60000);
    return () => { cancelledRef.current = true; clearInterval(id); };
  }, [symbols]);

  return (
    <section className="space-y-4">
      <div className="flex items-center gap-2 px-2">
        <Bookmark size={16} className="text-blue-500" />
        <h3 className="text-[10px] font-black text-white uppercase tracking-widest">Your Watchlist</h3>
      </div>
      <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-4 min-h-[100px] flex flex-col gap-3">
        {symbols.length > 0 ? (
          symbols.map(symbol => {
            const q = quotes[symbol];
            const pct = q?.changesPercentage ?? null;
            const isUp = pct !== null && pct >= 0;
            return (
              <div
                key={symbol}
                onClick={() => navigate(`/stock/${symbol}`)}
                className="flex items-center justify-between p-3 bg-white/5 hover:bg-white/10 rounded-2xl border border-white/5 cursor-pointer transition-all group"
              >
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-lg bg-blue-500/10 flex items-center justify-center font-black text-blue-500 text-[10px]">
                    {symbol[0]}
                  </div>
                  <div>
                    <span className="text-xs font-black text-white group-hover:text-blue-400 transition-colors block">
                      {symbol}
                    </span>
                    {q?.price != null && (
                      <span className="text-[10px] font-bold text-slate-400 tabular-nums">
                        {formatPrice(q.price, q.currency || 'INR')}
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  {pct !== null ? (
                    <>
                      <span className={`text-[10px] font-black tabular-nums ${isUp ? 'text-emerald-400' : 'text-rose-400'}`}>
                        {isUp ? '+' : ''}{pct.toFixed(2)}%
                      </span>
                      {isUp
                        ? <TrendingUp  size={10} className="text-emerald-500" />
                        : <TrendingDown size={10} className="text-rose-500"   />}
                    </>
                  ) : (
                    <ArrowUpRight size={12} className="text-slate-600 group-hover:text-blue-400" />
                  )}
                </div>
              </div>
            );
          })
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center py-6 text-center space-y-2">
            <p className="text-[9px] font-black text-slate-600 uppercase tracking-widest">No assets tracked</p>
          </div>
        )}
      </div>
    </section>
  );
});

export default WatchlistWidget;
