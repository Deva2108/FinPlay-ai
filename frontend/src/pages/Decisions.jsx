import React, { useState, useEffect, useMemo } from 'react';
import { motion } from 'framer-motion';
import { getRecentDecisions } from '../services/api';
import { History, TrendingUp, Clock, Activity } from 'lucide-react';
import { formatPrice } from '../utils/formatters';

export default function DecisionHistory() {
  const [decisions, setDecisions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const fetchHistory = async () => {
      try {
        const res = await getRecentDecisions();
        if (!cancelled && res?.success) setDecisions(res.data || []);
      } catch (err) {
        console.error("Failed to fetch history", err);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    fetchHistory();
    return () => { cancelled = true; };
  }, []);

  const stats = useMemo(() => {
    if (decisions.length === 0) return null;
    const buys = decisions.filter(d => d?.action === 'BUY').length;
    const skips = decisions.filter(d => d?.action === 'SKIP').length;
    const total = decisions.length;
    const symbols = new Set(decisions.map(d => d?.symbol).filter(Boolean));
    const conviction = total > 0 ? Math.round((buys / total) * 100) : 0;

    // Most-touched symbol
    const counts = decisions.reduce((acc, d) => {
      if (!d?.symbol) return acc;
      acc[d.symbol] = (acc[d.symbol] || 0) + 1;
      return acc;
    }, {});
    const topSymbol = Object.entries(counts).sort((a, b) => b[1] - a[1])[0];

    // Cadence: median gap between consecutive decisions, in hours
    const stamps = decisions
      .map(d => new Date(d?.timestamp).getTime())
      .filter(t => !isNaN(t))
      .sort((a, b) => a - b);
    let medianGapHours = null;
    if (stamps.length >= 2) {
      const gaps = [];
      for (let i = 1; i < stamps.length; i++) gaps.push(stamps[i] - stamps[i - 1]);
      gaps.sort((a, b) => a - b);
      const mid = Math.floor(gaps.length / 2);
      medianGapHours = (gaps.length % 2 === 0 ? (gaps[mid - 1] + gaps[mid]) / 2 : gaps[mid]) / 3_600_000;
    }

    return { buys, skips, total, uniqueSymbols: symbols.size, conviction, topSymbol, medianGapHours };
  }, [decisions]);

  const formatGap = (h) => {
    if (h == null) return '--';
    if (h < 1) return `${Math.round(h * 60)}m`;
    if (h < 48) return `${h.toFixed(1)}h`;
    return `${(h / 24).toFixed(1)}d`;
  };

  return (
    <div className="max-w-6xl mx-auto p-6 sm:p-10 space-y-8">
      <header className="flex justify-between items-center">
        <div className="space-y-1">
          <h2 className="text-3xl font-black text-white tracking-tighter uppercase">Decision Log</h2>
          <p className="text-slate-500 text-xs font-bold uppercase tracking-widest flex items-center gap-2">
            <History size={12} /> Every BUY and SKIP you've recorded · most recent first
          </p>
        </div>
      </header>

      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-5 gap-px bg-white/5 border border-white/5 rounded-2xl overflow-hidden">
          <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
            <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Total</span>
            <span className="text-sm font-black text-white tabular-nums">{stats.total}</span>
          </div>
          <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
            <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Buys</span>
            <span className="text-sm font-black text-emerald-400 tabular-nums">{stats.buys}</span>
          </div>
          <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
            <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Skips</span>
            <span className="text-sm font-black text-slate-300 tabular-nums">{stats.skips}</span>
          </div>
          <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
            <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Symbols Touched</span>
            <span className="text-sm font-black text-white tabular-nums">{stats.uniqueSymbols}</span>
          </div>
          <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
            <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Median Cadence</span>
            <span className="text-sm font-black text-white tabular-nums">{formatGap(stats.medianGapHours)}</span>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-8 space-y-3">
          {loading ? (
            <div className="p-20 flex flex-col items-center justify-center gap-4 bg-white/5 rounded-3xl border border-dashed border-white/10">
              <div className="w-8 h-8 border-2 border-white/10 border-t-blue-500 rounded-full animate-spin" />
              <p className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Loading recent decisions</p>
            </div>
          ) : decisions.length > 0 ? (
            decisions.map((d, i) => (
              <motion.div
                key={d.id || i}
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: Math.min(i, 10) * 0.02 }}
                className="bg-slate-900/40 border border-white/5 px-5 py-4 rounded-2xl hover:bg-slate-900/60 transition-colors flex items-center justify-between"
              >
                <div className="flex items-center gap-4">
                  <div className={`w-10 h-10 rounded-xl flex items-center justify-center border ${d.action === 'BUY' ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-500' : 'bg-slate-500/10 border-slate-500/20 text-slate-400'}`}>
                    {d.action === 'BUY' ? <TrendingUp size={16} /> : <Activity size={16} />}
                  </div>
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-2">
                       <span className="text-base font-black text-white">{d.symbol}</span>
                       <span className={`text-[9px] font-black uppercase px-1.5 py-0.5 rounded ${d.action === 'BUY' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-slate-500/20 text-slate-400'}`}>{d.action}</span>
                    </div>
                    <p className="text-[10px] text-slate-500 font-medium flex items-center gap-1.5">
                       <Clock size={10} /> {new Date(d.timestamp).toLocaleString()}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                   <p className="text-sm font-black text-white tabular-nums">{formatPrice(d.price, d.market)}</p>
                   <p className="text-[9px] text-slate-600 font-bold uppercase tracking-widest">Price at decision</p>
                </div>
              </motion.div>
            ))
          ) : (
            <div className="p-20 text-center text-slate-500 font-bold text-xs uppercase tracking-[0.3em] border border-dashed border-white/5 rounded-3xl">
              No decisions logged yet — make your first call in the Arena.
            </div>
          )}
        </div>

        <div className="lg:col-span-4 space-y-4">
           <div className="bg-slate-900/40 border border-white/5 rounded-3xl p-6 space-y-4">
              <div className="flex items-center gap-2">
                 <Clock size={14} className="text-blue-400" />
                 <h3 className="text-[10px] font-black text-white uppercase tracking-widest">Pattern</h3>
              </div>
              {stats ? (
                <div className="space-y-3 text-sm">
                   <div className="flex justify-between items-baseline">
                      <span className="text-slate-500 text-xs">Conviction rate</span>
                      <span className="font-black text-white tabular-nums">{stats.conviction}%</span>
                   </div>
                   <div className="h-1 w-full bg-slate-800 rounded-full overflow-hidden">
                      <div className="h-full bg-blue-500" style={{ width: `${stats.conviction}%` }} />
                   </div>
                   <p className="text-[10px] text-slate-500 font-medium leading-relaxed pt-1">
                      Share of decisions where you acted (BUY) instead of skipping. Higher isn't better or worse — it reflects how decisive you are.
                   </p>
                   {stats.topSymbol && (
                      <div className="pt-3 border-t border-white/5 flex justify-between items-baseline">
                         <span className="text-slate-500 text-xs">Most analyzed</span>
                         <span className="font-black text-white">{stats.topSymbol[0]} <span className="text-slate-500 text-xs">· {stats.topSymbol[1]}×</span></span>
                      </div>
                   )}
                </div>
              ) : (
                <p className="text-[10px] text-slate-500 font-medium leading-relaxed">Stats unlock after your first decision.</p>
              )}
           </div>
        </div>
      </div>
    </div>
  );
}
