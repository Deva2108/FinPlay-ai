import React, { useMemo } from 'react';
import { motion } from 'framer-motion';
import { Target, Zap, Lightbulb, ChevronRight, Info } from 'lucide-react';
import { useBehavior } from '../context/BehaviorContext';
import { useTrading } from '../context/TradingContext';
import { getLearningInsight } from '../utils/learningEngine';

const NextEdgeCard = () => {
  const { decisions = [], missedOpportunities = [] } = useBehavior();
  const { portfolio = [] } = useTrading();

  const adaptiveInsight = useMemo(() => {
    return getLearningInsight({
      decisions: decisions || [],
      missedOpportunities: missedOpportunities || [],
      holdings: portfolio || [],
      totalCurrentValue: (portfolio || []).reduce((sum, h) => sum + (h.currentValue || h.invested || 0), 0)
    });
  }, [decisions, missedOpportunities, portfolio]);

  const stats = useMemo(() => {
    const safeDecisions = decisions || [];
    const total = safeDecisions.length;
    if (total === 0) return null;

    const skips = safeDecisions.filter(d => d.action === 'skip').length;
    const buys = safeDecisions.filter(d => d.action === 'buy').length;
    const skipRate = skips / total;
    const buyRate = buys / total;

    // Calculate missed value: assume ₹10,000 base investment per setup
    const missedValue = missedOpportunities.reduce((sum, opp) => {
      const gainPercent = parseFloat(opp.potentialGain) || 0;
      return sum + (gainPercent * 100);
    }, 0);

    let state = "balanced";
    if (skipRate > 0.6) state = "cautious";
    else if (buyRate > 0.7) state = "aggressive";

    return { total, skipRate, buyRate, missedValue: Math.round(missedValue), state };
  }, [decisions, missedOpportunities]);

  const content = {
    cautious: {
      tag: adaptiveInsight?.topic || "Cautious Player",
      insight: adaptiveInsight?.message || `You are playing too safe. You skipped ${Math.round(stats?.skipRate * 100 || 0)}% of setups recently.`,
      impact: stats?.missedValue > 0 ? `+₹${stats.missedValue}` : "+₹180",
      suggestion: adaptiveInsight?.explanation || "Take 1 controlled entry to build conviction.",
      color: "from-indigo-600/20 to-purple-600/20",
      border: "border-indigo-500/30"
    },
    aggressive: {
      tag: adaptiveInsight?.topic || "Momentum Chaser",
      insight: adaptiveInsight?.message || `High entry speed detected. You are buying ${Math.round(stats?.buyRate * 100 || 0)}% of setups.`,
      impact: "-₹450",
      suggestion: adaptiveInsight?.explanation || "Wait for a 2% pullback before the next entry.",
      color: "from-orange-600/20 to-rose-600/20",
      border: "border-orange-500/30"
    },
    balanced: {
      tag: adaptiveInsight?.topic || "Balanced Thinker",
      insight: adaptiveInsight?.message || "Strong discipline. Your risk-to-reward ratio is improving.",
      impact: stats?.missedValue > 0 ? `+₹${stats.missedValue}` : "+₹320",
      suggestion: adaptiveInsight?.explanation || "Explore mid-cap sectors to diversify your edge.",
      color: "from-emerald-600/20 to-teal-600/20",
      border: "border-emerald-500/30"
    },
    empty: {
      tag: "Beginner",
      insight: "The Arena is waiting for your first decision.",
      impact: "₹0",
      suggestion: "Make 3 decisions in the Arena to unlock your edge.",
      color: "from-slate-600/20 to-slate-800/20",
      border: "border-slate-500/30"
    }
  };

  const active = stats ? content[stats.state] : content.empty;

  return (
    <motion.div 
      whileHover={{ scale: 1.01 }}
      className={`relative overflow-hidden rounded-[2rem] border ${active.border} bg-gradient-to-br ${active.color} p-6 shadow-2xl backdrop-blur-sm transition-all duration-300 group`}
    >
      {/* Background Glows */}
      <div className="absolute -right-20 -top-20 h-40 w-40 rounded-full bg-blue-500/10 blur-3xl" />
      <div className="absolute -bottom-20 -left-20 h-40 w-40 rounded-full bg-purple-500/10 blur-3xl" />

      <div className="relative z-10 space-y-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10 text-white shadow-inner">
              <Target size={20} />
            </div>
            <h3 className="text-sm font-black uppercase tracking-widest text-white">Your Next Edge</h3>
          </div>
          <div className="flex items-center gap-1 rounded-full bg-white/10 px-3 py-1 text-[9px] font-black uppercase tracking-tighter text-blue-300">
             <Zap size={10} className="animate-pulse" /> {stats ? "AI Analysis Active" : "Waiting for Data"}
          </div>
        </div>

        <div className="space-y-2">
          <p className="text-base font-bold leading-tight text-white/90">
            {active.insight}
          </p>
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-black uppercase tracking-widest text-slate-400">Potential missed gain:</span>
            <span className="animate-pulse text-sm font-black text-emerald-400 drop-shadow-[0_0_8px_rgba(52,211,153,0.5)]">
              {active.impact}
            </span>
          </div>
        </div>

        <div className="rounded-2xl bg-white/5 p-4 border border-white/5">
          <p className="flex items-center gap-2 text-xs font-medium text-slate-300">
            <Lightbulb size={14} className="text-yellow-400 shrink-0" />
            {active.suggestion}
          </p>
        </div>

        <div className="flex flex-wrap gap-2 pt-2">
          <button className="flex flex-1 items-center justify-center gap-2 whitespace-nowrap rounded-xl bg-blue-600 px-4 py-3 text-[10px] font-black uppercase tracking-widest text-white transition-all hover:bg-blue-500 hover:shadow-[0_0_15px_rgba(59,130,246,0.4)] active:scale-95">
            Retry Setup
          </button>
          <button className="flex items-center justify-center gap-2 rounded-xl bg-white/5 border border-white/10 px-4 py-3 text-[10px] font-black uppercase tracking-widest text-white transition-all hover:bg-white/10 active:scale-95">
            <Info size={14} /> Learn Why
          </button>
          <button className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/5 border border-white/10 transition-all hover:bg-white/10 active:scale-95">
            <ChevronRight size={18} className="text-white" />
          </button>
        </div>
      </div>

      {/* Behavior Tag */}
      <div className="absolute bottom-4 right-6">
        <span className="text-[8px] font-black uppercase tracking-[0.2em] text-white/20 group-hover:text-white/40 transition-colors">
          {active.tag}
        </span>
      </div>
    </motion.div>
  );
};

export default NextEdgeCard;
