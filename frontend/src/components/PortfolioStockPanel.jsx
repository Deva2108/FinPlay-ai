import React, { useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, TrendingUp, TrendingDown, Target, Shield, Zap, Info, Briefcase, Trash2, Plus } from 'lucide-react';
import { formatPrice } from '../utils/formatters';
import { useBehavior } from '../context/BehaviorContext';
import { useTrading } from '../context/TradingContext';
import { getLearningInsight } from '../utils/learningEngine';
import MicroLearningCard from './MicroLearningCard';

const PortfolioStockPanel = ({ isOpen, onClose, stock, marketCode, onAction }) => {
  const { decisions, missedOpportunities } = useBehavior();
  const { portfolio } = useTrading();

  const adaptiveInsight = useMemo(() => {
    return getLearningInsight({
      decisions,
      missedOpportunities,
      holdings: portfolio,
      totalCurrentValue: portfolio.reduce((sum, h) => sum + (h.currentValue || h.invested), 0)
    });
  }, [decisions, missedOpportunities, portfolio]);

  // Prevent body scroll when open
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'auto';
    }
    return () => {
      document.body.style.overflow = 'auto';
    };
  }, [isOpen]);

  if (!stock) return null;

  const isProfit = stock.gainVal >= 0;
  const accentColor = isProfit ? 'text-emerald-400' : 'text-rose-400';
  const bgColor = isProfit ? 'bg-emerald-500/10' : 'bg-rose-500/10';
  const borderColor = isProfit ? 'border-emerald-500/20' : 'border-rose-500/20';

  // Determine Risk Level based on gain/loss volatility (mock logic)
  const riskLevel = Math.abs(stock.gainPct) > 5 ? 'High' : Math.abs(stock.gainPct) > 2 ? 'Medium' : 'Low';
  const riskColor = riskLevel === 'High' ? 'text-rose-400' : riskLevel === 'Medium' ? 'text-yellow-400' : 'text-emerald-400';

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
                  {stock.symbol}
                  <span className={`text-[10px] px-2 py-0.5 rounded-md ${bgColor} ${accentColor} border ${borderColor}`}>
                    HOLDING
                  </span>
                </h3>
                <div className="flex items-center gap-3">
                  <span className="text-xl font-bold text-slate-200">{formatPrice(stock.currentValue / stock.quantity, stock.market)}</span>
                  <span className={`text-sm font-black ${accentColor}`}>
                    {isProfit ? '+' : ''}{stock.gainPct.toFixed(2)}%
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

            {/* Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-8 no-scrollbar pb-32">
              
              {/* Position Summary */}
              <section className="grid grid-cols-2 gap-3">
                <div className="p-4 rounded-2xl bg-white/5 border border-white/5">
                   <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Avg Buy Price</p>
                   <p className="text-lg font-bold text-white">{formatPrice(stock.buyPrice, stock.market)}</p>
                </div>
                <div className="p-4 rounded-2xl bg-white/5 border border-white/5">
                   <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Total Returns</p>
                   <p className={`text-lg font-bold ${accentColor}`}>{isProfit ? '+' : ''}{formatPrice(stock.gainVal, stock.market)}</p>
                </div>
              </section>

              {/* Why it moved */}
              <section className="space-y-3">
                <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
                  <Zap size={12} className="text-yellow-500" /> Why it moved
                </h4>
                <div className="p-5 rounded-3xl bg-slate-800/40 border border-white/5">
                  <p className="text-sm text-slate-300 font-medium leading-relaxed italic">
                    "{stock.insight || "Market dynamics and sector-specific volume buying have influenced this stock's current trajectory."}"
                  </p>
                </div>
              </section>

              {/* What this means */}
              <section className="space-y-3">
                <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
                  <Info size={12} className="text-blue-400" /> What this means
                </h4>
                <div className="p-5 rounded-3xl bg-white/5 border border-white/5 space-y-3">
                  <p className="text-sm text-slate-300 font-medium leading-relaxed">
                    {isProfit 
                      ? "Your investment is outperforming the entry price. This indicates that your initial thesis is being validated by the current market sentiment."
                      : "The stock is currently trading below your average buy price. This is common in volatile markets and requires a review of the long-term outlook."}
                  </p>
                </div>
              </section>

              {/* Risk Level & Smart Action */}
              <section className="space-y-4">
                <div className="flex items-center justify-between px-1">
                   <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest">System Analysis</h4>
                   <span className={`text-[10px] font-black uppercase ${riskColor}`}>Risk: {riskLevel}</span>
                </div>
                
                <MicroLearningCard insight={adaptiveInsight} />

                <div className="p-6 rounded-3xl bg-gradient-to-br from-blue-600/10 to-purple-600/10 border border-blue-500/20 relative overflow-hidden">
                  <div className="absolute top-0 right-0 p-4 opacity-5"><Target size={80} /></div>
                  <div className="relative z-10 space-y-4">
                    <h5 className="text-sm font-black text-white flex items-center gap-2">
                      <Shield size={14} className="text-blue-400" /> Smart Suggestion:
                    </h5>
                    <p className="text-sm text-blue-100/80 font-medium leading-relaxed">
                      {isProfit 
                        ? "Consider trailing your stop-loss to protect these gains while allowing for more upside if the momentum continues."
                        : "Analyze the sector health. If the fundamentals remain strong, this could be an opportunity to average down your cost."}
                    </p>
                  </div>
                </div>
              </section>
            </div>

            {/* Sticky Action Footer */}
            <div className="p-6 bg-[#020617]/90 backdrop-blur-xl border-t border-white/5 flex flex-col md:flex-row gap-3 z-20 sticky bottom-0">
               <button 
                 onClick={() => onAction('buy', stock)}
                 className="flex-1 py-4 bg-blue-600 hover:bg-blue-500 text-white rounded-2xl text-[10px] font-black uppercase tracking-widest transition-all shadow-lg shadow-blue-600/20 active:scale-95 flex items-center justify-center gap-2"
               >
                 <Plus size={16} /> Add More
               </button>
               <button 
                 onClick={() => onAction('sell', stock)}
                 className="flex-1 py-4 bg-white/5 hover:bg-rose-600 text-white border border-white/10 rounded-2xl text-[10px] font-black uppercase tracking-widest transition-all active:scale-95 flex items-center justify-center gap-2 group"
               >
                 <Trash2 size={16} className="group-hover:text-white" /> Exit Position
               </button>
               <button className="px-6 py-4 bg-white/5 hover:bg-white/10 text-white border border-white/10 rounded-2xl text-[10px] font-black uppercase tracking-widest transition-all active:scale-95">
                 <Briefcase size={16} />
               </button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default PortfolioStockPanel;
