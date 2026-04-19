import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Info, TrendingUp, TrendingDown, Lightbulb, Target, Activity, Briefcase, Zap } from 'lucide-react';
import MiniChart from './MiniChart';
import InfoTooltip from './InfoTooltip';

export default function MarketInsightPanel({ isOpen, onClose, indexData, onTryGame }) {
  const [timeframe, setTimeframe] = useState('1D');
  const [userDecision, setUserDecision] = useState(null);

  // Prevent body scroll when open
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
      setUserDecision(null); // Reset choice on open
    } else {
      document.body.style.overflow = 'auto';
    }
    return () => {
      document.body.style.overflow = 'auto';
    };
  }, [isOpen]);

  if (!indexData) return null;

  const isPositive = indexData.change && indexData.change.startsWith('+');
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
                    {indexData.change} ({indexData.percent})
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
                    <Activity size={12} /> <InfoTooltip concept="volatility">Live Trend</InfoTooltip>
                  </h4>
                  <div className="flex gap-1 bg-white/5 p-1 rounded-xl border border-white/5">
                    {['1D', '1W', '1M'].map((tf) => (
                      <button
                        key={tf}
                        onClick={() => setTimeframe(tf)}
                        className={`px-3 py-1 rounded-lg text-[10px] font-black transition-all ${
                          timeframe === tf ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'
                        }`}
                      >
                        {tf}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="bg-slate-950/50 rounded-3xl border border-white/5 p-4 relative overflow-hidden">
                   <MiniChart timeframe={timeframe} color={isPositive ? '#10b981' : '#f43f5e'} />
                </div>
              </section>

              {/* ✅ STEP 3: IMPACT PREVIEW */}
              <section className="px-4 py-2 border border-white/5 rounded-2xl bg-white/[0.02]">
                <p className="text-sm font-bold text-slate-400 opacity-70">
                  If you invested yesterday: <span className={accentColor}>+0.56% today</span>
                </p>
              </section>

              {/* Learning Block */}
              <section className="space-y-3">
                <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
                  <Info size={12} /> Insight
                </h4>
                <div className="p-5 rounded-3xl bg-white/5 border border-white/5 space-y-3">
                  <p className="text-sm text-slate-300 font-medium leading-relaxed">
                    This index tracks the performance of key companies. A rising trend indicates bullish market sentiment and sector strength.
                  </p>
                  <div className="text-[11px] font-bold p-3 rounded-xl bg-blue-500/10 text-blue-400 flex items-center gap-2">
                    <Lightbulb size={14} />
                    {isPositive ? "Positive momentum detected." : "Current trend shows a short-term cooling period."}
                  </div>
                </div>
              </section>

              {/* ✅ STEP 4: PERSONAL CONTEXT */}
              <section className="p-4 rounded-2xl bg-white/5 border border-white/5 flex items-start gap-3">
                 <div className="p-2 bg-blue-500/10 rounded-lg shrink-0 text-blue-400">
                    <Activity size={14} />
                 </div>
                 <div className="space-y-1">
                    <h5 className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Your Context</h5>
                    <p className="text-xs font-bold text-slate-300 leading-relaxed">
                      You are currently focused on 1 sector. This trend may increase your risk.
                    </p>
                 </div>
              </section>

              {/* ✅ STEP 2: WHAT WOULD YOU DO? BLOCK */}
              <section className="space-y-4 pt-4 border-t border-white/5">
                <div className="flex items-center gap-2">
                  <Target size={14} className="text-blue-500" />
                  <h4 className="text-[10px] font-black text-white uppercase tracking-widest">If this trend continues, what would you do?</h4>
                </div>
                <div className="grid grid-cols-1 gap-2">
                   {['Buy Leaders', 'Wait', 'Avoid Market'].map((option) => (
                     <button 
                       key={option}
                       onClick={() => setUserDecision(option)}
                       className={`py-3 px-4 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all border ${
                         userDecision === option 
                          ? 'bg-blue-600 border-blue-500 text-white' 
                          : 'bg-white/5 border-white/5 text-slate-400 hover:bg-white/10'
                       }`}
                     >
                       {option}
                     </button>
                   ))}
                </div>
              </section>

              {/* Smart Insight */}
              <section className="space-y-3">
                <h4 className="text-[10px] font-black text-emerald-400 uppercase tracking-widest flex items-center gap-2">
                  <Zap size={12} /> Smart Insight
                </h4>
                <div className="p-4 rounded-2xl bg-emerald-500/5 border border-emerald-500/10 flex items-start gap-3">
                   <div className="p-2 bg-emerald-500/10 rounded-lg shrink-0 text-emerald-400">
                      <Zap size={14} />
                   </div>
                   <p className="text-[11px] font-bold text-slate-300 leading-relaxed">
                     Broad market sentiment is 72% Bullish. Large-cap stocks are leading the rally with strong volume support.
                   </p>
                </div>
              </section>

            </div>

            {/* ✅ STEP 1: ACTION FOOTER */}
            <div className="p-6 bg-slate-900 border-t border-white/5 flex flex-wrap gap-2 z-20 sticky bottom-0">
               <button 
                 onClick={() => {
                   console.log("TRY_GAME_TRIGGERED", indexData?.symbol);
                   onTryGame?.(indexData?.symbol);
                   onClose();
                 }}
                 className="flex-1 min-w-[120px] py-3 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-[10px] font-black uppercase tracking-widest transition-all shadow-lg shadow-blue-600/20 flex items-center justify-center gap-2"
               >
                 <Zap size={14} /> Try in Game
               </button>
               <button 
                 onClick={() => console.log("action")}
                 className="flex-1 min-w-[120px] py-3 px-4 rounded-xl bg-white/10 hover:bg-white/20 text-white text-[10px] font-black uppercase tracking-widest transition-all flex items-center justify-center gap-2"
               >
                 <Briefcase size={14} /> Add to Watchlist
               </button>
               <button 
                 onClick={() => console.log("action")}
                 className="w-full py-3 px-4 rounded-xl bg-white/5 hover:bg-white/10 text-white text-[10px] font-black uppercase tracking-widest border border-white/5 transition-all"
               >
                 Explore Stocks
               </button>
            </div>

          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
