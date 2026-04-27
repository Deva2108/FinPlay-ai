import React, { useState, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Zap, Target, Trophy, Activity, ChevronRight, BrainCircuit, Sparkles, Loader2 } from 'lucide-react';
import GameStockCard from './GameMode/GameStockCard';
import ActionPanel from './GameMode/ActionPanel';
import OutcomeOverlay from './GameMode/OutcomeOverlay';
import LiveContextPanel from './GameMode/LiveContextPanel';
import BuyModal from './GameMode/BuyModal';
import InfoTooltip from './InfoTooltip';
import MicroLearningCard from './MicroLearningCard';
import { getLearningInsight } from '../utils/learningEngine';
import { useStockPanel } from '../context/StockPanelContext';
import { useTrading } from '../context/TradingContext';
import { useBehavior } from '../context/BehaviorContext';

const indiaArenaStocks = [
  { symbol: 'RELIANCE', name: 'Reliance Industries', price: 2950.00, market: 'IN', change: '+1.2%', situation: 'Focusing on clean energy.', context: 'Building mega-factories for solar/wind.', impact: '+2.3%', isPositive: true, explanation: 'Green pivot is seen as a long-term growth driver.' },
  { symbol: 'ZOMATO', name: 'Zomato Limited', price: 185.00, market: 'IN', change: '+5.4%', situation: 'Blinkit revenue growth soaring.', context: 'Quick commerce adoption hitting record highs.', impact: '+6.5%', isPositive: true, explanation: 'Rapid expansion in delivery markets drives valuation.' },
  { symbol: 'HDFC BANK', name: 'HDFC Bank Ltd', price: 1450.00, market: 'IN', change: '-0.4%', situation: 'Post-merger integration delays.', context: 'Combining tech stacks is proving expensive.', impact: '-1.2%', isPositive: false, explanation: 'Operational friction is temporary but hurting sentiment.' },
  { symbol: 'TCS', name: 'Tata Consultancy', price: 3840.00, market: 'IN', change: '-0.5%', situation: 'US Tech budgets tightening.', context: 'Major clients delaying big software overhauls.', impact: '-2.1%', isPositive: false, explanation: 'Macro slowdown reduces immediate contract pipeline.' },
  { symbol: 'PAYTM', name: 'Paytm', price: 420.00, market: 'IN', change: '-10.0%', situation: 'Regulatory action on bank unit.', context: 'RBI imposes strict limits on new customers.', impact: '-15.4%', isPositive: false, explanation: 'Compliance hurdles create uncertainty for future growth.' },
  { symbol: 'JIOFIN', name: 'Jio Financial', price: 350.00, market: 'IN', change: '+2.1%', situation: 'Entering Asset Management.', context: 'Partnership with global giant BlackRock confirmed.', impact: '+5.2%', isPositive: true, explanation: 'Massive scale potential in under-penetrated sectors.' },
  { symbol: 'ADANI ENT', name: 'Adani Enterprises', price: 3100.00, market: 'IN', change: '+0.8%', situation: 'New airport expansion.', context: 'Successfully won bids for 3 more international terminals.', impact: '+3.1%', isPositive: true, explanation: 'Infrastructure dominance builds predictable long-term cashflows.' },
  { symbol: 'TATA MOTORS', name: 'Tata Motors', price: 980.00, market: 'IN', change: '+2.1%', situation: 'EV Sales hitting peak capacity.', context: 'Demand for Nexon EV exceeds supply chain speed.', impact: '+3.8%', isPositive: true, explanation: 'Lead in the EV segment secures future market share.' }
];

const usArenaStocks = [
  { symbol: 'AAPL', name: 'Apple Inc.', price: 182.41, market: 'US', change: '-0.45%', situation: 'AI integrated into iOS.', context: 'Apple Intelligence expected to boost upgrade cycle.', impact: '+3.5%', isPositive: true, explanation: 'Software-driven cycles increase hardware demand.' },
  { symbol: 'TSLA', name: 'Tesla, Inc.', price: 175.22, market: 'US', change: '+1.05%', situation: 'Global demand slowing.', context: 'Inventory build-up in China triggers price cuts.', impact: '-4.2%', isPositive: false, explanation: 'Margin compression is a major worry for investors.' },
  { symbol: 'NVDA', name: 'NVIDIA Corporation', price: 894.52, market: 'US', change: '+2.45%', situation: 'Next-gen chips delayed.', context: 'Blackwell architecture facing minor design tweaks.', impact: '-3.1%', isPositive: false, explanation: 'Markets hate delays in the AI growth story.' },
  { symbol: 'NFLX', name: 'Netflix', price: 620.00, market: 'US', change: '+1.8%', situation: 'Ad-tier revenue exceeds targets.', context: 'Lower-cost plan attracting millions of new users.', impact: '+7.4%', isPositive: true, explanation: 'New revenue streams reduce reliance on sub growth.' },
  { symbol: 'META', name: 'Meta Platforms', price: 505.00, market: 'US', change: '+0.5%', situation: 'Llama 3 open-source release.', context: 'AI model becoming the industry standard for devs.', impact: '+4.2%', isPositive: true, explanation: 'Platform dominance through AI positioning.' }
];

import { trackDecision, getDecisionStats, getArenaSummary, getArenaScenarios, api } from '../services/api';
import { useNavigate } from 'react-router-dom';

export default function FinPlayArena({ marketMode = "INDIA", onDecisionMade, onShowInsight, context }) {
  if (!marketMode) console.warn("FinPlayArena: marketMode is missing, defaulting to INDIA");
  const navigate = useNavigate();
  const [stocks, setStocks] = useState(marketMode === "INDIA" ? indiaArenaStocks : usArenaStocks);
  const [loadingScenarios, setLoadingScenarios] = useState(true);
  
  const { balance, executeBuy, recordGameResult } = useTrading();
  const { recordDecision, addMissedOpportunity, decisions, missedOpportunities: behaviorMissed, refreshInsights } = useBehavior();
  
  const [currentIdx, setCurrentIdx] = useState(0);
  const [gameStep, setGameStep] = useState('DECISION'); 

  useEffect(() => {
    const fetchScenarios = async () => {
      setLoadingScenarios(true);
      try {
        const dynamicStocks = await getArenaScenarios(marketMode);
        if (dynamicStocks && dynamicStocks.length > 0) {
          setStocks(dynamicStocks);
        }
      } catch (err) {
        console.error("Failed to fetch dynamic scenarios, using fallbacks", err);
      } finally {
        setLoadingScenarios(false);
      }
    };
    fetchScenarios();
  }, [marketMode]);

  const [quickFlash, setQuickFlash] = useState(null); 
  const [floatingFeedback, setFloatingFeedback] = useState(null);
  const [toast, setToast] = useState(null);
  const [narrative, setNarrative] = useState(null);
  const [decisionCount, setDecisionCount] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [userStats, setUserStats] = useState(null);

  const [userChoice, setUserChoice] = useState(null);
  const [score, setScore] = useState(0);
  const [streak, setStreak] = useState(0);
  const [isGameOver, setIsGameOver] = useState(false);
  const [isBuyModalOpen, setIsBuyModalOpen] = useState(false);
  const [lastResult, setLastResult] = useState(null); 

  const [insightScore, setInsightScore] = useState(() => parseInt(localStorage.getItem('insightScore') || '0', 10));

  useEffect(() => {
    const handleStorage = () => {
      setInsightScore(parseInt(localStorage.getItem('insightScore') || '0', 10));
    };
    window.addEventListener('storage', handleStorage);
    // Also poll slightly because localStorage events don't fire in the same tab
    const interval = setInterval(handleStorage, 2000);
    return () => {
      window.removeEventListener('storage', handleStorage);
      clearInterval(interval);
    };
  }, []);

  const { openStockPanel, isOpen: isPanelOpen } = useStockPanel();
  const currentStock = stocks[currentIdx];

  const confirmBuy = async (quantity) => {
    const success = await executeBuy(currentStock, quantity);
    if (success) {
      setToast(`Bought ${quantity} shares of ${currentStock.symbol}`);
      setIsBuyModalOpen(false);
      handleNext();
    }
  };

  const [roundDecisions, setRoundDecisions] = useState([]);
  const ROUND_LIMIT = 5;

  const handleDecision = (choice) => {
    if (isGameOver || isSubmitting) return;
    
    const decision = {
      symbol: currentStock?.symbol,
      action: (choice || "").toUpperCase(),
      price: currentStock?.price,
      isPositive: currentStock?.isPositive,
      impact: currentStock?.impact
    };

    const newRound = [...(roundDecisions || []), decision];
    setRoundDecisions(newRound);
    
    // Visual feedback (Brief Flash)
    setQuickFlash((choice === 'buy' && currentStock?.isPositive) || (choice === 'skip' && !currentStock?.isPositive) ? 'correct' : 'wrong');
    
    setTimeout(() => {
      setQuickFlash(null);
      if (newRound.length >= ROUND_LIMIT) {
        setGameStep('SUMMARY');
        generateDiagnosis(newRound);
      } else {
        setCurrentIdx(prev => (prev + 1) % (stocks?.length || 1));
      }
    }, 400);
  };

  const [diagnosis, setDiagnosis] = useState(null);
  const [isDiagnosing, setIsDiagnosing] = useState(false);

  const generateDiagnosis = async (history) => {
    setIsDiagnosing(true);
    try {
      const result = await getArenaSummary(history);
      setDiagnosis(result?.diagnosis);
    } catch (err) {
      setDiagnosis("Your patterns show a mix of caution and momentum. You're building a balanced eye for market entries.");
    } finally {
      setIsDiagnosing(false);
    }
  };

  const handleNext = () => {
    if (currentIdx === stocks.length - 1) {
      setIsGameOver(true);
      return;
    }
    setGameStep('DECISION');
    setUserChoice(null);
    setCurrentIdx((prev) => prev + 1);
  };

  const restartGame = () => {
    const fetchScenarios = async () => {
      setLoadingScenarios(true);
      try {
        const dynamicStocks = await getArenaScenarios(marketMode);
        if (dynamicStocks && dynamicStocks.length > 0) {
          setStocks(dynamicStocks);
        }
      } catch (err) {}
      setLoadingScenarios(false);
    };
    fetchScenarios();
    setCurrentIdx(0);
    setGameStep('DECISION');
    setUserChoice(null);
    setScore(0);
    setStreak(0);
    setIsGameOver(false);
    setDecisionCount(0);
    setRoundDecisions([]);
    setDiagnosis(null);
  };

  const gameResultInsight = useMemo(() => {
    if (!isGameOver) return null;
    return getLearningInsight({
      decisions: (decisions || []).slice(0, stocks?.length || 0), 
      missedOpportunities: (behaviorMissed || []).slice(0, 3),
      holdings: [], 
    });
  }, [isGameOver, decisions, behaviorMissed, stocks?.length]);

  if (loadingScenarios) {
    return (
      <div className="h-96 flex flex-col items-center justify-center gap-4">
        <Loader2 size={32} className="animate-spin text-blue-500" />
        <p className="text-[10px] font-black text-blue-500 uppercase tracking-[0.3em]">AI Generating Market Scenarios...</p>
      </div>
    );
  }

  if (isGameOver) {
    return (
      <div className="max-w-xl mx-auto px-4">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="text-center p-12 bg-slate-900/60 rounded-3xl border border-blue-500/20 shadow-2xl backdrop-blur-md">
          <div className="w-20 h-20 bg-blue-500/10 rounded-full flex items-center justify-center mx-auto mb-6 border border-blue-500/20"><Trophy size={40} className="text-blue-500" /></div>
          <h2 className="text-3xl font-black text-white mb-2 uppercase tracking-tighter">Arena Cleared</h2>
          <p className="text-slate-400 font-bold mb-8 uppercase tracking-widest text-sm">Accuracy: {((score / (stocks?.length || 1)) * 100).toFixed(0)}% • Total: {score}/{stocks?.length || 0}</p>
          
          <div className="mb-8 text-left">
            <MicroLearningCard insight={gameResultInsight} />
          </div>

          <button onClick={restartGame} className="px-10 py-4 bg-blue-600 hover:bg-blue-500 text-white rounded-2xl font-black uppercase tracking-widest text-xs transition-all shadow-xl active:scale-95">Re-Enter Arena</button>
        </motion.div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <BuyModal stock={currentStock} balance={balance} isOpen={isBuyModalOpen} onClose={() => setIsBuyModalOpen(false)} onConfirm={(qty) => confirmBuy?.(qty)} />

      <div className="flex items-center justify-between px-4">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-blue-600 rounded-xl shadow-lg shadow-blue-600/20"><Zap size={20} className="text-white" /></div>
          <div>
            <h2 className="text-xl font-black text-white uppercase tracking-tighter flex items-center gap-2">
              Decision Arena 
              <InfoTooltip concept="stoploss" />
              {context && <span className="text-xs text-blue-400 normal-case ml-2 font-bold tracking-normal opacity-80 italic">Based on: {context}</span>}
            </h2>
            <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Real-time behavior training</p>
          </div>
        </div>
        <div className="flex items-center gap-4 bg-slate-900/40 border border-slate-800 px-6 py-3 rounded-2xl shadow-lg">
          <div className="flex flex-col items-center px-4 border-r border-white/5">
            <span className="text-[10px] font-black text-blue-500 uppercase tracking-widest mb-1">Insights</span>
            <span className="text-sm font-black text-white">{insightScore}</span>
          </div>
          <div className="flex flex-col items-end">
            <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest leading-none mb-1.5">Streak</span>
            <span className="text-sm font-black text-emerald-500 leading-none">{streak}🔥</span>
          </div>
          <div className="h-8 w-[1px] bg-white/10" />
          <div className="flex flex-col items-end">
             <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest leading-none mb-1.5">Progress</span>
             <span className="text-sm font-black text-white leading-none">{currentIdx + 1} / {(stocks || []).length}</span>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-10 gap-8 items-start">
        {/* Left: Game Mode (70%) */}
        <div className="lg:col-span-7 relative rounded-[2.5rem] overflow-hidden transition-transform duration-500 z-10" style={{ transform: isPanelOpen ? 'scale(0.98)' : 'scale(1)', filter: isPanelOpen ? 'brightness(0.7) blur(2px)' : 'none', pointerEvents: isPanelOpen ? 'none' : 'auto' }}>
          
          {/* Fast Result Flash Overlay */}
          <AnimatePresence>
            {quickFlash && (
              <motion.div 
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
                className={`absolute inset-0 z-[50] pointer-events-none ${quickFlash === 'correct' ? 'bg-emerald-500/40' : 'bg-rose-500/40'} backdrop-blur-sm`}
              />
            )}
          </AnimatePresence>

          {/* Floating Feedback Text */}
          <AnimatePresence>
            {floatingFeedback && (
              <motion.div 
                initial={{ opacity: 0, y: 20, scale: 0.95 }}
                animate={{ opacity: 1, y: -40, scale: 1 }}
                exit={{ opacity: 0 }}
                className="absolute inset-0 z-[60] flex items-center justify-center pointer-events-none"
              >
                <span className="px-6 py-2 bg-white text-slate-900 rounded-full font-black uppercase tracking-widest text-sm shadow-2xl border border-white/20">
                  {floatingFeedback}
                </span>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Narrative Message */}
          <AnimatePresence>
            {narrative && (
              <motion.div 
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="absolute top-10 left-0 right-0 z-[60] flex justify-center pointer-events-none"
              >
                <span className="px-4 py-1.5 bg-blue-600/90 text-white rounded-xl font-black uppercase tracking-widest text-[10px] backdrop-blur-md shadow-xl border border-blue-400/20">
                  {narrative}
                </span>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Toast Notification */}
          <AnimatePresence>
            {toast && (
              <motion.div 
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.9 }}
                className="absolute bottom-10 left-0 right-0 z-[70] flex justify-center pointer-events-none"
              >
                <div className="px-6 py-3 bg-emerald-600 text-white rounded-2xl font-black uppercase tracking-widest text-xs shadow-2xl border border-white/20 flex items-center gap-3 backdrop-blur-md">
                   <Zap size={14} /> {toast}
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          <AnimatePresence mode="wait">
            {gameStep === 'DECISION' ? (
              <motion.div key={`stock-${currentIdx}`} initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} transition={{ type: "spring", stiffness: 300, damping: 30 }}>
                <div className="max-w-2xl mx-auto">
                  <GameStockCard stock={currentStock} onClick={() => openStockPanel(currentStock)} />
                  <ActionPanel onDecision={handleDecision} stock={currentStock} disabled={isSubmitting} />
                  
                  {/* Continuation Flow Strip */}
                  <div className="mt-8 pt-6 border-t border-white/5 flex items-center justify-center gap-8">
                     <span className="text-[10px] font-black text-slate-600 uppercase tracking-[0.2em]">Round {roundDecisions.length + 1} of {ROUND_LIMIT}</span>
                  </div>
                </div>
              </motion.div>
            ) : gameStep === 'SUMMARY' ? (
              <motion.div key="summary" initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} className="max-w-2xl mx-auto py-10">
                <div className="bg-slate-900 border border-blue-500/20 rounded-[3rem] p-10 text-center space-y-8 shadow-2xl relative overflow-hidden">
                  <div className="absolute top-0 right-0 p-6 opacity-5"><BrainCircuit size={120} className="text-blue-400" /></div>
                  
                  <div className="space-y-2 relative z-10">
                    <div className="flex items-center justify-center gap-2 text-blue-400 mb-2">
                      <Sparkles size={16} />
                      <span className="text-[10px] font-black uppercase tracking-[0.3em]">Diagnosis Complete</span>
                    </div>
                    <h2 className="text-4xl font-black text-white tracking-tighter">Your Trading <br/> <span className="text-blue-500">Personality</span></h2>
                  </div>

                  <div className="bg-white/5 border border-white/10 p-8 rounded-[2rem] text-left">
                    {isDiagnosing ? (
                      <div className="flex flex-col items-center gap-4 py-4">
                        <Loader2 size={24} className="animate-spin text-blue-500" />
                        <p className="text-[10px] font-black text-blue-400 uppercase animate-pulse">Analyzing Psychology...</p>
                      </div>
                    ) : (
                      <p className="text-lg font-bold leading-relaxed text-blue-100 italic">"{diagnosis}"</p>
                    )}
                  </div>

                  <div className="grid grid-cols-5 gap-2">
                    {(roundDecisions || []).map((d, i) => (
                      <div key={i} className={`h-1.5 rounded-full ${d?.action === 'BUY' ? (d?.isPositive ? 'bg-emerald-500' : 'bg-rose-500') : 'bg-slate-700'}`} />
                    ))}
                  </div>

                  <div className="flex flex-col gap-3">
                    <button onClick={restartGame} className="w-full py-5 bg-blue-600 text-white font-black rounded-2xl uppercase tracking-widest text-xs hover:bg-blue-500 transition-all shadow-xl shadow-blue-900/20">Enter Next Round</button>
                    <button onClick={() => navigate('/portfolio')} className="w-full py-5 bg-white/5 text-slate-400 border border-white/10 rounded-2xl uppercase tracking-widest text-xs font-black hover:text-white transition-all">Go to Portfolio</button>
                  </div>
                </div>
              </motion.div>
            ) : (
              <motion.div key={`outcome-${currentIdx}`} initial={{ opacity: 0, scale: 1.05 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}>
                <div className="max-w-2xl mx-auto">
                  <OutcomeOverlay 
                    stock={currentStock} 
                    choice={userChoice} 
                    gameStep={gameStep} 
                    streak={streak} 
                    onNext={handleNext} 
                    onReset={() => {
                      setGameStep('DECISION');
                      setUserChoice(null);
                    }}
                    result={lastResult}
                  />
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Right: Context Panel (30%) */}
        <div className="lg:col-span-3 h-full">
           <LiveContextPanel stock={currentStock} gameStep={gameStep} onShowInsight={onShowInsight} />
        </div>
      </div>
    </div>
  );
}
