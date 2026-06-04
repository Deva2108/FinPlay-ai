import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  Search, User, Bell, TrendingUp, TrendingDown, Zap, Newspaper,
  Lightbulb, Quote, Info, ChevronRight, Loader2, X, Star,
  Activity, Target, Award, Shield, AlertTriangle, ArrowUpRight, HelpCircle,
  BrainCircuit, Sparkles, PlayCircle, Quote as QuoteIcon
} from 'lucide-react';
import GlassCard from '../components/GlassCard';
import FinPlayArena from '../components/FinPlayArena';
import LastMoveCard from '../components/GameMode/LastMoveCard';
import { useStockPanel } from '../context/StockPanelContext';
import { useMarket } from '../context/MarketContext';
import { searchStocks, getTutorialInsightResponse, getFamousInsights, getMarketPulse, API_ENDPOINTS, swrRead, swrWrite } from '../services/api';
import { useDebounce } from '../hooks/useDebounce';
import { formatPrice, safePct } from '../utils/formatters';
import { useTrading } from '../context/TradingContext';
import { useGuide } from '../hooks/useGuide';
import { useInsight } from '../hooks/useInsight';
import GuideOverlay from '../components/GuideOverlay';

import InsightPanel from '../components/InsightPanel';
import MarketInsightPanel from '../components/MarketInsightPanel';
import NextEdgeCard from '../components/NextEdgeCard';
import WidgetErrorBoundary from '../components/WidgetErrorBoundary';
import { FreshnessLabel, LiveFeedRotator } from '../components/Dashboard/LiveBits';
import IndicesWidget from '../components/Dashboard/IndicesWidget';
import MoversWidget from '../components/Dashboard/MoversWidget';
import WatchlistWidget from '../components/Dashboard/WatchlistWidget';

 
export default function Dashboard() {
  const { openStockPanel, recentlyViewed: allRecentlyViewed } = useStockPanel();
  const { marketMode, marketCode, setMarketMode } = useMarket();
  const { balance, recordDecision, decisions: allDecisions, activePortfolioId, missedOpportunities: allMissed, loading: portfolioLoading, error: portfolioError } = useTrading();

  // indices + trending state moved into <IndicesWidget> and <MoversWidget>.
  // Dashboard only receives the derived breadth counts it needs for the header bar.
  const [breadthCounts, setBreadthCounts] = useState({ up: 0, down: 0 });

  const vibeParams = useMemo(() => ({ marketType: marketCode === 'IN' ? 'INDIA' : 'US' }), [marketCode]);
  const { data: vibeData, status: vibeStatus } = useInsight(API_ENDPOINTS.MARKET.VIBE, vibeParams);
  const marketVibe = useMemo(() => vibeData?.simpleVibe || vibeData?.message || "Reading the tape — fresh sentiment in a moment.", [vibeData]);
  const loadingVibe = vibeStatus === 'SYNCING';

  const [pulseData, setMarketPulseData] = useState(null);
  const [loadingPulse, setLoadingPulse] = useState(false);
  const [lastRefresh, setLastRefresh] = useState(new Date());
  const [famousInsights, setFamousInsights] = useState([]);
  // watchlist state moved into <WatchlistWidget /> — no longer lives in Dashboard

  const [insightContent, setInsightContent] = useState(null);
  const [marketInsightData, setMarketInsightData] = useState(null);
  const [gameContext, setGameContext] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [lastDecision, setLastDecision] = useState(null);
  
  const debouncedSearch = useDebounce(searchQuery, 300);
  const navigate = useNavigate();
  const arenaRef = useRef(null);
  const dropdownRef = useRef(null);

  // Hybrid Tutorial Logic
  const { currentStep, nextStep, setStep } = useGuide(localStorage.getItem("finplay_tutorial_done") ? 99 : 1);
  const [aiTutorialInsight, setAiTutorialInsight] = useState("");
  
  const fetchTutorialInsight = async (stepNum) => {
    const topics = { 1: "investing mindset", 2: "market volatility", 3: "portfolio management", 4: "trading behavior" };
    const topic = topics[stepNum] || "wealth building";
    try {
      const res = await getTutorialInsightResponse(topic);
      setAiTutorialInsight(res?.data?.message || res?.message || "Tutorial content syncing...");
    } catch (err) {
      setAiTutorialInsight("Tutorial content syncing...");
    }
  };

  useEffect(() => {
    if (currentStep > 0 && currentStep < 6) {
      setAiTutorialInsight("");
      fetchTutorialInsight(currentStep);
    }
  }, [currentStep]);

  const handleFinishTutorial = () => {
    localStorage.setItem("finplay_tutorial_done", "true");
    setStep(99);
  };

  const fetchMarketData = async () => {
    // ── Instant paint: hydrate from SWR cache before any network call ──────
    // indices + trending are now seeded inside their own widgets.
    const cf = swrRead('famous');
    if (cf) setFamousInsights(cf);

    // ── Fire both requests in parallel — no request blocks another ─────────
    setLoadingPulse(true);
    const [famousRes, pulseRes] = await Promise.allSettled([
      getFamousInsights("ALL"),
      activePortfolioId ? getMarketPulse(activePortfolioId) : Promise.resolve(null)
    ]);

    // ── Hydrate independently — one failure cannot blank adjacent widgets ───
    if (famousRes.status === 'fulfilled') {
      const d = famousRes.value?.data;
      if (Array.isArray(d)) { setFamousInsights(d); swrWrite('famous', d); }
    }

    if (pulseRes.status === 'fulfilled' && pulseRes.value?.data) {
      setMarketPulseData(pulseRes.value.data);
    }
    setLoadingPulse(false);
    setLastRefresh(new Date());
  };

  useEffect(() => {
    fetchMarketData();
    const heavyPoll = setInterval(() => fetchMarketData(), 1800000); // 30 min: full refresh
    return () => clearInterval(heavyPoll);
  }, [marketMode, activePortfolioId, portfolioLoading]);

  // Flash detector: MoversWidget reports breadth via onBreadthChange → setBreadthCounts.
  // When counts shift, briefly highlight the breadth bar in the header.
  const prevBreadthRef = useRef({ up: 0, down: 0 });
  const [breadthFlash, setBreadthFlash] = useState(null);
  useEffect(() => {
    const { up, down } = breadthCounts;
    const prev = prevBreadthRef.current;
    if (prev.up !== 0 || prev.down !== 0) {
      if (up > prev.up) setBreadthFlash('up');
      else if (down > prev.down) setBreadthFlash('down');
    }
    prevBreadthRef.current = { up, down };
    if (breadthFlash) {
      const t = setTimeout(() => setBreadthFlash(null), 800);
      return () => clearTimeout(t);
    }
  }, [breadthCounts]);

  const recentlyViewed = useMemo(() => (allRecentlyViewed || []).filter(s => s?.market === marketCode), [allRecentlyViewed, marketCode]);
  const decisions = useMemo(() => (allDecisions || []).filter(d => d?.stock && (d?.stock?.currency === (marketCode === 'IN' ? 'INR' : 'INR'))), [allDecisions, marketCode]);
  const missedOpportunities = useMemo(() => (allMissed || []).filter(d => d?.action === 'SKIP').slice(0, 3), [allMissed, marketCode]);

  const objectives = useMemo(() => [
    { title: "Daily Arena Clear", progress: (decisions?.length || 0) >= 5 ? 100 : ((decisions?.length || 0) / 5) * 100, icon: <Target size={12}/> },
    { title: "Market Analysis", progress: (recentlyViewed?.length || 0) >= 3 ? 100 : ((recentlyViewed?.length || 0) / 3) * 100, icon: <Zap size={12}/> }
  ], [decisions, recentlyViewed]);


  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) setShowDropdown(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    if (debouncedSearch.trim().length > 1) performSearch();
    else { setSearchResults([]); setIsSearching(false); }
  }, [debouncedSearch]);

  const performSearch = async () => {
    setIsSearching(true);
    setShowDropdown(true);
    try {
      const res = await searchStocks(debouncedSearch);
      setSearchResults(res?.data || []);
      setIsSearching(!!res?.syncing);
    } catch (err) {
      setSearchResults([]);
      setIsSearching(false);
    }
  };

  const handleSearchKeyDown = (e) => {
    if (e.key === 'Enter' && searchQuery?.trim()) {
      navigate(`/stock/${(searchQuery || "").toUpperCase().trim()}`);
      setShowDropdown(false);
    }
  };

  const handleResultClick = (symbol) => {
    navigate(`/stock/${symbol}`);
    setShowDropdown(false);
    setSearchQuery("");
  };

  const accentColor = marketMode === "INDIA" ? "#FF9933" : "#3B82F6";
  const marketTone = marketMode === "INDIA" ? "bg-orange-500/5" : "bg-blue-500/5";


  const handleIndexClick = (idx) => setMarketInsightData(idx);
  const handleTryGameFromIndex = (indexData) => {
    setGameContext(indexData?.symbol);
    arenaRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  return (
    <>
    <InsightPanel isOpen={!!insightContent} onClose={() => setInsightContent(null)} content={insightContent} />
    <MarketInsightPanel isOpen={!!marketInsightData} onClose={() => setMarketInsightData(null)} indexData={marketInsightData} onTryGame={handleTryGameFromIndex} />
    <div className={`transition-colors duration-500 min-h-full w-full pb-20 ${marketTone}`}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 md:px-8 py-6 sm:py-8 space-y-8">

        {/* Degraded-state banner (CHANGE 4): if the portfolio sync failed, the
            dashboard still renders — we just say data is limited and retrying,
            instead of silently showing defaults or a blank screen. */}
        {portfolioError && !portfolioLoading && (
          <div className="bg-amber-500/10 border border-amber-500/20 text-amber-300 rounded-2xl px-5 py-3 text-xs font-bold">
            Showing limited data — we couldn't fully sync your portfolio. Retrying in the background.
          </div>
        )}

        <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} className="bg-slate-900/50 border border-white/5 backdrop-blur-xl rounded-2xl px-5 py-4 flex flex-col lg:flex-row lg:items-center gap-4 lg:gap-6">
          <div className="flex items-center gap-3 shrink-0">
            <div className={`w-2 h-2 rounded-full ${loadingPulse ? 'bg-amber-500 animate-pulse' : 'bg-emerald-500 animate-pulse'}`} />
            <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">{loadingPulse ? 'Syncing' : 'Live'}</span>
            <FreshnessLabel since={lastRefresh} />
            <span className="hidden lg:block w-px h-4 bg-white/10" />
          </div>
          <div className="flex items-center gap-2 flex-1 min-w-0">
            <BrainCircuit size={14} className="text-indigo-400 shrink-0" />
            {loadingVibe ? (
              <div className="h-3 w-2/3 bg-white/5 animate-pulse rounded-full" />
            ) : (
              <p className="text-sm font-semibold text-slate-200 leading-snug truncate">{marketVibe}</p>
            )}
          </div>
          {(() => {
            const { up, down } = breadthCounts;
            const total = up + down;
            const breadth = total > 0 ? Math.round((up / total) * 100) : null;
            return (
              <div className={`flex items-center gap-4 shrink-0 transition-colors duration-500 ${breadthFlash === 'up' ? 'bg-emerald-500/10 -mx-2 px-2 py-1 rounded-lg' : breadthFlash === 'down' ? 'bg-rose-500/10 -mx-2 px-2 py-1 rounded-lg' : ''}`}>
                <div className="flex items-center gap-1.5"><TrendingUp size={12} className="text-emerald-500" /><span className="text-xs font-bold text-emerald-400 tabular-nums">{up}</span></div>
                <div className="flex items-center gap-1.5"><TrendingDown size={12} className="text-rose-500" /><span className="text-xs font-bold text-rose-400 tabular-nums">{down}</span></div>
                {breadth !== null && (
                  <div className="hidden md:flex items-center gap-2">
                    <div className="w-20 h-1 bg-rose-500/30 rounded-full overflow-hidden"><div className="h-full bg-emerald-500 transition-all duration-500" style={{ width: `${breadth}%` }} /></div>
                    <span className="text-[10px] font-black text-slate-400 tabular-nums w-8">{breadth}%</span>
                  </div>
                )}
              </div>
            );
          })()}
        </motion.div>

        {!pulseData && loadingPulse && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 animate-pulse">
            <div className="lg:col-span-8 bg-slate-900/40 border border-white/5 rounded-[2.5rem] p-8 space-y-4">
              <div className="h-5 w-48 bg-white/5 rounded-full" />
              <div className="grid grid-cols-2 gap-4">
                {[0,1,2,3].map(i => <div key={i} className="h-28 bg-white/5 rounded-2xl" />)}
              </div>
            </div>
            <div className="lg:col-span-4 bg-slate-900/40 border border-white/5 rounded-[2.5rem] p-8 space-y-4">
              <div className="h-4 w-32 bg-white/5 rounded-full" />
              {[0,1,2].map(i => <div key={i} className="h-8 bg-white/5 rounded-xl" />)}
            </div>
          </div>
        )}

        {pulseData && (
           <motion.section initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="grid grid-cols-1 lg:grid-cols-12 gap-6">
              <div className="lg:col-span-8 bg-slate-900/40 border border-white/5 rounded-[2.5rem] p-8 space-y-6">
                 <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3"><div className="p-2 bg-blue-600 rounded-xl shadow-lg shadow-blue-600/20"><Newspaper size={18} className="text-white"/></div><h3 className="text-sm font-black text-white uppercase tracking-widest">Contextual Market Pulse</h3></div>
                 </div>
                 <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {(pulseData.news || []).slice(0, 4).map((n, i) => (
                      <a key={i} href={n.url} target="_blank" rel="noopener noreferrer" className={`p-5 rounded-2xl border transition-all hover:scale-[1.02] group ${n.isRisk ? 'bg-rose-500/5 border-rose-500/10 hover:border-rose-500/30' : n.isOpportunity ? 'bg-emerald-500/5 border-emerald-500/10 hover:border-emerald-500/30' : 'bg-white/5 border-white/5 hover:border-white/20'}`}>
                         <div className="flex justify-between items-start mb-3">
                            <span className={`text-[8px] font-black uppercase px-2 py-0.5 rounded-md ${n.isRisk ? 'bg-rose-500/20 text-rose-500' : n.isOpportunity ? 'bg-emerald-500/20 text-emerald-500' : 'bg-blue-500/20 text-blue-500'}`}>
                               {n.isRisk ? 'Risk' : n.isOpportunity ? 'Opportunity' : 'Neutral'}
                            </span>
                            <ArrowUpRight size={14} className="text-slate-600 group-hover:text-white transition-colors" />
                         </div>
                         <p className="text-xs font-bold text-slate-200 line-clamp-2 leading-relaxed mb-4 group-hover:text-white">{n.headline}</p>
                         <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">{n.source}</span>
                      </a>
                    ))}
                 </div>
              </div>
              <div className="lg:col-span-4 bg-gradient-to-br from-indigo-950/40 to-slate-900/60 border border-indigo-500/20 rounded-[2.5rem] p-8 flex flex-col justify-center space-y-6 relative overflow-hidden">
                 <div className="absolute top-0 right-0 p-8 opacity-5"><BrainCircuit size={150} className="text-indigo-400" /></div>
                 <div className="relative z-10 space-y-4">
                    <div className="flex items-center gap-2 text-indigo-400"><Zap size={16} className="fill-indigo-400" /><span className="text-[10px] font-black uppercase tracking-[0.2em]">Strategic AI Insights</span></div>
                    <div className="space-y-4">
                       {(typeof pulseData.insights === "string" ? pulseData.insights : pulseData.insights?.text || "").split('\n').filter(line => line.trim()).map((line, i) => (
                         <div key={i} className="flex gap-4 group"><div className="w-1.5 h-1.5 rounded-full bg-indigo-500 mt-2 shrink-0 group-hover:scale-150 transition-transform" /><p className="text-sm font-medium text-slate-300 leading-relaxed">{line.replace(/^[*-]\s*/, '')}</p></div>
                       ))}
                    </div>
                 </div>
              </div>
           </motion.section>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-center">
           <div className="lg:col-span-6">
              <section className="relative w-full" ref={dropdownRef}>
                <div className="relative group">
                  <div className="absolute inset-0 bg-gradient-to-r from-blue-500/10 to-purple-500/10 rounded-3xl blur-2xl opacity-50" />
                  <div className="relative flex items-center bg-slate-900/80 border border-slate-800 rounded-2xl p-1 focus-within:border-blue-500/50 transition-all shadow-xl">
                    <div className="flex items-center justify-center pl-4 pr-2 text-slate-400">{isSearching ? <Loader2 size={18} className="animate-spin text-blue-500" /> : <Search size={18} />}</div>
                    <input type="text" value={searchQuery} onChange={(e) => { setSearchQuery(e.target.value); setShowDropdown(true); }} onKeyDown={handleSearchKeyDown} placeholder={`Search ${marketMode === 'INDIA' ? 'NSE/BSE' : 'US (Nasdaq/NYSE)'} Stocks...`} className="w-full bg-transparent py-3 text-sm font-bold text-white placeholder-slate-500 focus:outline-none" />
                  </div>
                </div>
                {showDropdown && (searchQuery.length > 1) && (isSearching || searchResults.length > 0) && (
                  <div className="absolute top-full left-0 right-0 mt-2 z-50">
                    <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl max-h-[400px] overflow-y-auto">
                      {isSearching ? <div className="p-8 text-center text-slate-500 text-[10px] font-black uppercase animate-pulse">Scanning tickers...</div> : (
                        <div className="py-2">
                          {(searchResults || []).map((result, idx) => (
                            <div key={idx} onClick={() => handleResultClick(result.symbol)} className="px-5 py-3 flex items-center justify-between hover:bg-white/5 cursor-pointer group border-b border-white/[0.03] last:border-0">
                              <div className="flex items-center gap-3">
                                <div className="w-10 h-10 rounded-xl bg-slate-800 flex items-center justify-center font-black text-blue-500 text-xs group-hover:bg-blue-600 group-hover:text-white transition-all">{(result.symbol || "")[0]}</div>
                                <div><h5 className="text-sm font-black text-white group-hover:text-blue-400">{result.symbol}</h5><p className="text-[10px] text-slate-500 font-bold uppercase truncate max-w-[200px]">{result.name}</p></div>
                              </div>
                              <span className="text-[9px] font-black text-slate-500 bg-white/5 px-2 py-1 rounded-lg">{result.stockExchange || ((result.symbol || "").includes('.') ? 'INDIA' : 'US')}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </section>
           </div>
           <div className="lg:col-span-6">
              <IndicesWidget marketMode={marketMode} onIndexClick={handleIndexClick} />
           </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          <div className="lg:col-span-8 space-y-6" ref={arenaRef}>
            <section className="relative">
              <FinPlayArena marketMode={marketMode} context={gameContext} onDecisionMade={(d) => { setLastDecision(d); recordDecision(d.action, d.stock, marketCode); }} onShowInsight={setInsightContent} />
              <div className="space-y-4 pt-4">
                <div className="flex items-center justify-between px-2">
                  <div className="flex items-center gap-3"><div className="p-2 bg-rose-600 rounded-xl shadow-lg shadow-rose-600/20"><PlayCircle size={16} className="text-white" /></div><h2 className="text-[10px] font-black text-white uppercase tracking-widest italic">The Investor's Library</h2></div>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {(Array.isArray(famousInsights) ? famousInsights : []).slice(0, 2).map((insight, i) => (
                    <motion.div key={i} whileHover={{ y: -5, scale: 1.01 }} onClick={() => window.open(insight.podcastUrl, '_blank')} className="bg-slate-900/40 border border-white/5 p-6 rounded-[2rem] relative overflow-hidden group cursor-pointer shadow-xl backdrop-blur-md">
                      <div className="absolute top-0 right-0 p-6 opacity-5 group-hover:opacity-10 transition-opacity"><QuoteIcon size={60} className="text-white" /></div>
                      <div className="relative z-10 flex gap-6 items-center">
                        <div className="w-12 h-12 rounded-xl bg-rose-600/10 flex items-center justify-center border border-rose-500/20 shadow-inner group-hover:bg-rose-600/20 transition-colors"><PlayCircle size={24} className="text-rose-500" /></div>
                        <div className="space-y-1 flex-1">
                          <p className="text-[9px] font-black text-rose-500 uppercase tracking-[0.2em]">{insight.investor}</p>
                          <h4 className="text-sm font-bold text-white line-clamp-1 group-hover:text-blue-400 transition-colors">{insight.title}</h4>
                          <p className="text-[11px] text-slate-400 italic line-clamp-1 leading-relaxed">"{insight.message}"</p>
                        </div>
                      </div>
                    </motion.div>
                  ))}
                </div>
              </div>
            </section>
            <section className="space-y-4">
                <div className="flex items-center gap-2 px-2">{recentlyViewed.length > 0 ? <Activity size={16} className="text-blue-500" /> : <Zap size={16} className="text-purple-500" />}<h3 className="text-[10px] font-black text-white uppercase tracking-widest">{recentlyViewed.length > 0 ? "Recently Viewed" : "Your Next Move"}</h3></div>
                <WidgetErrorBoundary name="Recently Viewed">
                {recentlyViewed.length > 0 ? (
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    {((recentlyViewed || []).slice(0, 4)).map((stock, i) => (
                      <div key={i} onClick={() => openStockPanel(stock)} className="p-4 rounded-2xl border border-slate-800 bg-slate-900/60 hover:bg-slate-800 transition-all cursor-pointer group shadow-lg">
                        <div className="flex justify-between items-start mb-2"><span className="text-white font-black text-xs group-hover:text-blue-400">{stock?.symbol}</span><ArrowUpRight size={12} className="text-slate-600 group-hover:text-blue-400" /></div>
                        <p className="text-sm font-black text-white">{formatPrice(stock?.price, stock?.currency || 'INR')}</p>
                      </div>
                    ))}
                  </div>
                ) : ( <NextEdgeCard /> )}
                </WidgetErrorBoundary>
            </section>
          </div>

          <div className="lg:col-span-4 space-y-6">
            <MoversWidget openStockPanel={openStockPanel} onBreadthChange={setBreadthCounts} />
            {/* WatchlistWidget owns its own state + 60s quote poll.
                Isolated from Dashboard so price updates never re-render siblings. */}
            <WatchlistWidget />
            <AnimatePresence mode="wait" initial={false}>
              {lastDecision ? ( <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: 0.95 }}><LastMoveCard decision={lastDecision} onNext={() => setLastDecision(null)} /></motion.div>
              ) : (
                <div className="bg-gradient-to-br from-blue-600 to-blue-400 p-6 rounded-3xl shadow-xl shadow-blue-500/20 relative overflow-hidden group">
                   <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:scale-110 transition-transform"><Lightbulb size={100} /></div>
                   <div className="relative z-10 space-y-3">
                      <span className="text-[10px] font-black bg-white/20 text-white px-2 py-1 rounded-lg uppercase">Daily Alpha</span>
                      <h3 className="text-xl font-black text-white leading-tight">Master the <br/>Decision Loop</h3>
                      <button onClick={() => navigate('/portfolio')} className="w-full py-3 bg-white text-blue-600 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-blue-50 transition-colors">Go to Portfolio</button>
                   </div>
                </div>
              )}
            </AnimatePresence>
            <section className="bg-slate-900/40 border border-slate-800 p-5 rounded-3xl space-y-5">
               <div className="flex items-center gap-2"><Target size={14} className="text-blue-500" /><h3 className="text-[10px] font-black text-white uppercase tracking-widest">Active Objectives</h3></div>
               <div className="space-y-4">
                  {objectives.map((obj, i) => (
                    <div key={i} className="space-y-1.5">
                       <div className="flex justify-between text-[9px] font-bold"><span className="text-slate-400 flex items-center gap-2">{obj?.icon} {obj?.title}</span><span className="text-blue-400">{safePct(obj?.progress, 0)}%</span></div>
                       <div className="h-1 w-full bg-slate-800 rounded-full overflow-hidden"><div className="h-full bg-blue-500 transition-all duration-1000" style={{ width: `${obj?.progress}%` }} /></div>
                    </div>
                  ))}
               </div>
            </section>
            {missedOpportunities.length > 0 && (
               <section className="bg-slate-900/40 border border-slate-800 p-5 rounded-3xl space-y-4">
                  <div className="flex items-center gap-2"><AlertTriangle size={14} className="text-rose-500" /><h3 className="text-[10px] font-black text-white uppercase tracking-widest">Missed Growth</h3></div>
                  <div className="space-y-3">
                     {missedOpportunities.map((m, i) => (
                       <div key={i} onClick={() => setInsightContent({ type: 'game', title: `Missed: ${m?.stock?.symbol}`, explanation: `You skipped ${m?.stock?.symbol} recently.`, insight: `Breakouts often follow consolidation.`, actions: [{ label: 'View Deep Dive', primary: true, onClick: () => navigate(`/stock/${m?.stock?.symbol}`) }] })} className="flex items-center justify-between p-3 bg-rose-500/5 border border-rose-500/10 rounded-xl group hover:bg-rose-500/10 transition-colors cursor-pointer">
                          <div className="flex items-center gap-3"><div className="w-8 h-8 rounded-lg bg-rose-500/10 flex items-center justify-center font-black text-rose-500 text-[10px]">{(m?.stock?.symbol || "")[0]}</div><div><p className="text-[10px] font-black text-white group-hover:text-rose-400 transition-colors">{m?.stock?.symbol}</p></div></div>
                          <TrendingUp size={12} className="text-rose-400 group-hover:scale-110 transition-transform" />
                       </div>
                     ))}
                  </div>
               </section>
            )}
            <LiveFeedRotator newsItems={pulseData?.news} />
          </div>
        </div>
      </div>
    </div>
    <GuideOverlay step={currentStep} totalSteps={5} active={currentStep <= 5} onDismiss={handleFinishTutorial} title={currentStep === 1 ? "Welcome to FinPlay 🚀" : "The Market Floor 📊"} content={aiTutorialInsight} onNext={currentStep === 5 ? handleFinishTutorial : nextStep} />
    </>
  );
}
