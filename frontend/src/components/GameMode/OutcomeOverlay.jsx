import React, { useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Trophy, ArrowRight, BookOpen, Target, Zap, ShieldCheck, Briefcase } from 'lucide-react';
import { useBehavior } from '../../context/BehaviorContext';
import { useTrading } from '../../context/TradingContext';
import { getLearningInsight } from '../../utils/learningEngine';
import MicroLearningCard from '../MicroLearningCard';

const behavioralQuotes = [
  "Patience is the investor's greatest weapon.",
  "Don't trade on emotions, trade on evidence.",
  "Small streaks build massive portfolios.",
  "Risk is not the enemy, ignorance is.",
  "The market is a machine for transferring wealth from the impatient to the patient."
];

export default function OutcomeOverlay({ stock, choice, gameStep, streak, onNext, onReset, result }) {
  const { decisions, missedOpportunities, userInsights } = useBehavior();
  const { portfolio } = useTrading();

  const isCorrect = (choice === 'buy' && stock.isPositive) || (choice === 'skip' && !stock.isPositive);
  
  // Dynamic Emotional Feedback
  const getFeedbackDetails = () => {
    if (!result || result.type === 'none') return null;
    
    switch (result.type) {
      case 'gain':
        return {
          message: `This decision added ₹${result.amount} to your portfolio.`,
          color: 'text-emerald-400',
          bg: 'bg-emerald-500/10',
          border: 'border-emerald-500/20',
          glow: 'shadow-[0_0_30px_rgba(16,185,129,0.3)]'
        };
      case 'loss':
        return {
          message: `This move reduced your portfolio by ₹${Math.abs(result.amount)}.`,
          color: 'text-rose-400',
          bg: 'bg-rose-500/10',
          border: 'border-rose-500/20',
          glow: 'shadow-[0_0_30px_rgba(244,63,94,0.3)]'
        };
      case 'missed':
        return {
          message: `You missed a potential ₹${result.amount} gain.`,
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

  const quote = behavioralQuotes[Math.floor(Math.random() * behavioralQuotes.length)];
  
  const missedGain = Math.abs(parseFloat(stock.impact || "0"));

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
            className={`fixed inset-0 z-[300] flex flex-col items-center justify-center ${isCorrect ? 'bg-emerald-500/30' : 'bg-rose-500/30'} backdrop-blur-xl`}
          >
             <motion.div 
               initial={{ scale: 0.5, opacity: 0, rotate: -20 }}
               animate={{ scale: 1.5, opacity: 1, rotate: 0 }}
               transition={{ type: "spring", stiffness: 400, damping: 15 }}
               className={`p-10 rounded-full ${isCorrect ? 'text-emerald-400' : 'text-rose-400'}`}
             >
                <Zap size={120} fill="currentColor" className={isCorrect ? 'drop-shadow-[0_0_50px_rgba(16,185,129,0.8)]' : 'drop-shadow-[0_0_50px_rgba(244,63,94,0.8)]'} />
             </motion.div>
             
             <motion.div
               initial={{ y: 40, opacity: 0 }}
               animate={{ y: -60, opacity: 1 }}
               transition={{ delay: 0.1, duration: 1, ease: "easeOut" }}
               className="absolute flex flex-col items-center gap-4"
             >
                <span className={`text-4xl font-black uppercase tracking-widest ${isCorrect ? 'text-emerald-400' : 'text-rose-400'}`}>
                  {isCorrect ? 'Excellent Call' : 'Risky Decision'}
                </span>
                <p className="text-white/80 font-bold text-center uppercase tracking-[0.2em] italic max-w-sm">
                  "{quote}"
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
                 className={`h-full ${isCorrect ? 'bg-emerald-500' : 'bg-blue-500'} shadow-[0_0_20px_rgba(59,130,246,0.5)]`}
               />
            </div>

            <div className="space-y-10">
              <div className="flex flex-col items-center gap-4">
                <div className={`w-20 h-20 rounded-3xl flex items-center justify-center border-2 shadow-2xl ${isCorrect ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-500' : 'bg-blue-500/10 border-blue-500/20 text-blue-500'}`}>
                  {isCorrect ? <ShieldCheck size={40} /> : <Trophy size={40} />}
                </div>
                <div className="space-y-1">
                  <span className={`text-[10px] font-black uppercase tracking-[0.5em] block ${isCorrect ? 'text-emerald-500' : 'text-blue-500'}`}>
                    {isCorrect ? 'Precision Gained' : 'Behavioral Insight'}
                  </span>
                  <h3 className="text-3xl sm:text-4xl font-black text-white uppercase tracking-tighter">
                    {choice === 'buy' && isCorrect ? 'Growth Started' : choice === 'buy' ? 'Valuable Lesson' : isCorrect ? 'Safety First' : 'Potential Missed'}
                  </h3>
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
                     <span className="text-[10px] font-black text-blue-400 uppercase tracking-widest">AI Voice Feedback</span>
                  </div>

                  <p className="text-white text-lg font-bold leading-relaxed relative z-10 pr-10">
                    "{result?.aiMessage || (isCorrect 
                      ? `Buying was a good move because the stock price went up. This adds real value to your portfolio.`
                      : choice === 'buy' 
                        ? `The price went down by ${stock.impact}. It is normal to feel an urge to buy, but checking the trend first helps avoid losses.`
                        : `The price went up by ${stock.impact}. You decided to skip, which means you missed a ${missedGain}% gain. Next time, look for strong momentum.`)}"
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
                      "{userInsights.insightMessage}"
                    </p>
                  </motion.div>
                )}

                <div className="flex items-center justify-center gap-6">
                  <div className="flex flex-col items-center">
                    <span className="text-[10px] font-black text-slate-600 uppercase tracking-widest mb-1">Challenge</span>
                    <span className="text-sm font-black text-white">{(streak % 5) || 5}/5</span>
                  </div>
                  <div className="w-px h-8 bg-white/5" />
                  <div className="flex flex-col items-center">
                    <span className="text-[10px] font-black text-slate-600 uppercase tracking-widest mb-1">Mood Effect</span>
                    <span className={`text-sm font-black ${isCorrect ? 'text-emerald-400' : 'text-rose-400'}`}>
                      {isCorrect ? '+15' : '-10'} Intelligence
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex flex-col gap-3">
                <button 
                  onClick={onNext}
                  className={`w-full py-6 rounded-[2rem] font-black uppercase tracking-widest text-sm flex items-center justify-center gap-3 transition-all shadow-2xl active:scale-95 group ${isCorrect ? 'bg-white text-slate-950 hover:bg-emerald-50' : 'bg-blue-600 text-white hover:bg-blue-500 shadow-blue-600/20'}`}
                >
                  {isCorrect ? 'Next Challenge' : 'Try to Recover'} <ArrowRight size={20} className="group-hover:translate-x-1 transition-transform" />
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
              
              <div className="inline-flex items-center gap-2 bg-emerald-500/10 px-6 py-2 rounded-full border border-emerald-500/10">
                <span className="text-xs font-black text-emerald-500 uppercase tracking-widest">
                  Current Streak: {streak} 🔥
                </span>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
