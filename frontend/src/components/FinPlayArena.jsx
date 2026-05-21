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
import { useInsight } from '../hooks/useInsight';
import { trackDecision, getDecisionStats, getArenaSummary, getArenaScenarios, api, API_ENDPOINTS } from '../services/api';
import { useNavigate } from 'react-router-dom';

export default function FinPlayArena({ marketMode = "INDIA", onDecisionMade, onShowInsight, context }) {
  const navigate = useNavigate();
  const [stocks, setStocks] = useState([]);
  const scenarioParams = useMemo(() => ({ marketType: marketMode }), [marketMode]);
  const { data: scenarioData, status: scenarioStatus } = useInsight(API_ENDPOINTS.AI.ONBOARDING.SCENARIOS, scenarioParams);
  const loadingScenarios = scenarioStatus === 'SYNCING';

  useEffect(() => {
    if (scenarioData?.scenarios) setStocks(scenarioData.scenarios);
  }, [scenarioData]);
  
  const { balance, executeBuy, recordDecision, missedOpportunities, decisions, loading: tradingLoading } = useTrading();
  const { openStockPanel } = useStockPanel();

  const [currentIdx, setCurrentIdx] = useState(0);
  const [gameStep, setGameStep] = useState('DECISION'); 
  const [quickFlash, setQuickFlash] = useState(null); 
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [roundDecisions, setRoundDecisions] = useState([]);
  const [diagnosis, setDiagnosis] = useState(null);
  const [isDiagnosing, setIsDiagnosing] = useState(false);

  const ROUND_LIMIT = 5;
  const currentStock = stocks[currentIdx];
  const insightScore = parseInt(localStorage.getItem('insightScore') || '0', 10);

  const handleDecision = async (choice) => {
    if (!currentStock || isSubmitting) return;
    setIsSubmitting(true);
    
    const decision = { stock: currentStock, action: choice.toUpperCase(), timestamp: Date.now() };
    setRoundDecisions(prev => [...prev, decision]);
    
    // Visual feedback
    setQuickFlash((choice === 'buy' && currentStock?.isPositive) || (choice === 'skip' && !currentStock?.isPositive) ? 'correct' : 'wrong');
    
    await recordDecision(choice.toUpperCase(), currentStock, marketMode);

    setTimeout(() => {
      setQuickFlash(null);
      if (roundDecisions.length + 1 >= ROUND_LIMIT) {
        setGameStep('SUMMARY');
        generateDiagnosis([...roundDecisions, decision]);
      } else {
        setCurrentIdx(prev => (prev + 1) % (stocks.length || 1));
      }
      setIsSubmitting(false);
    }, 400);
  };

  const generateDiagnosis = async (history) => {
    setIsDiagnosing(true);
    try {
      const res = await getArenaSummary(history);
      setDiagnosis(res?.data?.diagnosis || res?.diagnosis || "Analysis complete.");
    } finally {
      setIsDiagnosing(false);
    }
  };

  if (loadingScenarios && stocks.length === 0) {
    return <div className="h-96 flex flex-col items-center justify-center gap-4"><Loader2 size={32} className="animate-spin text-blue-500" /><p className="text-[10px] font-black text-blue-500 uppercase tracking-[0.3em]">Syncing Arena...</p></div>;
  }

  if (gameStep === 'SUMMARY') {
    return (
      <div className="max-w-2xl mx-auto py-10">
        <div className="bg-slate-900 border border-blue-500/20 rounded-[3rem] p-10 text-center space-y-8 shadow-2xl relative overflow-hidden">
          <div className="absolute top-0 right-0 p-6 opacity-5"><BrainCircuit size={120} className="text-blue-400" /></div>
          <h2 className="text-4xl font-black text-white tracking-tighter">Arena <span className="text-blue-500">Insights</span></h2>
          <div className="bg-white/5 p-8 rounded-[2rem] text-left">
            {isDiagnosing ? <Loader2 size={24} className="animate-spin text-blue-500 mx-auto" /> : <p className="text-lg font-bold leading-relaxed text-blue-100 italic">"{diagnosis || "Your patterns are being processed..."}"</p>}
          </div>
          <button onClick={() => window.location.reload()} className="w-full py-5 bg-blue-600 text-white font-black rounded-2xl uppercase tracking-widest text-xs">Enter Next Round</button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between px-4">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-blue-600 rounded-xl shadow-lg shadow-blue-600/20"><Zap size={20} className="text-white" /></div>
          <h2 className="text-xl font-black text-white uppercase tracking-tighter">Decision Arena</h2>
        </div>
        <div className="flex items-center gap-4 bg-slate-900/40 border border-slate-800 px-6 py-3 rounded-2xl">
          <div className="flex flex-col items-center px-4 border-r border-white/5"><span className="text-[10px] font-black text-blue-500 uppercase tracking-widest">Insights</span><span className="text-sm font-black text-white">{insightScore}</span></div>
          <div className="flex flex-col items-end"><span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Progress</span><span className="text-sm font-black text-white">{currentIdx + 1} / {stocks.length}</span></div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-10 gap-8 items-start">
        <div className="lg:col-span-7 relative rounded-[2.5rem] overflow-hidden">
          <AnimatePresence>{quickFlash && <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }} className={`absolute inset-0 z-[50] pointer-events-none ${quickFlash === 'correct' ? 'bg-emerald-500/40' : 'bg-rose-500/40'} backdrop-blur-sm`} />}</AnimatePresence>
          {currentStock && (
            <motion.div key={currentIdx} initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }}>
              <GameStockCard stock={currentStock} onClick={() => openStockPanel(currentStock)} />
              <ActionPanel onDecision={handleDecision} stock={currentStock} disabled={isSubmitting} />
            </motion.div>
          )}
        </div>
        <div className="lg:col-span-3"><LiveContextPanel stock={currentStock} onShowInsight={onShowInsight} /></div>
      </div>
    </div>
  );
}
