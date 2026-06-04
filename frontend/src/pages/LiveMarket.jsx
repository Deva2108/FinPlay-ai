import { useState, useMemo, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, TrendingDown, Activity, Bot, Sparkles, Loader2, Info, ArrowRight, MousePointer2, Zap, ArrowLeft, Building2 } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useMarket } from '../context/MarketContext';
import { getChartData, getStockDetails, getLiveQuotes, swrRead, swrWrite } from '../services/api';

// Evaluated once — Framer Motion whileHover fires on touch via synthetic pointer
// events, triggering x-transform animations during scroll and causing GPU repaint
// churn. Gate to hover-capable devices only.
const CAN_HOVER = typeof window !== 'undefined' && window.matchMedia('(hover: hover) and (pointer: fine)').matches;
import ChartComponent from '../components/ChartComponent';
import InfoTooltip from '../components/InfoTooltip';

export default function LiveMarket() {
  const { marketMode, currencySymbol } = useMarket();
  const navigate = useNavigate();
  const location = useLocation();
  
  // Default to a provider-covered equity (indices like ^NSEI/SPY are poorly
  // covered by free-tier quote providers, so they render "--" on a cold mirror).
  const defaultIndex = marketMode === 'INDIA' ? 'RELIANCE.NS' : 'AAPL';
  const [symbol, setSymbol] = useState(location.state?.selectedStock || defaultIndex);
  const [details, setDetails] = useState(null);
  const [chartData, setChartData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isSyncing, setIsSyncing] = useState(false);
  const [loadingChart, setLoadingChart] = useState(false);
  const [timeframe, setTimeframe] = useState('1M');
  const [constituents, setConstituents] = useState([]);
  // Live current point (FIX 2) + backend period OHLC (FIX 3). chartData stays
  // purely HISTORICAL so the SWR cache and the FIX 4 "<2 points" gate stay honest.
  const [currentPoint, setCurrentPoint] = useState(null);
  const [ohlc, setOhlc] = useState(null);

  // Fetch constituents based on market
  const constituentsList = useMemo(() => {
    return marketMode === 'INDIA' 
      ? ['RELIANCE.NS', 'TCS.NS', 'HDFCBANK.NS', 'ICICIBANK.NS', 'INFY.NS', 'BHARTIARTL.NS', 'SBIN.NS', 'LICI.NS', 'ITC.NS', 'HINDUNILVR.NS']
      : ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META', 'TSLA', 'BRK.B', 'LLY', 'AVGO'];
  }, [marketMode]);

  const fetchData = async () => {
    setLoading(true);
    setIsSyncing(false);
    try {
      const [detailsRes, quotesRes] = await Promise.all([
        getStockDetails(symbol),
        getLiveQuotes(constituentsList)
      ]);

      // Read the backend sync signal from the envelope meta (status === 'SYNCING'),
      // not a non-existent `detailsRes.syncing` field. Prefer live data when present;
      // otherwise surface an honest syncing state instead of a bare "--".
      if (detailsRes?.data) {
        setDetails(detailsRes.data);
        swrWrite(`lm_det_${symbol}`, detailsRes.data);
      } else if (detailsRes?.meta?.status === 'SYNCING' || !detailsRes?.success) {
        setIsSyncing(true);
      }

      const quotes = quotesRes?.data || [];
      setConstituents(quotes);
      if (quotes.length > 0) swrWrite(`lm_quotes_${marketMode}`, quotes);
    } catch (err) {
      console.error("Failed to load market hub", err);
    } finally {
      setLoading(false);
    }
  };

  const fetchChart = async () => {
    setLoadingChart(true);
    try {
      const res = await getChartData(symbol, timeframe);
      // Historical points only — cached and used for the FIX 4 point-count gate.
      const pts = res?.data?.data || [];
      setChartData(pts);
      // FIX 2: live point appended visually (not cached, not part of history).
      setCurrentPoint(res?.data?.currentPoint || null);
      // FIX 3: OHLC sourced from backend, not derived on the client.
      setOhlc(res?.data
        ? { open: res.data.open, high: res.data.high, low: res.data.low, close: res.data.close }
        : null);
      if (pts.length > 0) swrWrite(`lm_chart_${symbol}_${timeframe}`, pts);
    } catch (err) {
      setChartData([]);
      setCurrentPoint(null);
      setOhlc(null);
    } finally {
      setLoadingChart(false);
    }
  };

  // On symbol / market change: seed from SWR cache first (instant paint),
  // then fetch fresh data in the background.
  useEffect(() => {
    const cachedDet = swrRead(`lm_det_${symbol}`);
    if (cachedDet) { setDetails(cachedDet); setLoading(false); }
    const cachedQuotes = swrRead(`lm_quotes_${marketMode}`);
    if (cachedQuotes) setConstituents(cachedQuotes);
    fetchData();
  }, [symbol, marketMode]);

  // On timeframe change: seed chart from cache, then refetch.
  useEffect(() => {
    const cachedChart = swrRead(`lm_chart_${symbol}_${timeframe}`);
    if (cachedChart && cachedChart.length > 0) setChartData(cachedChart);
    fetchChart();
  }, [symbol, timeframe]);

  const isUp = (details?.changesPercentage ?? details?.change ?? 0) >= 0;
  const isIndex = symbol?.startsWith('^') || symbol === 'SPY' || symbol === 'QQQ' || symbol === 'DIA';

  // FIX 3 — OHLC comes from the backend (computed server-side from real daily
  // closes), no longer derived on the client. range/drift/position are simple
  // derivations of that authoritative OHLC. Null when the backend supplied none.
  const session = useMemo(() => {
    const num = (v) => (v === null || v === undefined || v === '' || isNaN(Number(v))) ? null : Number(v);
    const open = num(ohlc?.open), high = num(ohlc?.high), low = num(ohlc?.low), close = num(ohlc?.close);
    if (open === null || high === null || low === null || close === null) return null;
    const range = high - low;
    const driftPct = open ? ((close - open) / open) * 100 : 0;
    const position = range > 0 ? ((close - low) / range) * 100 : 50;
    return { open, close, high, low, range, driftPct, position };
  }, [ohlc]);

  // FIX 2 — extend the historical series with the live current point so the
  // chart's latest visible point equals the header price. Visual only: the
  // historical `chartData` state (and its SWR cache) is never mutated.
  const chartSeries = useMemo(
    () => (currentPoint ? [...chartData, currentPoint] : chartData),
    [chartData, currentPoint]
  );

  const fmt = (v) => {
    if (v == null || isNaN(v)) return '--';
    if (isIndex) return Number(v).toLocaleString(undefined, { maximumFractionDigits: 2 });
    return `${currencySymbol}${Number(v).toLocaleString(undefined, { maximumFractionDigits: 2 })}`;
  };

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="max-w-6xl mx-auto p-6 sm:p-10 space-y-6 pb-32">

      {/* Header & Symbol Toggle */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-6">
        <div className="space-y-1">
          <div className="flex items-center gap-4">
            <button onClick={() => setSymbol(defaultIndex)} className={`px-4 py-2 rounded-2xl text-[10px] font-black uppercase tracking-widest transition-all ${symbol === defaultIndex ? 'bg-blue-600 text-white shadow-lg' : 'bg-white/5 text-slate-500 hover:text-white'}`}>
              Global Index
            </button>
            <h1 className="text-4xl font-black text-white tracking-tighter uppercase flex items-center gap-2">
              {symbol.startsWith('^') ? (symbol === '^NSEI' ? 'NIFTY 50' : symbol === '^BSESN' ? 'SENSEX' : symbol) : symbol === 'SPY' ? 'S&P 500' : symbol}
              <InfoTooltip concept="index" />
            </h1>
          </div>
          {isSyncing ? (
            <p className="text-sm font-black text-slate-500 uppercase tracking-widest animate-pulse">Syncing market data…</p>
          ) : (
            <p className="text-2xl font-black text-white flex items-center gap-3">
              {isIndex ? (details?.price ?? '--') : `${currencySymbol}${details?.price ?? '--'}`}
              <span className={`text-sm font-black ${isUp ? 'text-emerald-500' : 'text-rose-500'}`}>
                {isUp ? '+' : ''}{details?.changesPercentage ?? details?.change ?? '--'}%
              </span>
            </p>
          )}
        </div>

        <div className="flex gap-1 bg-white/5 p-1 rounded-2xl border border-white/5">
          {['1D', '1W', '1M', '1Y', '5Y'].map((tf) => (
            <button
              key={tf}
              onClick={() => setTimeframe(tf)}
              className={`px-6 py-2 rounded-xl text-xs font-black transition-all ${timeframe === tf ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'}`}
            >
              {tf}
            </button>
          ))}
        </div>
      </div>

      {/* Metric Strip — real session + fundamentals, computed/sourced */}
      <div className="grid grid-cols-2 md:grid-cols-6 gap-px bg-white/5 border border-white/5 rounded-2xl overflow-hidden">
        {[
          { label: 'Open', value: fmt(session?.open) },
          { label: 'High', value: fmt(session?.high), tone: 'text-emerald-400' },
          { label: 'Low', value: fmt(session?.low), tone: 'text-rose-400' },
          { label: 'Range', value: session ? fmt(session.range) : '--' },
          { label: isIndex ? '52W Hi' : 'Mkt Cap', value: isIndex ? fmt(details?.high52) : (details?.marketCap || '--') },
          { label: isIndex ? '52W Lo' : 'P/E', value: isIndex ? fmt(details?.low52) : (details?.peRatio || '--') }
        ].map((m, i) => (
          <div key={i} className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
            <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">{m.label}</span>
            <span className={`text-sm font-black tabular-nums tracking-tight ${m.tone || 'text-white'}`}>{m.value}</span>
          </div>
        ))}
      </div>

      {/* Session position bar — where price sits in the day's range */}
      {session && session.range > 0 && (
        <div className="bg-slate-900/40 border border-white/5 rounded-2xl px-5 py-3 flex items-center gap-4">
          <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest shrink-0">Range Position</span>
          <div className="relative flex-1 h-1.5 bg-white/5 rounded-full overflow-hidden">
            <div className="absolute inset-y-0 left-0 bg-gradient-to-r from-rose-500/40 via-amber-500/40 to-emerald-500/40 w-full" />
            <div className="absolute top-1/2 -translate-y-1/2 w-2.5 h-2.5 rounded-full bg-white shadow-lg ring-2 ring-slate-900" style={{ left: `calc(${session.position}% - 5px)` }} />
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <span className={`text-xs font-black tabular-nums ${session.driftPct >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{session.driftPct >= 0 ? '+' : ''}{session.driftPct.toFixed(2)}%</span>
            <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">{timeframe} drift</span>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Main Chart Section */}
        <div className="lg:col-span-8 space-y-6">
          <div className="p-5 rounded-3xl bg-slate-900/40 border border-white/5 relative overflow-hidden">
            <div className="flex items-center justify-between mb-3 px-2">
               <p className="text-[10px] font-black text-blue-400 uppercase tracking-widest flex items-center gap-2">
                 <Activity size={12} /> Price Action · {timeframe}
               </p>
               {session && (
                 <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest tabular-nums">{chartData.length} pts</p>
               )}
            </div>

            <div className="h-[350px] w-full relative">
              {loadingChart && (
                <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 bg-[#020617]/30 backdrop-blur-sm rounded-2xl">
                  <Zap size={20} className="text-blue-500 animate-spin" />
                  <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest animate-pulse">Loading {timeframe} history</span>
                </div>
              )}
              {chartData.length < 2 ? (
                /* FIX 4 — a single daily point is not a chart. Never draw a fake
                   line; say so honestly instead. (Daily history = 1 row/day, so
                   1D structurally always lands here.) */
                <div className="h-full w-full flex flex-col items-center justify-center gap-2 bg-slate-900/40 rounded-3xl border border-white/5">
                  <Activity size={18} className="text-slate-600" />
                  <p className="text-slate-400 font-black uppercase text-[10px] tracking-[0.2em]">
                    {timeframe === '1D' ? 'Intraday coming soon' : 'Not enough history yet'}
                  </p>
                  <p className="text-slate-600 text-[9px] font-medium">
                    {timeframe === '1D' ? 'Daily data captures one close per day' : 'Try a longer timeframe'}
                  </p>
                </div>
              ) : (
                <ChartComponent
                  data={chartSeries}
                  color={isUp ? '#10b981' : '#f43f5e'}
                  height={350}
                  isIndex={isIndex}
                />
              )}
            </div>
          </div>

          {/* AI Mentor Context — compact, sourced */}
          <div className="p-5 rounded-3xl bg-indigo-600/5 border border-indigo-500/20 flex gap-4">
             <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center shrink-0">
                <Bot size={18} className="text-indigo-400" />
             </div>
             <div className="space-y-1.5 flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-[10px] font-black text-indigo-400 uppercase tracking-widest">Market Mentor</span>
                  <span className="text-[9px] font-black text-slate-600 uppercase tracking-widest">AI</span>
                </div>
                <p className="text-sm font-medium text-slate-200 leading-relaxed">
                  {details?.mentorLine || (session ? `${isUp ? 'Up' : 'Down'} ${Math.abs(session.driftPct).toFixed(2)}% on the ${timeframe} chart, sitting ${session.position.toFixed(0)}% into today's range. Watch for follow-through near the ${isUp ? 'high' : 'low'}.` : "Mentor commentary will appear once the chart loads.")}
                </p>
             </div>
          </div>
        </div>

        {/* Constituents List */}
        <div className="lg:col-span-4 space-y-6">
          <div className="flex items-center justify-between px-2">
            <h3 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
              <Building2 size={12} /> Top Companies
            </h3>
          </div>

          <div className="space-y-3 max-h-[600px] overflow-y-auto pr-2 custom-scrollbar">
            {constituents.map((stock) => (
              <motion.div
                key={stock.symbol}
                whileHover={CAN_HOVER ? { x: 5 } : undefined}
                onClick={() => navigate(`/stock/${stock.symbol}`)}
                className="p-4 rounded-2xl bg-white/5 border border-white/5 flex items-center justify-between cursor-pointer hover:bg-white/10 transition-all group"
              >
                <div className="flex items-center gap-3">
                  <div className={`w-1 h-8 rounded-full ${stock.changesPercentage >= 0 ? 'bg-emerald-500' : 'bg-rose-500'}`} />
                  <div>
                    <div className="flex items-center gap-1">
                      <p className="text-sm font-black text-white tracking-tight">{stock.symbol}</p>
                      <InfoTooltip concept="moat" />
                    </div>
                    <p className="text-[9px] font-bold text-slate-500 uppercase truncate max-w-[120px]">{stock.name}</p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm font-black text-white tracking-tighter">{currencySymbol}{stock.price ?? '--'}</p>
                  <p className={`text-[10px] font-black ${stock.changesPercentage >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>
                    {stock.changesPercentage >= 0 ? '+' : ''}{stock.changesPercentage ?? '--'}%
                  </p>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </motion.div>
  );
}
