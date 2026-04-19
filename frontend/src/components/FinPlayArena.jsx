import React, { useState, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Zap, Target, Trophy, Activity, ChevronRight } from 'lucide-react';
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
  { symbol: 'RELIANCE', name: 'Reliance Industries', price: 2950.00, market: 'IN', change: '+1.2%', situation: 'Focusing on clean energy.', context: 'The company is building new factories for solar and wind power.', impact: '+2.3%', isPositive: true, explanation: 'Moving into green energy is seen as a smart long-term move by investors.' },
  { symbol: 'ZOMATO', name: 'Zomato Limited', price: 185.00, market: 'IN', change: '+5.4%', situation: 'Orders are at a record high.', context: 'More people are ordering food during the festive season than ever before.', impact: '+6.5%', isPositive: true, explanation: 'Growing order numbers mean the company is earning more, which attracts buyers.' },
  { symbol: 'HDFC BANK', name: 'HDFC Bank Ltd', price: 1450.00, market: 'IN', change: '-0.4%', situation: 'Combining two big companies.', context: 'Merging two large banks is taking more time and effort than expected.', impact: '-1.2%', isPositive: false, explanation: 'Investors are worried that the extra work might slow down profits for a few months.' },
  { symbol: 'TCS', name: 'Tata Consultancy', price: 3840.00, market: 'IN', change: '-0.5%', situation: 'Global companies spending less.', context: 'Big clients in Europe are cutting their budgets for new software.', impact: '-2.1%', isPositive: false, explanation: 'Lower spending by clients means fewer contracts and lower earnings for TCS.' },
  { symbol: 'INFY', name: 'Infosys Limited', price: 1520.00, market: 'IN', change: '+0.4%', situation: 'Winning big new deals.', context: 'The company just signed a ₹12,000 crore contract with a US retailer.', impact: '+4.2%', isPositive: true, explanation: 'New contracts guarantee future income, making the company more valuable today.' },
  { symbol: 'TATA MOTORS', name: 'Tata Motors', price: 980.00, market: 'IN', change: '+2.1%', situation: 'EV Sales peaking.', context: 'The demand for electric cars in India is growing at 50% year-on-year.', impact: '+3.8%', isPositive: true, explanation: 'Dominance in the EV segment is positioning the company as a future leader.' },
  { symbol: 'SBI', name: 'State Bank of India', price: 750.00, market: 'IN', change: '-1.5%', situation: 'Loan defaults rising.', context: 'Agricultural loan recoveries have slowed down this quarter.', impact: '-2.4%', isPositive: false, explanation: 'Risk of non-performing assets (NPAs) makes investors cautious about bank stocks.' },
  { symbol: 'TITAN', name: 'Titan Company', price: 3600.00, market: 'IN', change: '+0.8%', situation: 'Wedding season demand.', context: 'Jewelry sales are expected to hit record highs this quarter.', impact: '+1.9%', isPositive: true, explanation: 'Strong consumption patterns in the luxury segment drive long-term value.' }
];

const usArenaStocks = [
  { symbol: 'AAPL', name: 'Apple Inc.', price: 182.41, market: 'US', change: '-0.45%', situation: 'New AI features for iPhones.', context: 'Apple is adding powerful Artificial Intelligence to its next software update.', impact: '+3.5%', isPositive: true, explanation: 'Excitement about new technology usually makes people want to buy the stock.' },
  { symbol: 'TSLA', name: 'Tesla, Inc.', price: 175.22, market: 'US', change: '+1.05%', situation: 'Selling fewer cars abroad.', context: 'Sales in some international markets have dropped due to more competition.', impact: '-4.2%', isPositive: false, explanation: 'If a company sells fewer products, its stock price often starts to fall.' },
  { symbol: 'NVDA', name: 'NVIDIA Corporation', price: 894.52, market: 'US', change: '+2.45%', situation: 'High demand for AI chips.', context: 'Almost every tech company is buying NVIDIA chips to build AI tools.', impact: '+8.1%', isPositive: true, explanation: 'Huge demand for a product leads to record profits, which pushes the price up.' },
  { symbol: 'MSFT', name: 'Microsoft Corp', price: 421.44, market: 'US', change: '+0.15%', situation: 'Cloud services growing fast.', context: 'More businesses are using Microsoft cloud tools to run their operations.', impact: '+4.8%', isPositive: true, explanation: 'Strong growth in a key part of the business makes the stock more attractive.' },
  { symbol: 'GOOGL', name: 'Alphabet Inc.', price: 154.22, market: 'US', change: '-0.8%', situation: 'Facing new legal rules.', context: 'Government officials are checking if the company is following fair competition laws.', impact: '-3.1%', isPositive: false, explanation: 'Legal troubles can lead to big fines, which makes investors nervous.' }
];

import { trackDecision, getDecisionStats } from '../services/api';
import { useNavigate } from 'react-router-dom';

export default function FinPlayArena({ marketMode = "INDIA", onDecisionMade, onShowInsight, context }) {
  const navigate = useNavigate();
  const stocks = marketMode === "INDIA" ? indiaArenaStocks : usArenaStocks;
  const { balance, executeBuy, recordGameResult } = useTrading();
  const { recordDecision, addMissedOpportunity, decisions, missedOpportunities: behaviorMissed, refreshInsights } = useBehavior();
  
  const [currentIdx, setCurrentIdx] = useState(() => Math.floor(Math.random() * stocks.length));
  const [gameStep, setGameStep] = useState('DECISION'); 
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
  const [lastResult, setLastResult] = useState(null); // { amount, type }

  const { openStockPanel, isOpen: isPanelOpen } = useStockPanel();
  const currentStock = stocks[currentIdx];

  useEffect(() => {
    if (context) {
      console.log("Game loaded with context:", context);
    }
  }, [context]);

  useEffect(() => { restartGame(); }, [marketMode]);

  const handleDecision = (choice) => {
    if (gameStep !== 'DECISION' || isGameOver || isSubmitting) return;
    if (choice === 'buy') { setIsBuyModalOpen(true); return; }
    processDecision('skip');
  };

  const confirmBuy = (quantity) => {
    const success = executeBuy(currentStock, quantity, currentStock.situation);
    if (success) { setIsBuyModalOpen(false); processDecision('buy'); }
  };

  const processDecision = async (choice) => {
    if (isSubmitting) return;
    setIsSubmitting(true);

    const isCorrect = (choice === 'buy' && currentStock.isPositive) || (choice === 'skip' && !currentStock.isPositive);
    
    // Create Decision Object
    const decisionObj = {
      symbol: currentStock.symbol,
      action: choice.toUpperCase(),
      price: currentStock.price,
      market: currentStock.market,
      timestamp: new Date().toISOString()
    };

    console.log("DEBUG: Decision Object Created:", decisionObj);

    // Track Decision - Backend + LocalStorage Fallback
    try {
      setToast("Analyzing your decision...");
      // 1. Backend
      await trackDecision(decisionObj);
      
      // 2. Refresh Stats for personalized insight
      const stats = await getDecisionStats();
      setUserStats(stats);
      refreshInsights();

      // Evolving Narrative Logic
      let narrativeText = "";
      const isBuying = choice.toUpperCase() === 'BUY';
      const actionVerb = isBuying ? 'acting with intent' : 'choosing comfort';
      
      if (stats.totalDecisions <= 2) {
        narrativeText = `Small choice. No big deal. You're just getting started.`;
      } else if (stats.totalDecisions <= 5) {
        narrativeText = `This is becoming a habit. You're starting to ${actionVerb}.`;
      } else {
        narrativeText = `This is how your financial life shapes. You are ${actionVerb} repeatedly.`;
      }
      
      setNarrative(narrativeText);
      setTimeout(() => setNarrative(null), 6000);
      setToast(null);
    } catch (err) {
      console.error("DEBUG: Backend tracking failed, falling back to localStorage", err);
      // 2. LocalStorage Fallback
      try {
        const history = JSON.parse(localStorage.getItem('finplay_decision_history') || '[]');
        history.push(decisionObj);
        localStorage.setItem('finplay_decision_history', JSON.stringify(history.slice(-100)));
      } catch (e) {
        console.error("DEBUG: LocalStorage fallback also failed", e);
      }
    }

    // Emotional Feedback Logic
    let amount = 0;
    let type = 'none';

    if (currentStock.isPositive && choice === 'buy') {
      amount = 120;
      type = 'gain';
    } else if (!currentStock.isPositive && choice === 'buy') {
      amount = -80;
      type = 'loss';
    } else if (choice === 'skip' && currentStock.isPositive) {
      amount = 150;
      type = 'missed';
    }

    setLastResult({ amount, type });

    // Fetch AI Evaluation
    try {
      const evaluationData = await evaluateDecision({
        symbol: currentStock.symbol,
        action: choice.toUpperCase(),
        price: currentStock.price,
        isPositive: currentStock.isPositive,
        pattern: userInsights?.behaviorType || 'balanced'
      });
      setLastResult(prev => ({ ...prev, ...evaluationData }));
    } catch (err) {
      console.error("AI Evaluation failed", err);
    }
    if (amount !== 0) {
      recordGameResult(amount, type);
    }

    // Record behavior
    recordDecision(choice, currentStock, marketMode);
    
    // Logic for missed opportunities: if skipped but it was positive
    if (choice === 'skip' && currentStock.isPositive) {
      addMissedOpportunity(currentStock, currentStock.impact);
    }

    if (choice === 'buy') {
      setToast(`DECISION LOGGED: ${currentStock.symbol} added to analysis.`);
      setTimeout(() => setToast(null), 3000);
    } else {
      setToast(`DECISION LOGGED: Skipped ${currentStock.symbol}.`);
      setTimeout(() => setToast(null), 3000);
    }

    setUserChoice(choice);
    setQuickFlash(isCorrect ? 'correct' : 'wrong');
    setDecisionCount(prev => prev + 1);

    // Floating Feedback Logic
    let feedback = "";
    if (choice === 'buy') {
      feedback = isCorrect ? "Momentum Master" : "Late Entry";
    } else {
      feedback = isCorrect ? "Clean Avoid" : "Missed Rally";
    }
    setFloatingFeedback(feedback);

    if (isCorrect) {
      setScore(prev => prev + 1);
      setStreak(prev => prev + 1);
    } else {
      setStreak(0);
    }

    if (decisionCount + 1 === 3) {
      setNarrative("Your decisions are shaping your portfolio");
      setTimeout(() => setNarrative(null), 3000);
    }

    if (onDecisionMade) {
      onDecisionMade({
        stock: currentStock,
        choice,
        isCorrect
      });
    }

    setTimeout(() => {
      setQuickFlash(null);
      setFloatingFeedback(null);
      setGameStep('FLASH');
      setIsSubmitting(false); // Enable for next stock after animation
      setTimeout(() => setGameStep('OBJECTIVE'), 600);
    }, 1000);
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
    setCurrentIdx(Math.floor(Math.random() * stocks.length));
    setGameStep('DECISION');
    setUserChoice(null);
    setScore(0);
    setStreak(0);
    setIsGameOver(false);
    setDecisionCount(0);
  };

  const gameResultInsight = useMemo(() => {
    if (!isGameOver) return null;
    return getLearningInsight({
      decisions: decisions.slice(0, stocks.length), 
      missedOpportunities: behaviorMissed.slice(0, 3),
      holdings: [], 
    });
  }, [isGameOver, decisions, behaviorMissed, stocks.length]);

  if (isGameOver) {
    return (
      <div className="max-w-xl mx-auto px-4">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="text-center p-12 bg-slate-900/60 rounded-3xl border border-blue-500/20 shadow-2xl backdrop-blur-md">
          <div className="w-20 h-20 bg-blue-500/10 rounded-full flex items-center justify-center mx-auto mb-6 border border-blue-500/20"><Trophy size={40} className="text-blue-500" /></div>
          <h2 className="text-3xl font-black text-white mb-2 uppercase tracking-tighter">Arena Cleared</h2>
          <p className="text-slate-400 font-bold mb-8 uppercase tracking-widest text-sm">Accuracy: {((score / stocks.length) * 100).toFixed(0)}% • Total: {score}/{stocks.length}</p>
          
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
      <BuyModal stock={currentStock} balance={balance} isOpen={isBuyModalOpen} onClose={() => setIsBuyModalOpen(false)} onConfirm={confirmBuy} />

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
        <div className="flex items-center gap-6 bg-slate-900/40 border border-slate-800 px-6 py-3 rounded-2xl">
          <div className="flex flex-col items-end">
            <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest leading-none mb-1.5">Streak</span>
            <span className="text-sm font-black text-emerald-500 leading-none">{streak}🔥</span>
          </div>
          <div className="h-8 w-[1px] bg-white/10" />
          <div className="flex flex-col items-end">
             <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest leading-none mb-1.5">Progress</span>
             <span className="text-sm font-black text-white leading-none">{currentIdx + 1} / {stocks.length}</span>
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
                     <span className="text-[10px] font-black text-slate-600 uppercase tracking-[0.2em]">Next Steps:</span>
                     <div className="flex gap-4">
                        <button onClick={() => navigate('/insights')} className="flex items-center gap-2 text-[10px] font-black text-slate-500 hover:text-blue-400 transition-colors uppercase tracking-widest">
                          <Activity size={12}/>
                          View Signals
                        </button>
                        <button onClick={() => navigate('/portfolio')} className="flex items-center gap-2 text-[10px] font-black text-slate-500 hover:text-blue-400 transition-colors uppercase tracking-widest">
                          <Trophy size={12}/>
                          Check Portfolio
                        </button>
                        <button onClick={handleNext} className="flex items-center gap-2 text-[10px] font-black text-slate-500 hover:text-blue-400 transition-colors uppercase tracking-widest">
                          <ChevronRight size={12}/>
                          Continue Game
                        </button>
                     </div>
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
