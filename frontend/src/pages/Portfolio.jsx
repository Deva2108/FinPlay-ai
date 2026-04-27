import { useState, useEffect, useMemo, useRef } from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip as RechartsTooltip } from 'recharts';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  Briefcase, ArrowUpRight, TrendingDown, PieChart as PieIcon, Activity, 
  ArrowRight, DollarSign, Zap, Lightbulb, Trash2, Shield, AlertTriangle, 
  Info, Target, Newspaper, Award, TrendingUp, ChevronRight, CheckCircle2,
  BrainCircuit, LineChart, HelpCircle, Sparkles, Loader2
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { getUserPortfolios, getPortfolioMentorAdvice, getArchetype } from '../services/api';
import { formatPrice } from '../utils/formatters';
import { useTrading } from '../context/TradingContext';
import { useStockPanel } from '../context/StockPanelContext';
import { useBehavior } from '../context/BehaviorContext';
import PortfolioSuggestionCard from '../components/PortfolioSuggestionCard';
import InsightPanel from '../components/InsightPanel';
import NextEdgeCard from '../components/NextEdgeCard';
import PortfolioFlowInsight from '../components/PortfolioFlowInsight';
import PortfolioStockPanel from '../components/PortfolioStockPanel';
import InfoTooltip from '../components/InfoTooltip';
import MicroLearningCard from '../components/MicroLearningCard';
import { getLearningInsight } from '../utils/learningEngine';

const COLORS = ['#3b82f6', '#8b5cf6', '#22c55e', '#f59e0b', '#ec4899', '#14b8a6'];

import { useMarket } from '../context/MarketContext';

const MarketInsights = [
  "Infrastructure and Energy sectors are showing strong accumulation patterns this week.",
  "Inflation data cooling in major markets. Possible rate stabilization expected in Q3.",
  "Tech giants reporting higher-than-expected cloud revenue growth.",
  "Consumer spending shows resilience despite macro-economic tightening.",
  "Semi-conductor supply chain stabilizing; outlook bullish for hardware manufacturing."
];

export default function Portfolio() {
  const { balance, portfolio: allPortfolio, executeSell, lastAction, decisions: allDecisions, gameImpact, loading, refreshData } = useTrading();
  const { decisions: behaviorDecisions, missedOpportunities: behaviorMissed } = useBehavior();
  const { openStockPanel } = useStockPanel();
  const { marketCode } = useMarket();
  const navigate = useNavigate();

  useEffect(() => {
    refreshData();
  }, [refreshData, marketCode]);

  const portfolio = useMemo(() => (allPortfolio || []).filter(p => p && p.market === (marketCode === 'IN' ? 'INDIA' : marketCode)), [allPortfolio, marketCode]);

  const decisions = useMemo(() => (allDecisions || []).filter(d => d && d.stock && d.stock.market === marketCode), [allDecisions, marketCode]);

  const [priceModifiers, setPriceModifiers] = useState({});
  const [lastTotalValue, setLastTotalValue] = useState(0);
  const [valueDirection, setValueDirection] = useState(null);
  const [showBreakdown, setShowBreakdown] = useState(false);
  const [insightIndex, setInsightIndex] = useState(0);
  const [feedback, setFeedback] = useState(null);
  const [insightContent, setInsightContent] = useState(null);
  const [isStockPanelOpen, setIsStockPanelOpen] = useState(false);
  const [selectedPortfolioStock, setSelectedPortfolioStock] = useState(null);

  const [activePortfolioId, setActivePortfolioId] = useState(null);
  const [mentorAdvice, setMentorAdvice] = useState('');
  const [loadingMentor, setLoadingMentor] = useState(false);
  const [archetype, setArchetype] = useState(null);
  const [loadingArchetype, setLoadingArchetype] = useState(false);

  useEffect(() => {
    const fetchArchetype = async () => {
      setLoadingArchetype(true);
      try {
        const res = await getArchetype();
        setArchetype(res);
      } catch (err) {}
      setLoadingArchetype(false);
    };
    fetchArchetype();
  }, []);

  useEffect(() => {
    const init = async () => {
      try {
        const ports = await getUserPortfolios();
        const list = Array.isArray(ports) ? ports : (ports?.result || []);
        if (list.length > 0) setActivePortfolioId(list[0].portfolioId);
      } catch (err) {}
    };
    init();
  }, []);

  const fetchMentorAdvice = async () => {
    if (!activePortfolioId) return;
    setLoadingMentor(true);
    try {
      const res = await getPortfolioMentorAdvice(activePortfolioId);
      setMentorAdvice(res?.advice);
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingMentor(false);
    }
  };

  useEffect(() => {
    if (activePortfolioId) fetchMentorAdvice();
  }, [activePortfolioId]);

  const handlePortfolioStockClick = (stock) => {
    setSelectedPortfolioStock(stock);
    setIsStockPanelOpen(true);
  };

  const handlePortfolioAction = (type, stock) => {
    if (type === 'sell') {
      const success = executeSell(stock.symbol, stock.currentValue, stock.quantity);
      if (success) {
        triggerFeedback(`Sold ${stock.symbol} position`, 'success');
        setIsStockPanelOpen(false);
      }
    } else if (type === 'buy') {
      openStockPanel(stock);
      setIsStockPanelOpen(false);
    }
  };

  useEffect(() => {
    const interval = setInterval(() => {
      setInsightIndex(prev => (prev + 1) % MarketInsights.length);
    }, 5000);
    return () => clearInterval(interval);
  }, []);

  const { holdings, totalInvested, totalCurrentValue, totalGain, totalYield, allocation, portfolioMood, riskLevel, topStock } = useMemo(() => {
    const totalInvested = portfolio.reduce((acc, curr) => acc + curr.invested, 0);
    
    const calculatedHoldings = (portfolio || []).map(stock => ({
      ...stock,
      status: stock.gainVal >= 0 ? 'profit' : 'loss'
    }));

    const totalCurrentValue = (calculatedHoldings || []).reduce((acc, curr) => acc + curr.currentValue, 0);
    const totalGain = totalCurrentValue - totalInvested;
    const totalYield = totalInvested > 0 ? (totalGain / totalInvested) * 100 : 0;
    const allocation = (calculatedHoldings || []).map(h => ({ name: h.symbol, value: h.currentValue }));

    let portfolioMood = 'Mixed';
    if (totalYield > 2) portfolioMood = 'Bullish';
    if (totalYield < -2) portfolioMood = 'Risky';

    const riskLevel = (calculatedHoldings || []).length === 0 ? 'None' : (calculatedHoldings || []).length === 1 ? 'High' : (calculatedHoldings || []).length <= 3 ? 'Moderate' : 'Balanced';
    const topStock = (allocation || []).length > 0 ? ((allocation || []).sort((a,b) => b.value - a.value)[0]?.name || '') : '';

    return {
      holdings: calculatedHoldings,
      totalInvested,
      totalCurrentValue,
      totalGain,
      totalYield,
      allocation,
      portfolioMood,
      riskLevel,
      topStock
    };
  }, [portfolio]);

  const adaptiveInsight = useMemo(() => {
    return getLearningInsight({
      decisions: behaviorDecisions || [],
      missedOpportunities: behaviorMissed || [],
      holdings,
      totalCurrentValue
    });
  }, [behaviorDecisions, behaviorMissed, holdings, totalCurrentValue]);

  const objectives = [
    { title: "Diversify Sectors", progress: (holdings?.length || 0) >= 3 ? 100 : ((holdings?.length || 0) / 3) * 100, icon: <Shield size={14}/> },
    { title: "Smart Decision Streak", progress: (decisions || []).filter(d => d?.isCorrect).length >= 5 ? 100 : ((decisions || []).filter(d => d?.isCorrect).length / 5) * 100, icon: <Zap size={14}/> },
    { title: "Portfolio Health", progress: totalYield > 0 ? 100 : 0, icon: <Activity size={14}/> }
  ];

  useEffect(() => {
    if (totalCurrentValue > lastTotalValue && lastTotalValue !== 0) {
      setValueDirection('up');
    } else if (totalCurrentValue < lastTotalValue && lastTotalValue !== 0) {
      setValueDirection('down');
    }
    setLastTotalValue(totalCurrentValue);
    const timeout = setTimeout(() => setValueDirection(null), 1000);
    return () => clearTimeout(timeout);
  }, [totalCurrentValue]);

  const triggerFeedback = (msg, type = 'success') => {
    setFeedback({ msg, type });
    setTimeout(() => setFeedback(null), 3000);
  };

  const handleSell = (e, stock) => {
    e.stopPropagation();
    const success = executeSell(stock.symbol, stock.currentValue, stock.quantity);
    if (success) {
      triggerFeedback(`Sold ${stock.symbol} at ${formatPrice(stock.currentValue/stock.quantity, stock.market)}`, 'success');
    }
  };

  const handlePortfolioClick = () => {
    setInsightContent({
      type: 'allocation',
      title: 'Portfolio Breakdown',
      explanation: 'Total value of all active holdings combined.',
      data: (holdings || []).map(h => ({
        label: h.symbol,
        value: formatPrice(h.currentValue, h.market),
        progress: totalCurrentValue > 0 ? (h.currentValue / totalCurrentValue) * 100 : 0,
        barColor: h.status === 'profit' ? '#10b981' : '#f43f5e'
      })),
      insight: (holdings || []).length === 1 
        ? "Concentrated in one asset. High volatility absorption."
        : "Portfolio is spread across assets. Risk is distributed.",
      actions: [{ label: 'View Allocation Chart', onClick: () => {} }]
    });
  };

  const handleReturnsClick = () => {
    setInsightContent({
      type: 'return',
      title: 'Returns Logic',
      explanation: 'Returns show investment performance vs initial capital.',
      data: [
        { label: 'Total Invested', value: formatPrice(totalInvested, marketCode) },
        { label: 'Current Value', value: formatPrice(totalCurrentValue, marketCode) },
        { label: 'Net Profit/Loss', value: formatPrice(totalGain, marketCode), color: totalGain >= 0 ? 'text-emerald-400' : 'text-rose-400' }
      ],
      insight: totalYield > 5 ? "Excellent growth path." : "Small gains compound over time.",
      actions: [{ label: 'Hide Details', onClick: () => setInsightContent(null) }]
    });
  };

  const handleRiskClick = () => {
    setInsightContent({
      type: 'risk',
      title: 'Risk Analysis',
      explanation: 'Risk is calculated based on capital concentration.',
      data: [
        { label: 'Current Risk Level', value: riskLevel, color: riskLevel === 'High' ? 'text-rose-400' : 'text-yellow-400' },
        { label: 'Top Holding', value: topStock || 'None' }
      ],
      insight: riskLevel === 'High' ? "Highly concentrated portfolio." : "Risk is balanced across holdings.",
      actions: [{ label: 'Explore More Stocks', onClick: () => navigate('/') }]
    });
  };

  const handleStockClick = (stock) => {
    setInsightContent({
      type: 'stock',
      title: `${stock.symbol} Deep Dive`,
      explanation: `Holdings: ${Math.floor(stock.quantity)} shares at ${formatPrice(stock.buyPrice, stock.market)}.`,
      data: [
        { label: 'Current Value', value: formatPrice(stock.currentValue, stock.market) },
        { label: 'Unrealized P&L', value: `${stock.status === 'profit' ? '+' : ''}${formatPrice(stock.gainVal, stock.market)}`, color: stock.status === 'profit' ? 'text-emerald-400' : 'text-rose-400' },
        { label: 'Portfolio Impact', value: `${totalCurrentValue > 0 ? ((stock.currentValue / totalCurrentValue) * 100).toFixed(1) : 0}%` }
      ],
      insight: stock.gainPct > 3 ? "Strong momentum." : stock.gainPct < -3 ? "Weak trend." : "Consolidating.",
      actions: [
        { label: 'Add More', primary: true, onClick: () => openStockPanel(stock) },
        { label: 'Exit Position', onClick: () => { executeSell(stock.symbol, stock.currentValue); setInsightContent(null); } }
      ]
    });
  };

  if (portfolio.length === 0) return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="min-h-[80vh] flex flex-col items-center justify-center px-6 relative overflow-hidden text-center">
      <div className="absolute inset-0 bg-gradient-to-b from-blue-500/5 to-transparent pointer-events-none" />
      <div className="max-w-md w-full space-y-10 relative z-10">
        <motion.div animate={{ y: [0, -10, 0] }} transition={{ duration: 4, repeat: Infinity, ease: "easeInOut" }} className="relative inline-block">
          <div className="w-32 h-32 bg-slate-900/80 rounded-full flex items-center justify-center border border-slate-800 shadow-2xl mx-auto relative overflow-hidden">
             <div className="absolute inset-0 bg-blue-500/10 blur-xl rounded-full" />
             <Zap size={48} className="text-slate-700" />
          </div>
        </motion.div>
        <div className="space-y-4">
          <h2 className="text-4xl font-black text-white uppercase tracking-tighter">Start Your Journey</h2>
          <p className="text-sm opacity-80 text-slate-400 font-bold uppercase tracking-widest max-w-xs mx-auto">Available Capital: <span className="text-white font-black">{formatPrice(balance, marketCode)}</span></p>
        </div>
        <motion.button whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }} onClick={() => navigate('/')} className="w-full bg-white text-slate-950 font-black py-4 rounded-2xl uppercase tracking-widest text-xs transition-all shadow-xl flex items-center justify-center gap-4 group">
          Explore Stocks <ArrowRight size={18} className="group-hover:translate-x-2 transition-transform" />
        </motion.button>
      </div>
    </motion.div>
  );

  return (
    <>
    <InsightPanel isOpen={!!insightContent} onClose={() => setInsightContent(null)} content={insightContent} />
    <PortfolioStockPanel 
      isOpen={isStockPanelOpen} 
      onClose={() => setIsStockPanelOpen(false)} 
      stock={selectedPortfolioStock} 
      marketCode={marketCode}
      onAction={handlePortfolioAction}
    />
    <motion.div 
      initial={{ opacity: 0 }} 
      animate={{ opacity: 1, backgroundColor: totalGain >= 0 ? 'rgba(16, 185, 129, 0.02)' : 'rgba(244, 63, 94, 0.02)' }} 
      transition={{ duration: 1 }}
      className="w-full pb-32 transition-colors relative"
    >
      <AnimatePresence>
        {feedback && (
          <motion.div 
            initial={{ opacity: 0, y: -20, x: '-50%' }}
            animate={{ opacity: 1, y: 0, x: '-50%' }}
            exit={{ opacity: 0, scale: 0.9 }}
            className="fixed top-24 left-1/2 z-[100] px-6 py-3 rounded-full bg-blue-600 text-white font-black text-xs uppercase tracking-widest shadow-2xl flex items-center gap-3 border border-white/20 backdrop-blur-md"
          >
            <Zap size={16} /> {feedback.msg}
          </motion.div>
        )}
      </AnimatePresence>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 md:px-8 py-8 sm:py-12">
        <div className="grid grid-cols-1 lg:grid-cols-10 gap-4 items-start">

          {/* LEFT COLUMN: PRIMARY DATA & HOLDINGS */}
          <div className="lg:col-span-6 flex flex-col gap-6">

            {/* AI Behavioral Identity Card */}
            <motion.div 
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              className="p-8 rounded-[2.5rem] bg-slate-900/60 border border-white/5 relative overflow-hidden group"
            >
               <div className="absolute inset-0 bg-blue-600/5 blur-3xl rounded-[3rem]" />
               <div className="relative z-10 flex flex-col md:flex-row items-center gap-8">
                  <div className="w-20 h-20 rounded-3xl bg-gradient-to-br from-blue-600 to-purple-600 flex items-center justify-center text-3xl font-black text-white shadow-2xl group-hover:rotate-3 transition-transform shrink-0">
                     {loadingArchetype ? <Loader2 className="animate-spin" size={32} /> : (archetype?.title?.charAt(0) || "U")}
                  </div>
                  <div className="text-center md:text-left space-y-2">
                     <div className="flex items-center justify-center md:justify-start gap-3">
                        <h1 className="text-2xl font-black text-white tracking-tighter uppercase">
                          {loadingArchetype ? "Analyzing..." : (archetype?.title || "Evaluating Style")}
                        </h1>
                        <div className="px-3 py-1 bg-emerald-500/20 border border-emerald-500/20 rounded-full text-[9px] font-black text-emerald-400 uppercase tracking-widest">Psych Profile</div>
                     </div>
                     <p className="text-blue-100/70 font-medium text-sm leading-relaxed">
                        {loadingArchetype ? "Deep-scanning your recent market decisions..." : (archetype?.trait || "Start making decisions in the Arena to unlock your psychological profile.")}
                     </p>
                  </div>
               </div>
            </motion.div>

            {/* Core Value Card */}
            <div className="bg-slate-900/40 border border-slate-800 p-6 rounded-[2rem] shadow-2xl backdrop-blur-md">
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <p className="text-[10px] font-black text-slate-500 uppercase tracking-[0.3em]">Total Portfolio Value</p>
                    <div className={`px-2 py-0.5 rounded-full text-[8px] font-black uppercase tracking-widest border ${portfolioMood === 'Bullish' ? 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20' : portfolioMood === 'Risky' ? 'bg-rose-500/10 text-rose-500 border-rose-500/20' : 'bg-blue-500/10 text-blue-500 border-blue-500/20'}`}>
                      {portfolioMood}
                    </div>
                  </div>
                </div>
                
                <div className="flex items-start gap-3 group cursor-pointer" onClick={handlePortfolioClick}>
                  <motion.h3 
                    animate={{ 
                      color: valueDirection === 'up' ? '#34d399' : valueDirection === 'down' ? '#f43f5e' : '#ffffff',
                      textShadow: valueDirection === 'up' ? '0 0 40px rgba(52, 211, 153, 0.5)' : valueDirection === 'down' ? '0 0 40px rgba(244, 63, 94, 0.5)' : '0 20px 50px rgba(59,130,246,0.3)'
                    }}
                    transition={{ duration: 0.3 }}
                    className="text-5xl md:text-6xl font-black tracking-tighter"
                  >
                    {formatPrice(totalCurrentValue, marketCode)}
                  </motion.h3>
                </div>

                <div className="flex flex-wrap items-center gap-6 pt-2 cursor-pointer" onClick={handleReturnsClick}>
                  <InfoTooltip concept="pnl">
                    <div className={`flex items-center gap-2 font-semibold text-xl md:text-2xl ${totalGain >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                      {totalGain >= 0 ? <TrendingUp size={20}/> : <TrendingDown size={20}/>}
                      <span>{totalGain >= 0 ? '+' : ''}{formatPrice(totalGain, marketCode)}</span>
                    </div>
                  </InfoTooltip>
                  <div className="flex items-center gap-2">
                    <InfoTooltip concept="yield">
                      <span className="text-base font-medium text-slate-500">
                        {totalGain >= 0 ? '+' : ''}{totalYield.toFixed(2)}%
                      </span>
                    </InfoTooltip>
                    <span className="text-[10px] font-black text-slate-600 uppercase tracking-widest">Returns</span>
                  </div>
                </div>
              </div>
            </div>

            <NextEdgeCard behaviorState="balanced" />
            <PortfolioFlowInsight />

            {/* Holdings section */}
            <div className="space-y-4">
              <PortfolioSuggestionCard 
                holdings={holdings} 
                totalYield={totalYield} 
                mentorAdvice={mentorAdvice}
                loadingMentor={loadingMentor}
              />
              
              <div className="flex items-center gap-3 px-2">
                <div className="p-2 bg-blue-600 rounded-xl shadow-lg shadow-blue-600/20"><Activity size={16} className="text-white" /></div>
                <h2 className="text-[10px] font-black text-white uppercase tracking-widest">Holdings</h2>
              </div>

              <div className="flex flex-col gap-4">
                {(Array.isArray(holdings) ? holdings : []).map((stock) => (
                  <motion.div 
                    key={stock?.symbol || Math.random()}
                    onClick={() => handlePortfolioStockClick(stock)}
                    whileHover={{ scale: 1.01 }}
                    className="bg-slate-900/40 border border-slate-800 rounded-[2rem] p-6 group cursor-pointer relative overflow-hidden"
                  >
                    <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-6">
                      <div className="flex items-center gap-5 flex-1">
                        <div className={`w-12 h-12 rounded-xl flex items-center justify-center font-black text-lg border border-white/5 ${stock.status === 'profit' ? 'bg-emerald-500/10 text-emerald-500' : 'bg-rose-500/10 text-rose-500'}`}>
                          {(stock?.symbol || "")[0]}
                        </div>
                        <div className="space-y-0.5">
                          <h4 className="text-lg font-semibold text-white tracking-tight group-hover:text-blue-400 transition-colors">{stock.symbol}</h4>
                          <p className="text-sm opacity-80 text-slate-500">Avg {formatPrice(stock.buyPrice, stock.market)} • {Math.floor(stock.quantity)} Shares</p>
                        </div>
                      </div>
                      <div className="flex items-center justify-between w-full sm:w-auto gap-8 text-right">
                        <div>
                          <p className={`text-lg font-semibold tabular-nums ${stock.status === 'profit' ? 'text-emerald-400' : 'text-rose-400'}`}>{stock.status === 'profit' ? '+' : ''}{formatPrice(stock.gainVal, stock.market)}</p>
                          <p className={`text-sm font-medium ${stock.status === 'profit' ? 'text-emerald-500' : 'text-rose-500'}`}>{stock.gainPct.toFixed(2)}% Returns</p>
                        </div>
                        <div className="flex items-center gap-2" onClick={e => e.stopPropagation()}>
                           <button onClick={() => openStockPanel(stock)} className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-[10px] font-black uppercase transition-all shadow-lg">Buy</button>
                           <button onClick={(e) => handleSell(e, stock)} className="px-3 py-1.5 bg-slate-800 hover:bg-rose-600 text-slate-400 hover:text-white rounded-lg text-[10px] font-black uppercase transition-all border border-white/5">Sell</button>
                        </div>
                      </div>
                    </div>
                  </motion.div>
                ))}
              </div>
            </div>
          </div>

          {/* RIGHT COLUMN: ANALYTICS */}
          <div className="lg:col-span-4 flex flex-col gap-4 h-full">
            
            {/* AI Mentor Advice Card */}
            <motion.div 
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              className="p-8 rounded-[2rem] bg-[#020617] border border-blue-500/20 shadow-2xl relative overflow-hidden group"
            >
              <div className="absolute top-0 right-0 p-6 opacity-10 group-hover:opacity-20 transition-opacity">
                <BrainCircuit size={80} className="text-blue-400" />
              </div>
              
              <div className="relative z-10 space-y-6">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 bg-blue-600 rounded-xl text-white shadow-lg shadow-blue-600/20">
                    <Sparkles size={20} />
                  </div>
                  <div>
                    <h3 className="text-xl font-black text-white uppercase tracking-tighter">AI Mentor Advice</h3>
                    <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Deep Portfolio Analysis</p>
                  </div>
                </div>

                <div className="bg-white/5 border border-white/5 p-6 rounded-2xl">
                  {loadingMentor ? (
                    <div className="flex flex-col items-center gap-3 py-4">
                      <Loader2 size={24} className="animate-spin text-blue-400" />
                      <span className="text-[10px] font-black text-blue-400 uppercase tracking-widest animate-pulse text-center">Syncing with Live Intelligence...</span>
                    </div>
                  ) : (
                    <p className="text-sm font-bold leading-relaxed text-blue-100/90 italic">
                      "{mentorAdvice || "Your portfolio is ready for analysis. Keep making trades to build a behavioral profile."}"
                    </p>
                  )}
                </div>

                <button 
                  onClick={fetchMentorAdvice}
                  disabled={loadingMentor}
                  className="flex items-center gap-2 text-[10px] font-black text-blue-400 uppercase tracking-widest hover:text-blue-300 transition-colors group/btn"
                >
                  Regenerate Analysis <ArrowRight size={12} className="group-hover/btn:translate-x-1 transition-transform" />
                </button>
              </div>
            </motion.div>

            {/* Adaptive Learning Insight */}
            <MicroLearningCard insight={adaptiveInsight} />

            {/* Capital Status */}
            <div className="p-6 rounded-[2rem] bg-gradient-to-br from-[#0f172a] to-[#1e293b] border border-white/5 shadow-[0_0_20px_rgba(59,130,246,0.08)] transition-all duration-300 relative overflow-hidden flex flex-col gap-4">
              <div className="space-y-1">
                <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Your Capital</p>
                <div className="flex items-center justify-between">
                  <p className="text-3xl font-black text-white tracking-tight">{formatPrice(balance, marketCode)} <span className="text-slate-500 text-sm font-bold lowercase tracking-normal">ready to act</span></p>
                  {gameImpact && gameImpact.timestamp && (Date.now() - gameImpact.timestamp < 300000) && (
                     <motion.div 
                       initial={{ x: 20, opacity: 0 }}
                       animate={{ x: 0, opacity: 1 }}
                       className={`px-3 py-1 rounded-lg text-[9px] font-black uppercase tracking-widest ${gameImpact.type === 'gain' ? 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20' : gameImpact.type === 'loss' ? 'bg-rose-500/10 text-rose-500 border-rose-500/20' : 'bg-amber-500/10 text-amber-500 border border-amber-500/20'}`}
                     >
                        {gameImpact.type === 'missed' ? 'Missed' : gameImpact.amount > 0 ? '+' : ''}₹{Math.abs(gameImpact.amount)} Game {gameImpact.type === 'missed' ? 'Opp' : 'Impact'}
                     </motion.div>
                  )}
                </div>
              </div>

              <div className="bg-white/5 rounded-2xl p-4 border border-white/5">
                <p className="text-xs text-slate-400 font-medium leading-relaxed italic">
                  "High liquidity allows for instant execution on market signals. Keep capital ready for high-conviction moves."
                </p>
              </div>

              <button 
                onClick={() => navigate('/')}
                className="w-full py-4 bg-white text-slate-900 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-blue-50 transition-all flex items-center justify-center gap-2 group shadow-xl"
              >
                Find Opportunities <ChevronRight size={14} className="group-hover:translate-x-1 transition-transform" />
              </button>

              {lastAction && (Date.now() - lastAction.timestamp < 3600000) && (
                <div className="bg-blue-600/10 border border-blue-500/20 p-3 rounded-xl flex items-center gap-3">
                  <Zap size={14} className="text-blue-400"/>
                  <p className="text-[10px] font-bold text-white uppercase">{lastAction.type} {lastAction.symbol} • Just now</p>
                </div>
              )}
            </div>

            {/* Market Thinking Feed */}
            <section 
              className="p-6 rounded-[2rem] bg-gradient-to-br from-[#0f172a] to-[#1e293b] border border-white/5 shadow-[0_0_20px_rgba(59,130,246,0.08)] hover:scale-[1.01] transition-all duration-300 relative overflow-hidden cursor-pointer group flex-1"
              onClick={() => setInsightContent({
                type: 'info',
                title: 'Market Intelligence',
                explanation: 'Live market analysis and sentiment tracking.',
                insight: MarketInsights[insightIndex],
                actions: [{ label: 'Got it' }]
              })}
            >
               <div className="absolute top-0 right-0 p-6 opacity-5"><Newspaper size={100} className="text-blue-400"/></div>
               <div className="flex items-center gap-2 mb-4 relative z-10">
                  <div className="text-blue-400 animate-pulse">
                    <BrainCircuit size={18} />
                  </div>
                  <h3 className="text-lg font-semibold text-white tracking-tight">Market Thinking</h3>
               </div>
               <AnimatePresence mode="wait">
                 <motion.div key={insightIndex} initial={{ opacity: 0, x: 10 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -10 }} className="min-h-[80px] relative z-10">
                    <p className="text-sm opacity-80 text-white leading-relaxed">{MarketInsights[insightIndex]}</p>
                 </motion.div>
               </AnimatePresence>
               <div className="mt-6 pt-4 border-t border-white/5 relative z-10">
                  <p className="text-xs font-bold text-blue-300">Suggestion: {riskLevel === 'High' ? "Hedge risk by exploring mid-cap." : "Trail stop-loss to protect gains."}</p>
               </div>
            </section>

            {/* Objectives */}
            <section className="p-6 rounded-[2rem] bg-gradient-to-br from-[#0f172a] to-[#1e293b] border border-white/5 shadow-[0_0_20px_rgba(59,130,246,0.08)] hover:scale-[1.01] transition-all duration-300 space-y-6 flex-1">
               <div className="flex items-center gap-2">
                  <Target size={18} className="text-emerald-400" />
                  <h3 className="text-lg font-semibold text-white tracking-tight">Objectives</h3>
               </div>
               <div className="space-y-5">
                  {(objectives || []).map((obj, i) => (
                    <div key={i} className="space-y-2">
                       <div className="flex justify-between items-center">
                          <span className="text-sm opacity-80 text-slate-300 uppercase tracking-widest">{obj.title}</span>
                          <span className={`text-xs font-bold ${obj?.progress === 100 ? "text-emerald-400" : "text-blue-400"}`}>
                            {obj?.progress === 100 ? <CheckCircle2 size={12}/> : `${obj?.progress?.toFixed(0)}%`}
                          </span>
                       </div>
                       <div className="h-1.5 w-full bg-slate-800 rounded-full overflow-hidden">
                          <motion.div initial={{ width: 0 }} animate={{ width: `${obj?.progress}%` }} transition={{ duration: 1.5 }} className={`h-full rounded-full ${obj?.progress === 100 ? "bg-emerald-500" : "bg-blue-500"}`} />
                       </div>
                    </div>
                  ))}
               </div>
            </section>

            {/* Allocation */}
            <div className="p-6 rounded-[2rem] bg-gradient-to-br from-[#0f172a] to-[#1e293b] border border-white/5 shadow-[0_0_20px_rgba(59,130,246,0.08)] hover:scale-[1.01] transition-all duration-300 flex flex-col items-center cursor-pointer flex-1 group" onClick={handleRiskClick}>
               <div className="h-32 w-full mb-4">
                  <ResponsiveContainer>
                    <PieChart>
                      <Pie data={allocation} innerRadius={35} outerRadius={50} paddingAngle={8} dataKey="value">
                        {(allocation || []).map((entry, index) => <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} stroke="none" />)}
                      </Pie>
                    </PieChart>
                  </ResponsiveContainer>
               </div>
               <div className="w-full space-y-3">
                  <div className="flex justify-between items-center py-2 border-b border-white/5">
                    <InfoTooltip concept="diversification">
                      <span className="text-sm opacity-80 text-slate-500 font-medium">Risk Level</span>
                    </InfoTooltip>
                    <span className={`text-sm font-semibold uppercase ${riskLevel === 'High' ? 'text-rose-400' : 'text-emerald-400'}`}>{riskLevel}</span>
                  </div>
                  <div className="flex justify-between items-center py-2 border-b border-white/5">
                    <span className="text-sm opacity-80 text-slate-500 font-medium">Positions</span>
                    <span className="text-sm font-semibold text-white">{holdings.length}</span>
                  </div>
               </div>
               <p className="mt-4 text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center justify-center gap-2"><Zap size={10}/> Analyze Risk</p>
            </div>

          </div>
        </div>
      </div>
    </motion.div>
    </>
  );
}
