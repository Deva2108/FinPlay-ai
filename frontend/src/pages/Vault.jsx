import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  Shield, Zap, Lock, ChevronRight, Play, TrendingUp, 
  Eye, ShoppingCart, BarChart3, Target, Trophy, BrainCircuit, Loader2
} from 'lucide-react';
import { getVaultDaily, getVaultCards } from '../services/api';
import { useTrading } from '../context/TradingContext';

const VaultGame = ({ scenario, onComplete }) => {
  const [showOutcome, setShowOutcome] = useState(false);
  const [selectedAction, setSelectedAction] = useState(null);

  const handleAction = (action) => {
    setSelectedAction(action);
    setShowOutcome(true);
  };

  if (!scenario) return null;

  return (
    <div className="space-y-8">
      <AnimatePresence mode="wait">
        {!showOutcome ? (
          <motion.div 
            key="game"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="space-y-8"
          >
            <div className="bg-slate-900/40 border border-white/5 p-10 rounded-[3rem] relative overflow-hidden shadow-2xl backdrop-blur-xl">
               <div className="absolute top-0 right-0 p-8 opacity-5">
                  <BrainCircuit size={120} className="text-blue-500" />
               </div>
               
               <div className="relative z-10 space-y-8">
                  <div className="flex items-center justify-between">
                     <div className="px-4 py-1.5 bg-blue-500/10 border border-blue-500/20 rounded-full text-blue-400 text-[9px] font-black uppercase tracking-[0.3em]">
                        Daily Scenario
                     </div>
                  </div>

                  <div className="p-8 bg-white/5 rounded-[2rem] border border-white/5">
                     <p className="text-xl font-bold text-white leading-relaxed italic">
                        "{scenario.scenario}"
                     </p>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                     {scenario.options.map((opt) => (
                        <button 
                           key={opt}
                           onClick={() => handleAction(opt)}
                           className={`flex flex-col items-center gap-4 p-6 border rounded-2xl transition-all group ${
                             opt === 'BUY' ? 'bg-emerald-500/10 border-emerald-500/20 hover:bg-emerald-500/20' :
                             opt === 'WATCHLIST' ? 'bg-blue-500/10 border-blue-500/20 hover:bg-blue-500/20' :
                             'bg-slate-500/10 border-slate-500/20 hover:bg-slate-500/20'
                           }`}
                        >
                           <div className="w-12 h-12 rounded-xl bg-white/5 flex items-center justify-center">
                              {opt === 'BUY' ? <ShoppingCart size={20} className="text-emerald-500" /> :
                               opt === 'WATCHLIST' ? <Eye size={20} className="text-blue-500" /> :
                               <Zap size={20} className="text-slate-400" />}
                           </div>
                           <span className="text-[10px] font-black uppercase tracking-[0.3em]">{opt}</span>
                        </button>
                     ))}
                  </div>
               </div>
            </div>
          </motion.div>
        ) : (
          <motion.div 
            key="outcome"
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            className="bg-slate-900 border border-blue-500/20 p-12 rounded-[3rem] text-center space-y-8 shadow-2xl backdrop-blur-2xl"
          >
             <div className={`w-24 h-24 rounded-[2rem] flex items-center justify-center mx-auto border ${
               selectedAction === scenario.correctAnswer ? 'bg-emerald-500/10 border-emerald-500/20' : 'bg-rose-500/10 border-rose-500/20'
             }`}>
                <Trophy size={48} className={selectedAction === scenario.correctAnswer ? 'text-emerald-500' : 'text-rose-500'} />
             </div>
             <div className="space-y-3">
                <h3 className="text-3xl font-black text-white uppercase tracking-tight">
                  {selectedAction === scenario.correctAnswer ? 'Perfect Call' : 'Learning Moment'}
                </h3>
                <p className="text-slate-400 text-sm font-medium leading-relaxed max-w-sm mx-auto">
                   Choosing <span className="text-blue-400 font-black tracking-widest">{selectedAction}</span> against a <span className="text-white font-black tracking-widest">{scenario.correctAnswer}</span> signal.
                </p>
             </div>
             <div className="space-y-4">
                <div className="p-6 bg-white/5 rounded-2xl border border-white/5 text-left">
                    <p className="text-[10px] font-black text-blue-500 uppercase tracking-widest mb-2">Analysis</p>
                    <p className="text-sm text-blue-100/90 font-medium leading-relaxed italic">"{scenario.explanation}"</p>
                </div>
                <div className="p-6 bg-emerald-500/5 rounded-2xl border border-emerald-500/10 text-left">
                    <p className="text-[10px] font-black text-emerald-500 uppercase tracking-widest mb-2">Lesson</p>
                    <p className="text-sm text-emerald-100/90 font-medium leading-relaxed">"{scenario.learning}"</p>
                </div>
             </div>
             <button 
                onClick={onComplete}
                className="w-full py-5 bg-white text-slate-950 font-black rounded-2xl uppercase tracking-[0.3em] text-[10px] hover:bg-slate-200 transition-all flex items-center justify-center gap-3"
             >
                Close Challenge <ChevronRight size={16} />
             </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default function Vault() {
  const [isPlaying, setIsPlaying] = useState(false);
  const [scenario, setScenario] = useState(null);
  const [loading, setLoading] = useState(false);
  const [cards, setCards] = useState([]);
  const { decisions = [], userInsights } = useTrading();

  const stats = React.useMemo(() => {
    const total = decisions.length;
    const actioned = decisions.filter(d => d?.action && d.action !== 'SKIP').length;
    const conviction = total > 0 ? Math.round((actioned / total) * 100) : null;
    return { total, conviction };
  }, [decisions]);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [scenarioRes, cardsRes] = await Promise.all([getVaultDaily(), getVaultCards()]);
      if (scenarioRes.success) setScenario(scenarioRes.data);
      if (cardsRes.success) setCards(cardsRes.data);
    } catch (err) {
      console.error("Vault fetch failed", err);
    } finally {
      setLoading(false);
    }
  };

  if (loading && !isPlaying) {
    return (
      <div className="h-96 flex flex-col items-center justify-center gap-4">
        <Loader2 size={32} className="animate-spin text-blue-500" />
        <p className="text-[10px] font-black text-blue-500 uppercase tracking-[0.3em]">Opening The Vault...</p>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-6 py-12 space-y-12">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
        <div className="space-y-4">
          <div className="flex items-center gap-3">
             <div className="p-3 bg-blue-600 rounded-2xl shadow-xl shadow-blue-600/20">
                <Shield size={24} className="text-white" />
             </div>
             <h1 className="text-4xl font-black text-white tracking-tighter uppercase">The Vault</h1>
          </div>
          <p className="text-slate-500 font-black uppercase tracking-[0.2em] text-[10px]">Daily scenarios · decision journal · behavioral feedback</p>
        </div>
        {userInsights?.behaviorType && userInsights.behaviorType !== 'neutral' && (
          <div className="flex items-center gap-4">
             <div className="text-right">
                <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Your Archetype</p>
                <p className="text-sm font-black text-blue-400 uppercase tracking-tighter">{userInsights.behaviorType}</p>
             </div>
          </div>
        )}
      </div>

      <AnimatePresence mode="wait">
        {!isPlaying ? (
          <motion.div 
            key="intro"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95 }}
            className="grid grid-cols-1 lg:grid-cols-10 gap-8"
          >
            {/* Primary Challenge Card */}
            <div className="lg:col-span-6 space-y-6">
              <div className="group relative">
                <div className="absolute inset-0 bg-blue-600/10 blur-3xl rounded-[3rem] group-hover:bg-blue-600/20 transition-all" />
                <div className="relative bg-slate-900/60 border border-white/10 p-12 rounded-[3.5rem] backdrop-blur-xl space-y-10 overflow-hidden">
                  <div className="absolute -top-10 -right-10 opacity-5 group-hover:rotate-12 transition-transform">
                     <Zap size={200} className="text-blue-500" />
                  </div>
                  
                  <div className="space-y-4">
                    <div className="px-3 py-1 bg-emerald-500/20 border border-emerald-500/20 rounded-full text-[9px] font-black text-emerald-400 uppercase tracking-widest inline-block">Daily Active</div>
                    <h2 className="text-4xl font-black text-white leading-none tracking-tight">STRATEGY SCAN</h2>
                    <p className="text-slate-400 text-sm font-medium leading-relaxed max-w-sm">
                      Master the psychology of entries and exits with our AI-curated daily scenario.
                    </p>
                  </div>

                  <button 
                    onClick={() => setIsPlaying(true)}
                    disabled={!scenario}
                    className="w-full py-6 bg-white text-slate-950 font-black rounded-2xl uppercase tracking-[0.2em] text-xs hover:bg-blue-50 transition-all shadow-2xl flex items-center justify-center gap-4 group disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {scenario ? 'Enter Arena' : 'Syncing...'} <Play size={16} fill="currentColor" className="group-hover:translate-x-1 transition-transform" />
                  </button>
                </div>
              </div>

              {/* Stats Section — sourced from real user decision data */}
              <div className="grid grid-cols-2 gap-4">
                 <div className="p-6 bg-slate-900/40 border border-white/5 rounded-[2rem] space-y-1">
                    <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Decisions Logged</p>
                    <p className="text-2xl font-black text-white tabular-nums">{stats.total}</p>
                    <p className="text-[10px] font-bold text-slate-500">{stats.total === 0 ? 'Start your first scenario' : stats.total < 5 ? 'Keep building your track record' : 'Your behavioral profile is forming'}</p>
                 </div>
                 <div className="p-6 bg-slate-900/40 border border-white/5 rounded-[2rem] space-y-1">
                    <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Conviction Rate</p>
                    <p className="text-2xl font-black text-emerald-500 tabular-nums">{stats.conviction == null ? '--' : `${stats.conviction}%`}</p>
                    <p className="text-[10px] font-bold text-slate-500">Acted vs. skipped — higher = more decisive</p>
                 </div>
              </div>
            </div>

            {/* Content Modules */}
            <div className="lg:col-span-4 space-y-6">
               {cards.filter(c => c.locked).map((card, idx) => (
                 <div key={idx} className="p-8 rounded-[3rem] bg-slate-900/40 border border-white/5 border-dashed relative group">
                    <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                       <div className="px-4 py-2 bg-slate-800 rounded-xl text-[10px] font-black text-white uppercase tracking-widest border border-white/10 shadow-2xl">{card.ctaLabel}</div>
                    </div>
                    <div className="space-y-6 group-hover:blur-sm transition-all grayscale opacity-50">
                       <div className="w-12 h-12 bg-slate-800 rounded-xl flex items-center justify-center">
                          <Lock size={20} className="text-slate-600" />
                       </div>
                       <div className="space-y-2">
                          <h4 className="text-lg font-black text-slate-400 uppercase tracking-tight">{card.title}</h4>
                          <p className="text-[10px] text-slate-600 font-bold uppercase tracking-widest">{card.subtitle}</p>
                       </div>
                    </div>
                 </div>
               ))}
            </div>
          </motion.div>
        ) : (
          <VaultGame scenario={scenario} onComplete={() => { setIsPlaying(false); }} />
        )}
      </AnimatePresence>
    </div>
  );
}
