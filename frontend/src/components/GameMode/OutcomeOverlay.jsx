import React, { useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Trophy, ArrowRight, BookOpen, Target, Zap, ShieldCheck, Briefcase, Bot, Sparkles, BrainCircuit } from 'lucide-react';
import { useTrading } from '../../context/TradingContext';
import { getLearningInsight } from '../../utils/learningEngine';
import { formatPrice } from '../../utils/formatters';
import MicroLearningCard from '../MicroLearningCard';

export default function OutcomeOverlay({ stock, choice, gameStep, streak, onNext, onReset, result }) {
  const { decisions, missedOpportunities, userInsights, portfolio } = useTrading();

  // "Aligned" replaces "aligned" — markets don't have right/wrong answers.
  // This only describes whether the choice matched the scenario's direction.
  const aligned = (choice === 'buy' && stock.isPositive) || (choice === 'skip' && !stock.isPositive);
  const impactPct = Math.abs(parseFloat(stock.impact || "0"));
  
  // Dynamic Emotional Feedback
  const getFeedbackDetails = () => {
    if (!result || result.type === 'none') return null;
    
    switch (result.type) {
      case 'gain':
        return {
          message: `This decision added ${formatPrice(result.amount, stock.currency || 'INR')} to your portfolio.`,
          color: 'text-emerald-400',
          bg: 'bg-emerald-500/10',
          border: 'border-emerald-500/20',
          glow: 'shadow-[0_0_30px_rgba(16,185,129,0.3)]'
        };
      case 'loss':
        return {
          message: `This move reduced your portfolio by ${formatPrice(Math.abs(result.amount), stock.currency || 'INR')}.`,
          color: 'text-rose-400',
          bg: 'bg-rose-500/10',
          border: 'border-rose-500/20',
          glow: 'shadow-[0_0_30px_rgba(244,63,94,0.3)]'
        };
      case 'missed':
        return {
          message: `You missed a potential ${formatPrice(result.amount, stock.currency || 'INR')} gain.`,
          color: 'text-amber-400',
          bg: 'bg-amber-500/10',
          border: 'border-amber-500/20',
          glow: 'shadow-[0_0_30px_rgba(245,158,11,0.3)]'
        };
      default:
        return null;
    }
  };

  const feedback = getFeedbackDetails();

  const insight = useMemo(() => {
    return getLearningInsight({
      decisions,
      missedOpportunities,
      holdings: portfolio,
      totalCurrentValue: portfolio.reduce((sum, h) => sum + (h.currentValue || h.invested), 0)
    });
  }, [decisions, missedOpportunities, portfolio]);

  const missedGain = impactPct;

  return (
    <div className="relative min-h-[500px] flex items-center justify-center w-full">
      <AnimatePresence mode="wait">
        {/* PHASE 1: Quick Result Flash */}
        {gameStep === 'FLASH' && (
          <motion.div 
            key="flash"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className={`fixed inset-0 z-[300] flex flex-col items-center justify-center ${aligned ? 'bg-emerald-500/30' : 'bg-rose-500/30'} backdrop-blur-xl`}
          >
             <motion.div 
               initial={{ scale: 0.5, opacity: 0, rotate: -20 }}
               animate={{ scale: 1.5, opacity: 1, rotate: 0 }}
               transition={{ type: "spring", stiffness: 400, damping: 15 }}
               className={`p-10 rounded-full ${aligned ? 'text-emerald-400' : 'text-rose-400'}`}
             >
                <Zap size={120} fill="currentColor" className={aligned ? 'drop-shadow-[0_0_50px_rgba(16,185,129,0.8)]' : 'drop-shadow-[0_0_50px_rgba(244,63,94,0.8)]'} />
             </motion.div>
             
             <motion.div
               initial={{ y: 40, opacity: 0 }}
               animate={{ y: -60, opacity: 1 }}
               transition={{ delay: 0.1, duration: 1, ease: "easeOut" }}
               className="absolute flex flex-col items-center gap-3"
             >
                <span className={`text-3xl font-black uppercase tracking-widest ${aligned ? 'text-emerald-400' : 'text-rose-400'}`}>
                  {aligned ? `${choice === 'buy' ? '+' : ''}${impactPct.toFixed(1)}% Captured` : `${choice === 'buy' ? '-' : 'Missed '}${impactPct.toFixed(1)}%`}
                </span>
                <p className="text-white/60 text-sm font-medium text-center max-w-sm">
                  {aligned
                    ? `${stock.symbol} moved ${stock.impact} in the direction you chose.`
                    : `${stock.symbol} moved ${stock.impact} — opposite to your decision.`}
                </p>
             </motion.div>
          </motion.div>
        )}

        {/* PHASE 2: Objective Card */}
        {gameStep === 'OBJECTIVE' && (
          <motion.div 
            key="objective"
            initial={{ y: 60, opacity: 0, scale: 0.95 }}
            animate={{ y: 0, opacity: 1, scale: 1 }}
            transition={{ type: "spring", damping: 25, stiffness: 200 }}
            className="w-full max-w-lg bg-slate-900 border-2 border-white/5 rounded-[3.5rem] p-10 sm:p-12 shadow-[0_40px_120px_rgba(0,0,0,0.7)] text-center relative overflow-hidden"
          >
            {/* Progression Progress Bar */}
            <div className="absolute top-0 left-0 right-0 h-2 bg-slate-800">
               <motion.div 
                 initial={{ width: 0 }}
                 animate={{ width: `${((streak % 5) || 5) * 20}%` }}
                 className={`h-full ${aligned ? 'bg-emerald-500' : 'bg-blue-500'} shadow-[0_0_20px_rgba(59,130,246,0.5)]`}
               />
            </div>

            <div className="space-y-10">
              <div className="flex flex-col items-center gap-4">
                <div className={`w-20 h-20 rounded-3xl flex items-center justify-center border-2 shadow-2xl ${aligned ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-500' : 'bg-blue-500/10 border-blue-500/20 text-blue-500'}`}>
                  {aligned ? <ShieldCheck size={40} /> : <Trophy size={40} />}
                </div>
                <div className="space-y-1">
                  <span className={`text-[10px] font-black uppercase tracking-[0.5em] block ${aligned ? 'text-emerald-500' : 'text-blue-500'}`}>
                    Outcome
                  </span>
                  <h3 className="text-2xl sm:text-3xl font-black text-white uppercase tracking-tighter">
                    {choice === 'buy' ? (aligned ? `Bought · +${impactPct.toFixed(1)}%` : `Bought · -${impactPct.toFixed(1)}%`)
                                      : (aligned ? `Skipped · avoided -${impactPct.toFixed(1)}%` : `Skipped · missed +${impactPct.toFixed(1)}%`)}
                  </h3>
                  <p className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Scenario outcome · not a verdict on the decision</p>
                </div>
              </div>

              <div className="space-y-6">
                {feedback && (
                  <motion.div 
                    initial={{ scale: 0.9, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    className={`p-6 rounded-[2rem] border ${feedback.bg} ${feedback.border} ${feedback.glow} flex flex-col items-center gap-2 animate-pulse`}
                  >
                    <span className={`text-xl font-bold text-center ${feedback.color}`}>{feedback.message}</span>
                    <span className="text-[8px] font-black text-white/40 uppercase tracking-[0.2em]">
                      Your decision directly affected your balance
                    </span>
                  </motion.div>
                )}

                <div className="bg-white/5 rounded-[2rem] p-8 border border-white/5 relative overflow-hidden text-left">
                  <div className="absolute top-0 right-0 p-4 opacity-5"><Bot size={80} className="text-blue-400" /></div>
                  
                  <div className="flex items-center gap-2 mb-4 relative z-10">
                     <Sparkles size={14} className="text-blue-400" />
                     <span className="text-[10px] font-black text-blue-400 uppercase tracking-widest">What this means</span>
                  </div>

                  <p className="text-white text-base font-medium leading-relaxed relative z-10 pr-10">
                    {typeof result?.aiMessage === "string" ? result.aiMessage : (result?.aiMessage?.text || (
                      choice === 'buy'
                        ? `A simulated ₹1,000 position would have ${aligned ? 'gained' : 'lost'} ≈ ₹${(impactPct * 10).toFixed(0)} on this move. One scenario isn't a signal — track how the pattern holds across your next decisions.`
                        : `By skipping, you ${aligned ? 'avoided a' : 'sat out a'} ${impactPct.toFixed(1)}% move. ${aligned ? 'Passing on uncertain setups is a valid strategy.' : 'Missed gains are part of any disciplined approach — selection bias is the real edge over time.'}`
                    ))}
                  </p>
                  
                  {result?.behaviorHighlight && (
                    <div className="mt-4 inline-flex items-center gap-2 px-3 py-1 bg-white/5 rounded-lg border border-white/10">
                       <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">{result.behaviorHighlight}</span>
                    </div>
                  )}
                </div>

                <div className="text-left">
                   <MicroLearningCard insight={insight} />
                </div>

                {/* Dynamic Behavioral Insight */}
                {userInsights && userInsights.insightMessage && (
                  <motion.div 
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="p-6 rounded-[2rem] bg-blue-600/10 border border-blue-500/20 text-left relative overflow-hidden"
                  >
                    <div className="absolute top-0 right-0 p-4 opacity-5">
                       <BrainCircuit size={40} className="text-blue-400" />
                    </div>
                    <div className="flex items-center gap-2 mb-2 relative z-10">
                       <BrainCircuit size={14} className="text-blue-400" />
                       <span className="text-[10px] font-black text-blue-400 uppercase tracking-widest">Behavioral Pattern</span>
                    </div>
                    <p className="text-xs text-blue-100 font-bold leading-relaxed italic relative z-10">
                      "{typeof userInsights.insightMessage === "string" ? userInsights.insightMessage : userInsights.insightMessage?.text || userInsights.insightMessage?.message || "Analyzing your behavioral DNA..."}"
                    </p>
                  </motion.div>
                )}

                <div className="flex items-center justify-center gap-8">
                  <div className="flex flex-col items-center">
                    <span className="text-[10px] font-black text-slate-600 uppercase tracking-widest mb-1">Decisions logged</span>
                    <span className="text-sm font-black text-white tabular-nums">{decisions?.length ?? 0}</span>
                  </div>
                  <div className="w-px h-8 bg-white/5" />
                  <div className="flex flex-col items-center">
                    <span className="text-[10px] font-black text-slate-600 uppercase tracking-widest mb-1">Open positions</span>
                    <span className="text-sm font-black text-white tabular-nums">{portfolio?.length ?? 0}</span>
                  </div>
                </div>
              </div>

              <div className="flex flex-col gap-3">
                <button 
                  onClick={onNext}
                  className={`w-full py-6 rounded-[2rem] font-black uppercase tracking-widest text-sm flex items-center justify-center gap-3 transition-all shadow-2xl active:scale-95 group ${aligned ? 'bg-white text-slate-950 hover:bg-emerald-50' : 'bg-blue-600 text-white hover:bg-blue-500 shadow-blue-600/20'}`}
                >
                  Next Scenario <ArrowRight size={20} className="group-hover:translate-x-1 transition-transform" />
                </button>
                
                <div className="grid grid-cols-2 gap-3">
                   <button 
                     onClick={onReset}
                     className="py-4 bg-white/5 border border-white/10 text-slate-400 rounded-2xl text-[10px] font-black uppercase tracking-widest hover:text-white hover:bg-white/10 transition-all flex items-center justify-center gap-2"
                   >
                     <Zap size={12}/> Try Again
                   </button>
                   <button 
                     onClick={() => window.open('https://www.google.com/search?q=' + stock.symbol + '+news', '_blank')}
                     className="py-4 bg-white/5 border border-white/10 text-slate-400 rounded-2xl text-[10px] font-black uppercase tracking-widest hover:text-white hover:bg-white/10 transition-all flex items-center justify-center gap-2"
                   >
                     <BookOpen size={12}/> Analysis
                   </button>
                </div>
              </div>
              
              {streak > 1 && (
                <div className="inline-flex items-center gap-2 bg-white/5 px-4 py-1.5 rounded-full border border-white/10">
                  <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest tabular-nums">
                    {streak} scenarios this session
                  </span>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
