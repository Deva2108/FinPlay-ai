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
import { searchStocks, getIndices, getTrending, getMarketVibe, getFamousInsights, getMarketPulse, api, API_ENDPOINTS } from '../services/api';
import { useDebounce } from '../hooks/useDebounce';
import { formatPrice } from '../utils/formatters';
import { useTrading } from '../context/TradingContext';
import { useGuide } from '../hooks/useGuide';
import GuideOverlay from '../components/GuideOverlay';

import InsightPanel from '../components/InsightPanel';
import IndexCard from '../components/IndexCard';
import MarketInsightPanel from '../components/MarketInsightPanel';
import NextEdgeCard from '../components/NextEdgeCard';

 
export default function Dashboard() {
  const { openStockPanel, recentlyViewed: allRecentlyViewed } = useStockPanel();
  const { marketMode, marketCode, setMarketMode } = useMarket();
  const { balance, recordDecision, decisions: allDecisions } = useTrading();

  const [indices, setIndices] = useState([]);
  const [trending, setTrending] = useState([]);
  const [loadingMarket, setLoadingMarket] = useState(true);
  
  const [marketVibe, setMarketVibe] = useState('');
  const [loadingVibe, setLoadingVibe] = useState(false);
  const [pulseData, setMarketPulseData] = useState(null);
  const [loadingPulse, setLoadingPulse] = useState(false);
  const [lastRefresh, setLastRefresh] = useState(new Date());
  const [famousInsights, setFamousInsights] = useState([]);

  const [insightContent, setInsightContent] = useState(null);
  const [marketInsightData, setMarketInsightData] = useState(null);
  const [gameContext, setGameContext] = useState(null);
  const [stripIndex, setStripIndex] = useState(0);
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
    const topics = {
      1: "investing mindset",
      2: "market volatility",
      3: "portfolio management",
      4: "trading behavior"
    };
    const topic = topics[stepNum] || "wealth building";
    try {
      const res = await getTutorialInsight(topic);
      if (res?.message) setAiTutorialInsight(res.message);
    } catch (err) {}
  };

  useEffect(() => {
    if (currentStep > 0 && currentStep < 6) {
      setAiTutorialInsight(""); // Reset for new step
      fetchTutorialInsight(currentStep);
    }
  }, [currentStep]);

  const handleFinishTutorial = () => {
    localStorage.setItem("finplay_tutorial_done", "true");
    setStep(99);
  };

  const fetchMarketData = async () => {
    try {
      const [indicesRes, trendingRes, famousRes] = await Promise.all([
        getIndices(marketMode),
        getTrending(),
        getFamousInsights("ALL")
      ]);
      setIndices(indicesRes || []);
      setTrending(trendingRes || []);
      setFamousInsights(famousRes || []);
    } catch (err) {
      console.error("Market data fetch failed", err);
    } finally {
      setLoadingMarket(false);
    }
  };

  const fetchPulse = async () => {
    try {
      const response = await api.get(API_ENDPOINTS.PORTFOLIO.BASE);
      const list = Array.isArray(response.data?.data) ? response.data.data : [];
      const firstId = list[0]?.portfolioId;
      if (firstId) {
        setLoadingPulse(true);
        const pulse = await getMarketPulse(firstId);
        setMarketPulseData(pulse);
        setLastRefresh(new Date());
      }
    } catch (err) {
      console.error("Pulse fetch failed", err);
    } finally {
      setLoadingPulse(false);
    }
  };

  useEffect(() => {
    fetchMarketData();
    fetchPulse();

    // Background Refresh every 30 mins
    const poll = setInterval(() => {
      fetchMarketData();
      fetchPulse();
    }, 1800000);

    return () => clearInterval(poll);
  }, [marketMode]);

  useEffect(() => {
    const fetchVibe = async () => {
      setLoadingVibe(true);
      try {
        const res = await getMarketVibe(marketCode === 'IN' ? 'INDIA' : 'US');
        setMarketVibe(res?.vibe);
      } catch (err) {}
      setLoadingVibe(false);
    };
    fetchVibe();
  }, [marketCode]);

  const recentlyViewed = useMemo(() => (allRecentlyViewed || []).filter(s => s?.market === marketCode), [allRecentlyViewed, marketCode]);
  const decisions = useMemo(() => (allDecisions || []).filter(d => d && d.stock && d.stock.market === marketCode), [allDecisions, marketCode]);

  const missedOpportunities = useMemo(() => {
    return decisions.filter(d => d.choice === 'skip' && d.isCorrect === false).slice(0, 3);
  }, [decisions]);

  const objectives = useMemo(() => [
    { title: "Daily Arena Clear", progress: (decisions?.length || 0) >= 5 ? 100 : ((decisions?.length || 0) / 5) * 100, icon: <Target size={12}/> },
    { title: "Market Analysis", progress: (recentlyViewed?.length || 0) >= 3 ? 100 : ((recentlyViewed?.length || 0) / 3) * 100, icon: <Zap size={12}/> }
  ], [decisions, recentlyViewed]);


  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    if (debouncedSearch.trim().length > 1) {
      performSearch();
    } else {
      setSearchResults([]);
      setIsSearching(false);
    }
  }, [debouncedSearch]);

  const performSearch = async () => {
    setIsSearching(true);
    setShowDropdown(true);
    try {
      const results = await searchStocks(debouncedSearch);
      setSearchResults(results || []);
    } catch (err) {
      console.error("SEARCH ERROR", err);
      setSearchResults([]);
    }
    setIsSearching(false);
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

  const indiaData = useMemo(() => ({
    indices: Array.isArray(indices) && indices.length > 0 && marketMode === "INDIA" ? indices : [
      { symbol: 'NIFTY 50', value: '22,453.80', change: '+124.50', percent: '+0.56%' },
      { symbol: 'SENSEX', value: '73,903.91', change: '+456.20', percent: '+0.62%' },
      { symbol: 'BANK NIFTY', value: '47,589.20', change: '-89.40', percent: '-0.19%' },
    ],
    trending: Array.isArray(trending) && trending.length > 0 && marketMode === "INDIA" ? trending : [
      { symbol: 'RELIANCE', name: 'Reliance Industries', price: 2950.00, change: '+1.2%', market: 'IN' },
      { symbol: 'ZOMATO', name: 'Zomato Limited', price: 185.00, change: '+5.4%', market: 'IN' },
      { symbol: 'HDFC BANK', name: 'HDFC Bank Ltd', price: 1450.00, change: '-0.4%', market: 'IN' },
      { symbol: 'TCS', name: 'Tata Consultancy Services', price: 3840.00, change: '-0.5%', market: 'IN' },
    ]
  }), [indices, trending, marketMode]);

  const usData = useMemo(() => ({
    indices: Array.isArray(indices) && indices.length > 0 && marketMode === "US" ? indices : [
      { symbol: 'S&P 500', value: '5,204.34', change: '+56.21', percent: '+1.09%' },
      { symbol: 'NASDAQ', value: '16,396.83', change: '+245.12', percent: '+1.51%' },
      { symbol: 'DOW JONES', value: '39,170.24', change: '+12.44', percent: '+0.03%' },
    ],
    trending: Array.isArray(trending) && trending.length > 0 && marketMode === "US" ? trending : [
      { symbol: 'NVDA', name: 'NVIDIA Corp', price: 894.52, change: '+2.45%', market: 'US' },
      { symbol: 'TSLA', name: 'Tesla, Inc.', price: 175.22, change: '+1.05%', market: 'US' },
      { symbol: 'AAPL', name: 'Apple Inc.', price: 182.41, change: '-0.45%', market: 'US' },
      { symbol: 'AMD', name: 'Advanced Micro Devices', price: 170.45, change: '-0.5%', market: 'US' },
    ]
  }), [indices, trending, marketMode]);

  const activeData = useMemo(() => marketMode === "INDIA" ? indiaData : usData, [marketMode, indiaData, usData]);

  useEffect(() => {
    const timer = setInterval(() => {
      setStripIndex((prev) => (prev + 1) % 5);
    }, 5000);
    return () => clearInterval(timer);
  }, []);

  const activeSmartData = marketMode === "INDIA" ? {
    insights: [
      { title: "India's Retail Revolution", sub: "150M+ new investors entering the capital markets this year." },
      { title: "Green Energy Pivot", sub: "Reliance's $10B Infrastructure Plan gaining institutional trust." },
      { title: "Zomato Road to Profit", sub: "Food delivery margins hitting all-time high of 18%." },
      { title: "Banking Consolidation", sub: "HDFC Bank integration nearing full operational synergy." },
      { title: "IT Cloud Surge", sub: "Cloud adoption across digital enterprises up 40% YoY." }
    ],
    tip: "Understand the 'WHY' before the '%'",
    mood: "Strong momentum in Banking & Tech"
  } : {
    insights: [
      { title: "AI Supercycle Peak", sub: "NVIDIA's Data Center revenue exceeds all previous benchmarks." },
      { title: "Fed Interest Path", sub: "Market projects 3-rate stabilization through Q4 2024." },
      { title: "Apple Services Boom", sub: "High-margin App Store revenue offsetting hardware cycles." },
      { title: "Tesla Global Expansion", sub: "Gigafactory output hits record 5M unit run rate." },
      { title: "S&P 500 Concentration", sub: "Mega-Caps drive 70% of total index growth in 2024." }
    ],
    tip: "Don't chase high-PE stocks without growth context",
    mood: "Caution in Semi-conductors as Inflation looms"
  };

  const handleIndexClick = (idx) => {
    setMarketInsightData(idx);
  };

  const handleTryGameFromIndex = (indexData) => {
    setGameContext(indexData?.symbol);
    // Smooth scroll to arena
    arenaRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  return (
    <>
    <InsightPanel isOpen={!!insightContent} onClose={() => setInsightContent(null)} content={insightContent} />
    <MarketInsightPanel 
      isOpen={!!marketInsightData} 
      onClose={() => setMarketInsightData(null)} 
      indexData={marketInsightData} 
      onTryGame={handleTryGameFromIndex}
    />
    <div className={`transition-colors duration-500 min-h-full w-full pb-20 ${marketTone}`}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 md:px-8 py-6 sm:py-8 space-y-8">
        
        {/* AI Market Vibe Section */}
        <motion.div 
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative group cursor-default"
        >
          <div className="absolute inset-0 bg-blue-600/5 blur-3xl rounded-[3rem] group-hover:bg-blue-600/10 transition-colors" />
          <div className="relative bg-slate-900/60 border border-white/5 backdrop-blur-xl p-8 rounded-[2.5rem] flex flex-col md:flex-row items-center gap-8 shadow-2xl transition-transform hover:scale-[1.01] duration-500">
            <div className="w-16 h-16 bg-blue-600/10 rounded-2xl flex items-center justify-center border border-blue-500/20 shrink-0">
               <BrainCircuit size={32} className="text-blue-400" />
            </div>
            <div className="space-y-2 flex-1 text-center md:text-left">
               <div className="flex items-center justify-center md:justify-start gap-2 text-blue-400 mb-1">
                 <Sparkles size={12} className="animate-pulse" />
                 <span className="text-[9px] font-black uppercase tracking-[0.4em]">Live Market Pulse</span>
               </div>
               {loadingVibe ? (
                 <div className="space-y-2">
                   <div className="h-4 w-3/4 bg-white/5 animate-pulse rounded-full" />
                   <div className="h-4 w-1/2 bg-white/5 animate-pulse rounded-full" />
                 </div>
               ) : (
                 <h2 className="text-lg md:text-xl font-bold text-blue-50/90 leading-relaxed italic">
                   "{marketVibe || "The market is finding its rhythm. Stay observant and look for high-probability setups."}"
                 </h2>
               )}
            </div>
            <div className="hidden lg:block h-12 w-[1px] bg-white/5" />
            <div className="flex flex-col items-center md:items-end shrink-0">
               <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">Sync Status</span>
               <span className="text-lg font-black text-emerald-500 uppercase tracking-tighter flex items-center gap-2">
                 {loadingPulse ? 'Syncing...' : 'Live'} <div className={`w-2 h-2 rounded-full ${loadingPulse ? 'bg-amber-500 animate-pulse' : 'bg-emerald-500 animate-ping'}`} />
               </span>
               <p className="text-[8px] font-bold text-slate-600 mt-1 uppercase">Last: {lastRefresh.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</p>
            </div>
          </div>
        </motion.div>

        {/* Portfolio Market Pulse: NEW SECTION */}
        {pulseData && (
           <motion.section 
             initial={{ opacity: 0, y: 10 }}
             animate={{ opacity: 1, y: 0 }}
             className="grid grid-cols-1 lg:grid-cols-12 gap-6"
           >
              <div className="lg:col-span-8 bg-slate-900/40 border border-white/5 rounded-[2.5rem] p-8 space-y-6">
                 <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                       <div className="p-2 bg-blue-600 rounded-xl shadow-lg shadow-blue-600/20"><Newspaper size={18} className="text-white"/></div>
                       <h3 className="text-sm font-black text-white uppercase tracking-widest">Contextual Market Pulse</h3>
                    </div>
                    <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest bg-white/5 px-3 py-1 rounded-full border border-white/5">Affecting Portfolio</span>
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
                    <div className="flex items-center gap-2 text-indigo-400">
                       <Zap size={16} className="fill-indigo-400" />
                       <span className="text-[10px] font-black uppercase tracking-[0.2em]">Strategic AI Insights</span>
                    </div>
                    <div className="space-y-4">
                       {(pulseData.insights || "").split('\n').filter(line => line.trim()).map((line, i) => (
                         <div key={i} className="flex gap-4 group">
                            <div className="w-1.5 h-1.5 rounded-full bg-indigo-500 mt-2 shrink-0 group-hover:scale-150 transition-transform" />
                            <p className="text-sm font-medium text-slate-300 leading-relaxed">{line.replace(/^[*-]\s*/, '')}</p>
                         </div>
                       ))}
                    </div>
                 </div>
              </div>
           </motion.section>
        )}

        {/* Top Section: Search & Indices */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-center">
           {/* Search Section */}
           <div className="lg:col-span-6">
              <section className="relative w-full" ref={dropdownRef}>
                <div className="relative group">
                  <div className="absolute inset-0 bg-gradient-to-r from-blue-500/10 to-purple-500/10 rounded-3xl blur-2xl opacity-50" />
                  <div className="relative flex items-center bg-slate-900/80 border border-slate-800 rounded-2xl p-1 focus-within:border-blue-500/50 transition-all shadow-xl">
                    <div className="flex items-center justify-center pl-4 pr-2 text-slate-400">
                      {isSearching ? <Loader2 size={18} className="animate-spin text-blue-500" /> : <Search size={18} />}
                    </div>
                    <input 
                      type="text"
                      value={searchQuery}
                      onChange={(e) => {
                        setSearchQuery(e.target.value);
                        setShowDropdown(true);
                      }}
                      onKeyDown={handleSearchKeyDown}
                      placeholder={`Search ${marketMode === 'INDIA' ? 'NSE/BSE' : 'US (Nasdaq/NYSE)'} Stocks...`}
                      className="w-full bg-transparent py-3 text-sm font-bold text-white placeholder-slate-500 focus:outline-none"
                    />
                  </div>
                </div>

                {/* Search Dropdown */}
                {showDropdown && (searchQuery.length > 1) && (isSearching || searchResults.length > 0) && (
                  <div className="absolute top-full left-0 right-0 mt-2 z-50">
                    <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl max-h-[400px] overflow-y-auto">
                      {isSearching ? <div className="p-8 text-center text-slate-500 text-[10px] font-black uppercase animate-pulse">Syncing...</div> : (
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
           
           {/* Indices Scroller */}
           <div className="lg:col-span-6">
              <section className="bg-slate-900/40 p-1 rounded-2xl border border-slate-800/50">
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-1">
                  {(Array.isArray(activeData?.indices) ? activeData.indices : []).map((idx) => (
                    <IndexCard 
                      key={idx.symbol}
                      {...idx}
                      onClick={() => handleIndexClick(idx)}
                    />
                  ))}
                </div>
              </section>
           </div>
        </div>

        {/* Main Content: Arena (Left) | Intelligence & Movers (Right) */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Left Column (8 units) - GAME PRIMARY */}
          <div className="lg:col-span-8 space-y-6" ref={arenaRef}>
            <section className="relative">
              <FinPlayArena 
                marketMode={marketMode} 
                context={gameContext}
                onDecisionMade={(decision) => {
                  setLastDecision(decision);
                  recordDecision(decision);
                }}
                onShowInsight={setInsightContent}
              />

              {/* AI-Curated Investor Wisdom */}
              <div className="space-y-4 pt-4">
                <div className="flex items-center justify-between px-2">
                  <div className="flex items-center gap-3">
                    <div className="p-2 bg-rose-600 rounded-xl shadow-lg shadow-rose-600/20"><PlayCircle size={16} className="text-white" /></div>
                    <h2 className="text-[10px] font-black text-white uppercase tracking-widest italic">The Investor's Library</h2>
                  </div>
                  <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest bg-white/5 px-3 py-1 rounded-full border border-white/5">Video Insights</span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {(Array.isArray(famousInsights) ? famousInsights : []).slice(0, 2).map((insight, i) => (
                    <motion.div 
                      key={i}
                      whileHover={{ y: -5, scale: 1.01 }}
                      onClick={() => window.open(insight.podcastUrl, '_blank')}
                      className="bg-slate-900/40 border border-white/5 p-6 rounded-[2rem] relative overflow-hidden group cursor-pointer shadow-xl backdrop-blur-md"
                    >
                      <div className="absolute top-0 right-0 p-6 opacity-5 group-hover:opacity-10 transition-opacity">
                        <QuoteIcon size={60} className="text-white" />
                      </div>
                      <div className="relative z-10 flex gap-6 items-center">
                        <div className="w-12 h-12 rounded-xl bg-rose-600/10 flex items-center justify-center border border-rose-500/20 shadow-inner group-hover:bg-rose-600/20 transition-colors">
                          <PlayCircle size={24} className="text-rose-500" />
                        </div>
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

            {/* Sub-section: Recent Activity replaced with Your Next Move if empty */}
            <section className="space-y-4">
                <div className="flex items-center gap-2 px-2">
                  {recentlyViewed.length > 0 ? <Activity size={16} className="text-blue-500" /> : <Zap size={16} className="text-purple-500" />}
                  <h3 className="text-[10px] font-black text-white uppercase tracking-widest">
                    {recentlyViewed.length > 0 ? "Recently Viewed" : "Your Next Move"}
                  </h3>
                </div>
                
                {recentlyViewed.length > 0 ? (
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    {((recentlyViewed || []).slice(0, 4)).map((stock, i) => (
                      <div key={i} onClick={() => openStockPanel(stock)} className="p-4 rounded-2xl border border-slate-800 bg-slate-900/60 hover:bg-slate-800 transition-all cursor-pointer group shadow-lg">
                        <div className="flex justify-between items-start mb-2">
                          <span className="text-white font-black text-xs group-hover:text-blue-400">{stock?.symbol}</span>
                          <ArrowUpRight size={12} className="text-slate-600 group-hover:text-blue-400" />
                        </div>
                        <p className="text-sm font-black text-white">{formatPrice(stock?.price, stock?.market)}</p>
                      </div>
                    ))}
                  </div>
                ) : (
                  <NextEdgeCard behaviorState="cautious" />
                )}
            </section>
          </div>

          {/* Right Column (4 units) - Movers & Intelligence */}
          <div className="lg:col-span-4 space-y-6">
            
            {/* NEW: Top Movers Split Section */}
            <section className="space-y-4">
                <div className="flex items-center gap-2 px-2">
                  <TrendingUp size={16} className="text-emerald-500" />
                  <h3 className="text-[10px] font-black text-white uppercase tracking-widest">Top Movers</h3>
                </div>
                
                <div className="space-y-3">
                   {/* Gainers */}
                   <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-4 space-y-3">
                      <p className="text-[9px] font-black text-emerald-500 uppercase tracking-[0.2em] mb-2 flex items-center gap-2">🔥 Top Gainers</p>
                      {(Array.isArray(activeData?.trending) ? activeData.trending : []).filter(s => (s?.change || "").startsWith('+')).slice(0, 2).map(stock => (
                        <div key={stock?.symbol} onClick={() => openStockPanel(stock)} className="flex items-center justify-between p-3 bg-white/5 hover:bg-white/10 rounded-2xl border border-white/5 cursor-pointer transition-all group">
                           <div className="flex items-center gap-3">
                              <div className="w-8 h-8 rounded-lg bg-emerald-500/10 flex items-center justify-center font-black text-emerald-500 text-[10px]">{(stock?.symbol || "")[0]}</div>
                              <span className="text-xs font-black text-white group-hover:text-emerald-400 transition-colors">{stock?.symbol}</span>
                           </div>
                           <span className="text-xs font-black text-emerald-500">{stock?.change}</span>
                        </div>
                      ))}
                   </div>

                   {/* Losers */}
                   <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-4 space-y-3">
                      <p className="text-[9px] font-black text-rose-500 uppercase tracking-[0.2em] mb-2 flex items-center gap-2">📉 Top Losers</p>
                      {(Array.isArray(activeData?.trending) ? activeData.trending : []).filter(s => (s?.change || "").startsWith('-')).slice(0, 2).map(stock => (
                        <div key={stock?.symbol} onClick={() => openStockPanel(stock)} className="flex items-center justify-between p-3 bg-white/5 hover:bg-white/10 rounded-2xl border border-white/5 cursor-pointer transition-all group">
                           <div className="flex items-center gap-3">
                              <div className="w-8 h-8 rounded-lg bg-rose-500/10 flex items-center justify-center font-black text-rose-500 text-[10px]">{(stock?.symbol || "")[0]}</div>
                              <span className="text-xs font-black text-white group-hover:text-rose-400 transition-colors">{stock?.symbol}</span>
                           </div>
                           <span className="text-xs font-black text-rose-500">{stock?.change}</span>
                        </div>
                      ))}
                   </div>
                </div>
            </section>
            
            {/* Last Move Quick Insight */}
            <AnimatePresence mode="wait">
              {lastDecision ? (
                <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: 0.95 }}>
                  <LastMoveCard decision={lastDecision} onNext={() => setLastDecision(null)} />
                </motion.div>
              ) : (
                <div className="bg-gradient-to-br from-blue-600 to-blue-400 p-6 rounded-3xl shadow-xl shadow-blue-500/20 relative overflow-hidden group">
                   <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:scale-110 transition-transform"><Lightbulb size={100} /></div>
                   <div className="relative z-10 space-y-3">
                      <span className="text-[10px] font-black bg-white/20 text-white px-2 py-1 rounded-lg uppercase">Daily Alpha</span>
                      <h3 className="text-xl font-black text-white leading-tight">Master the <br/>Decision Loop</h3>
                      <p className="text-xs text-blue-50 font-bold opacity-80">Clear the arena daily to sharpen your financial intuition.</p>
                      <button onClick={() => navigate('/portfolio')} className="w-full py-3 bg-white text-blue-600 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-blue-50 transition-colors">Go to Portfolio</button>
                   </div>
                </div>
              )}
            </AnimatePresence>

            {/* Smart Objectives */}
            <section className="bg-slate-900/40 border border-slate-800 p-5 rounded-3xl space-y-5 group cursor-pointer hover:bg-slate-800/50 transition-colors" onClick={() => setInsightContent({
                type: 'info',
                title: `Daily Objectives`,
                explanation: 'Completing daily objectives builds strong financial discipline. Consistency is key in the market.',
                insight: 'The system has noticed a 2-day streak. Complete today\'s missions to unlock advanced analytical features.',
                actions: [{ label: 'Continue Training' }]
            })}>
               <div className="flex items-center justify-between">
                 <div className="flex items-center gap-2">
                   <Target size={14} className="text-blue-500" />
                   <h3 className="text-[10px] font-black text-white uppercase tracking-widest">Active Objectives</h3>
                 </div>
                 <HelpCircle size={12} className="text-slate-500 opacity-0 group-hover:opacity-100 transition-opacity" />
               </div>
               <div className="space-y-4">
                  {(Array.isArray(objectives) ? objectives : []).map((obj, i) => (
                    <div key={i} className="space-y-1.5">
                       <div className="flex justify-between text-[9px] font-bold">
                          <span className="text-slate-400 flex items-center gap-2">{obj?.icon} {obj?.title}</span>
                          <span className="text-blue-400">{obj?.progress?.toFixed(0)}%</span>
                       </div>
                       <div className="h-1 w-full bg-slate-800 rounded-full overflow-hidden">
                          <div className="h-full bg-blue-500 transition-all duration-1000" style={{ width: `${obj?.progress}%` }} />
                       </div>
                    </div>
                  ))}
               </div>
            </section>

            {/* Missed Opportunities Feed */}
            {(Array.isArray(missedOpportunities) && missedOpportunities.length > 0) && (
               <section className="bg-slate-900/40 border border-slate-800 p-5 rounded-3xl space-y-4">
                  <div className="flex items-center gap-2">
                    <AlertTriangle size={14} className="text-rose-500" />
                    <h3 className="text-[10px] font-black text-white uppercase tracking-widest">Missed Growth</h3>
                  </div>
                  <div className="space-y-3">
                     {missedOpportunities.map((m, i) => (
                       <div 
                         key={i} 
                         onClick={() => setInsightContent({
                           type: 'game',
                           title: `Missed Opportunity: ${m?.stock?.symbol}`,
                           explanation: `You skipped ${m?.stock?.symbol} in the arena recently. The stock subsequently rallied.`,
                           data: [{ label: 'Missed Growth', value: m?.stock?.change, color: 'text-rose-400' }],
                           insight: `System Analysis: Breakouts often follow consolidation patterns. Don't let a "boring" stock deter you if the technicals align.`,
                           actions: [{ label: 'View Stock Deep Dive', primary: true, onClick: () => navigate(`/stock/${m?.stock?.symbol}`) }]
                         })}
                         className="flex items-center justify-between p-3 bg-rose-500/5 border border-rose-500/10 rounded-xl group hover:bg-rose-500/10 transition-colors cursor-pointer"
                       >
                          <div className="flex items-center gap-3">
                             <div className="w-8 h-8 rounded-lg bg-rose-500/10 flex items-center justify-center font-black text-rose-500 text-[10px]">{(m?.stock?.symbol || "")[0]}</div>
                             <div>
                               <p className="text-[10px] font-black text-white group-hover:text-rose-400 transition-colors">{m?.stock?.symbol}</p>
                               <p className="text-[8px] font-bold text-rose-400 uppercase">Rallied after skip</p>
                             </div>
                          </div>
                          <TrendingUp size={12} className="text-rose-400 group-hover:scale-110 transition-transform" />
                       </div>
                     ))}
                  </div>
               </section>
            )}

            {/* Smart Analysis Context */}
            <div 
              className="p-6 rounded-3xl shadow-xl relative overflow-hidden group min-h-[200px] flex flex-col justify-center border border-white/5 cursor-pointer" 
              style={{ background: `linear-gradient(145deg, ${accentColor}20, #020617)` }}
              onClick={() => setInsightContent({
                type: 'info',
                title: 'Live Market Intelligence',
                explanation: 'This dynamic feed pulls live market sentiment to guide your macro-level thinking.',
                insight: activeSmartData?.insights?.[stripIndex]?.sub || "Loading live intelligence...",
                actions: [{ label: 'Dismiss' }]
              })}
            >
              <div className="absolute top-0 right-0 p-6 opacity-5 group-hover:scale-110 transition-all duration-700"><Newspaper size={120} style={{ color: accentColor }} /></div>
              <div className="absolute top-4 right-4 opacity-0 group-hover:opacity-100 transition-opacity"><HelpCircle size={16} className="text-slate-400"/></div>
              <div className="relative z-10 space-y-4">
                <div className="flex items-center gap-2">
                  <Newspaper size={16} style={{ color: accentColor }} />
                  <h3 className="text-white font-black text-xs uppercase tracking-widest">Market Feed</h3>
                </div>
                <div className="space-y-2">
                   <h4 className="text-sm font-black text-white leading-tight">{activeSmartData?.insights?.[stripIndex]?.title || "Market Feed"}</h4>
                   <p className="text-[11px] text-slate-400 font-medium leading-relaxed">{activeSmartData?.insights?.[stripIndex]?.sub || "Loading live intelligence..."}</p>
                </div>
                <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest pt-2 flex items-center gap-1"><Zap size={10} style={{ color: accentColor }}/> Tap to analyze</p>
              </div>
            </div>

          </div>
        </div>

      </div>
    </div>

    {/* Hybrid Tutorial Overlay */}
    <GuideOverlay 
      step={currentStep}
      totalSteps={5}
      active={currentStep <= 5}
      onDismiss={handleFinishTutorial}
      title={
        currentStep === 1 ? "Welcome to FinPlay 🚀" :
        currentStep === 2 ? "The Market Floor 📊" :
        currentStep === 3 ? "Your Command Center 💼" :
        currentStep === 4 ? "Behavioral Insights 🧠" :
        "Ready to Fly? 🏁"
      }
      content={
        aiTutorialInsight || (
          currentStep === 1 ? "This is your simulated financial world. Every click is a lesson, every trade is a story." :
          currentStep === 2 ? "Watch the top indices. They are the pulse of the nation's economic heart." :
          currentStep === 3 ? "Your portfolio is where your decisions live. Track your growth and learn from volatility." :
          currentStep === 4 ? "Our AI scans your moves to find your 'Decision DNA'. Insights appear right here." :
          "The simulator is yours. Master your mind, and you will master the market. Start your first trade!"
        )
      }
      onNext={currentStep === 5 ? handleFinishTutorial : nextStep}
    />
    </>
  );
}
