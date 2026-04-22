import React, { useMemo } from 'react';
import { motion } from 'framer-motion';
import { ShoppingCart, SkipForward, ShieldCheck, Zap } from 'lucide-react';
import { getAnalyticalDetails } from '../../utils/gameLogic';

export default function ActionPanel({ onDecision, disabled, stock }) {
  const springConfig = { type: "spring", stiffness: 400, damping: 25 };
  
  // We need to re-run the logic to get the same confidence level or pass it from parent.
  // Since we want it to feel "alive", we can generate it here as well or receive it.
  // For consistency with the card, we'll assume it's stable for the current stock.
  const analytics = useMemo(() => getAnalyticalDetails(stock), [stock?.symbol]);

  return (
    <div className="pt-6 space-y-4">
      <div className="flex items-center justify-between px-2">
        <div className="flex items-center gap-2">
          <ShieldCheck size={14} className="text-blue-500" />
          <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">
            {analytics.confidence.text}
          </span>
        </div>
        <div className="flex items-center gap-1.5 bg-blue-500/10 px-3 py-1 rounded-lg border border-blue-500/10">
          <Zap size={10} className="text-blue-500" />
          <span className="text-[10px] font-black text-blue-500 uppercase tracking-widest">
            {analytics.confidence.value} Confidence
          </span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <motion.button
          whileHover={!disabled ? { scale: 1.02, translateY: -2 } : {}}
          whileTap={!disabled ? { scale: 0.95 } : {}}
          transition={springConfig}
          onClick={() => onDecision('buy')}
          disabled={disabled}
          className="group flex flex-col items-center justify-center gap-1.5 py-4 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-3xl transition-all shadow-xl shadow-emerald-900/20 border border-emerald-500/20"
        >
          <div className="flex items-center gap-2">
            <ShoppingCart size={18} />
            <span className="font-black text-xs uppercase tracking-widest">{disabled ? 'Analyzing...' : 'Buy'}</span>
          </div>
          <span className="text-[8px] font-black uppercase tracking-[0.2em] opacity-60 group-hover:opacity-100 transition-opacity">Trust the signal</span>
        </motion.button>
        
        <motion.button
          whileHover={!disabled ? { scale: 1.02, translateY: -2 } : {}}
          whileTap={!disabled ? { scale: 0.95 } : {}}
          transition={springConfig}
          onClick={() => onDecision('skip')}
          disabled={disabled}
          className="group flex flex-col items-center justify-center gap-1.5 py-4 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-3xl transition-all border border-slate-700 shadow-xl"
        >
          <div className="flex items-center gap-2">
            <SkipForward size={18} />
            <span className="font-black text-xs uppercase tracking-widest">{disabled ? 'Thinking...' : 'Skip'}</span>
          </div>
          <span className="text-[8px] font-black uppercase tracking-[0.2em] opacity-40 group-hover:opacity-100 transition-opacity">Wait for clarity</span>
        </motion.button>
      </div>

      <div className="space-y-1">
        <p className="text-center text-[9px] font-black text-slate-600 uppercase tracking-[0.25em] pt-2">
          No one knows the perfect answer. Only probabilities.
        </p>
        <p className="text-center text-[8px] font-bold text-slate-500/50 uppercase tracking-widest italic">
          Most beginners hesitate here.
        </p>
      </div>
    </div>
  );
}

