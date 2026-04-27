import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Info, TrendingUp, TrendingDown, Lightbulb, Target, Activity, Briefcase, Zap, Loader2 } from 'lucide-react';
import MiniChart from './MiniChart';
import InfoTooltip from './InfoTooltip';
import { getIndexInsight } from '../services/api';
import { useMarket } from '../context/MarketContext';

export default function MarketInsightPanel({ isOpen, onClose, indexData, onTryGame }) {
  const { marketMode } = useMarket();
  const [timeframe, setTimeframe] = useState('1D');
  const [userDecision, setUserDecision] = useState(null);
  const [aiInsight, setAiInsight] = useState(null);
  const [loadingInsight, setLoadingInsight] = useState(false);

  // Prevent body scroll when open
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
      setUserDecision(null); // Reset choice on open
      fetchAiInsight();
    } else {
      document.body.style.overflow = 'auto';
    }
    return () => {
      document.body.style.overflow = 'auto';
    };
  }, [isOpen, indexData]);

  const fetchAiInsight = async () => {
    if (!indexData) return;
    setLoadingInsight(true);
    try {
      const res = await getIndexInsight(indexData.symbol, indexData.value, indexData.change, marketMode);
      setAiInsight(res);
    } catch (err) {
      console.error("AI Insight Error", err);
    } finally {
      setLoadingInsight(false);
    }
  };

  if (!indexData) return null;

  const isPositive = (indexData.change || "").startsWith('+') || (indexData.changesPercentage >= 0);
  const accentColor = isPositive ? 'text-emerald-400' : 'text-rose-400';

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-slate-950/80 backdrop-blur-md z-[100]"
          />

          {/* Panel */}
          <motion.div
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', damping: 25, stiffness: 200 }}
            className="fixed top-0 right-0 w-full md:w-[450px] h-screen bg-slate-900 border-l border-white/10 z-[101] shadow-2xl flex flex-col overflow-hidden"
          >
            {/* Header */}
            <div className="p-6 border-b border-white/5 flex items-center justify-between sticky top-0 bg-slate-900/90 backdrop-blur-md z-10">
              <div className="space-y-1">
                <h3 className="text-2xl font-black text-white tracking-tight flex items-center gap-2">
                  {indexData.symbol}
                  <span className="text-[10px] px-2 py-0.5 rounded-md bg-blue-500/10 text-blue-400 border border-blue-500/20">
                    INDEX
                  </span>
                </h3>
                <div className="flex items-center gap-3">
                  <span className="text-xl font-bold text-slate-200">{indexData.value}</span>
                  <span className={`text-sm font-black ${accentColor}`}>
                    {indexData.change} ({indexData.percent || indexData.changesPercentage + '%'})
                  </span>
                </div>
              </div>
              <button 
                onClick={onClose}
                className="p-3 text-slate-400 hover:text-white bg-white/5 hover:bg-white/10 rounded-2xl transition-all"
              >
                <X size={24} />
              </button>
            </div>

            {/* Scrollable Content Container */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6 no-scrollbar pb-32">
              
              {/* Mini Chart Section */}
              <section className="space-y-4">
                <div className="flex items-center justify-between">
                  <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
                    <Activity size={12} /> <InfoTooltip concept="volatility">Live Market Momentum</InfoTooltip>
                  </h4>
                  <div className="flex gap-1 bg-white/5 p-1 rounded-xl border border-white/5">
                    {['1D', '1W', '1M'].map((tf) => (
                      <button
                        key={tf}
                        onClick={() => setTimeframe(tf)}
                        className={`px-3 py-1 rounded-lg text-[10px] font-black transition-all ${
                          timeframe === tf ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/20' : 'text-slate-500 hover:text-slate-300'
                        }`}
                      >
                        {tf}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="bg-slate-950/50 rounded-3xl border border-white/5 p-4 relative overflow-hidden group/chart">
                   <div className="absolute inset-0 bg-blue-500/5 opacity-0 group-hover/chart:opacity-100 transition-opacity pointer-events-none" />
                   <MiniChart timeframe={timeframe} color={isPositive ? '#10b981' : '#f43f5e'} />
                </div>
              </section>

              {/* Performance Summary */}
              <section className="px-6 py-4 border border-blue-500/10 rounded-2xl bg-blue-500/5 flex items-center justify-between">
                <p className="text-xs font-black text-blue-400 uppercase tracking-widest">Global impact</p>
                <span className={`text-sm font-black ${accentColor}`}>
                  {isPositive ? 'Accumulation' : 'Distribution'} Phase
                </span>
              </section>

              {/* Learning Block */}
              <section className="space-y-3">
                <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
                  <Info size={12} /> Educational Context
                </h4>
                <div className="p-6 rounded-[2rem] bg-white/5 border border-white/5 space-y-4">
                  {loadingInsight ? (
                    <div className="flex flex-col items-center gap-3 py-4">
                      <Loader2 size={20} className="animate-spin text-blue-500" />
                      <p className="text-[10px] font-black text-blue-500 uppercase tracking-widest animate-pulse">Syncing Macro Intelligence...</p>
                    </div>
                  ) : (
                    <>
                      <p className="text-sm text-slate-300 font-medium leading-relaxed italic">
                        "{aiInsight?.explanation || "This index represents the pulse of the major sectors. When it moves, the entire market shifts its stance."}"
                      </p>
                      <div className="text-[11px] font-bold p-4 rounded-2xl bg-blue-500/10 text-blue-400 flex items-start gap-3 border border-blue-500/20">
                        <Lightbulb size={16} className="shrink-0 mt-0.5" />
                        <p>{aiInsight?.observation || (isPositive ? "Current strength suggests high institutional confidence. Small pullbacks could be healthy." : "Heightened volatility observed. This is where disciplined traders separate from the impulsive.")}</p>
                      </div>
                    </>
                  )}
                </div>
              </section>

              {/* User Behavioral Check */}
              <section className="space-y-4 pt-4">
                <div className="flex items-center gap-3">
                  <div className="p-2 bg-purple-600/10 rounded-lg text-purple-400">
                    <Target size={14} />
                  </div>
                  <h4 className="text-[10px] font-black text-white uppercase tracking-widest">Self-Check: What's your move?</h4>
                </div>
                <div className="grid grid-cols-1 gap-2">
                   {[
                     { label: 'Strike with Momentum', value: 'Buy Leaders', icon: <Zap size={12}/> },
                     { label: 'Wait for Confirmation', value: 'Wait', icon: <Activity size={12}/> },
                     { label: 'Defensive Stance', value: 'Avoid Market', icon: <Shield size={12}/> }
                   ].map((opt) => (
                     <button 
                       key={opt.value}
                       onClick={() => setUserDecision(opt.value)}
                       className={`py-4 px-5 rounded-2xl text-[10px] font-black uppercase tracking-widest transition-all flex items-center justify-between border ${
                         userDecision === opt.value 
                          ? 'bg-blue-600 border-blue-500 text-white shadow-xl scale-[1.02]' 
                          : 'bg-white/5 border-white/5 text-slate-400 hover:bg-white/10'
                       }`}
                     >
                       <span className="flex items-center gap-2">{opt.icon} {opt.label}</span>
                       {userDecision === opt.value && <div className="w-1.5 h-1.5 bg-white rounded-full animate-pulse" />}
                     </button>
                   ))}
                </div>
              </section>

            </div>

            {/* Action Footer */}
            <div className="p-6 bg-slate-900 border-t border-white/5 flex flex-col gap-3 z-20 sticky bottom-0 backdrop-blur-xl">
               <button 
                 onClick={() => {
                   onTryGame?.(indexData);
                   onClose();
                 }}
                 className="w-full py-4 rounded-2xl bg-blue-600 hover:bg-blue-500 text-white text-[11px] font-black uppercase tracking-[0.2em] transition-all shadow-2xl shadow-blue-600/20 flex items-center justify-center gap-3 group"
               >
                 <Zap size={16} className="group-hover:scale-125 transition-transform" /> Enter Decision Arena
               </button>
               <div className="grid grid-cols-2 gap-2">
                  <button className="py-3 rounded-xl bg-white/5 hover:bg-white/10 text-white text-[9px] font-black uppercase tracking-widest border border-white/5 transition-all">
                    Track Sector
                  </button>
                  <button className="py-3 rounded-xl bg-white/5 hover:bg-white/10 text-white text-[9px] font-black uppercase tracking-widest border border-white/5 transition-all">
                    View Holdings
                  </button>
               </div>
            </div>

          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
