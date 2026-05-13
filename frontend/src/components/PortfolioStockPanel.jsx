import React, { useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, TrendingUp, TrendingDown, Target, Shield, Zap, Info, Briefcase, Trash2, Plus } from 'lucide-react';
import { formatPrice } from '../utils/formatters';
import { useTrading } from '../context/TradingContext';
import { getLearningInsight } from '../utils/learningEngine';
import MicroLearningCard from './MicroLearningCard';
import DataBadge from './DataBadge';

const PortfolioStockPanel = ({ isOpen, onClose, stock, marketCode, onAction }) => {
  const { decisions, missedOpportunities, portfolio } = useTrading();

  const adaptiveInsight = useMemo(() => {
    return getLearningInsight({
      decisions,
      missedOpportunities,
      holdings: portfolio,
      totalCurrentValue: portfolio.reduce((sum, h) => sum + (h.currentValue || h.invested), 0)
    });
  }, [decisions, missedOpportunities, portfolio]);

  useEffect(() => {
    document.body.style.overflow = isOpen ? 'hidden' : 'auto';
    return () => { document.body.style.overflow = 'auto'; };
  }, [isOpen]);

  if (!stock) return null;

  const isProfit = stock.gainVal >= 0;
  const accentColor = isProfit ? 'text-emerald-400' : 'text-rose-400';
  const bgColor = isProfit ? 'bg-emerald-500/10' : 'bg-rose-500/10';
  const borderColor = isProfit ? 'border-emerald-500/20' : 'border-rose-500/20';

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} className="fixed inset-0 bg-slate-950/80 backdrop-blur-md z-[100]" />
          <motion.div initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }} transition={{ type: 'spring', damping: 25, stiffness: 200 }} className="fixed top-0 right-0 w-full md:w-[450px] h-screen bg-slate-900 border-l border-white/10 z-[101] shadow-2xl flex flex-col overflow-hidden">
            <div className="p-6 border-b border-white/5 flex items-center justify-between sticky top-0 bg-slate-900/90 backdrop-blur-md z-10">
              <div className="space-y-1">
                <h3 className="text-2xl font-black text-white tracking-tight flex items-center gap-2">
                  {stock.symbol}
                  <span className={`text-[10px] px-2 py-0.5 rounded-md ${bgColor} ${accentColor} border ${borderColor}`}>HOLDING</span>
                  <DataBadge meta={stock.meta} />
                </h3>
                <div className="flex items-center gap-3">
                  <span className="text-xl font-bold text-slate-200">{formatPrice(stock.currentValue / stock.quantity, stock.currency)}</span>
                  <span className={`text-sm font-black ${accentColor}`}>{isProfit ? '+' : ''}{stock.gainPct.toFixed(2)}%</span>
                </div>
              </div>
              <button onClick={onClose} className="p-3 text-slate-400 hover:text-white bg-white/5 rounded-2xl transition-all"><X size={24} /></button>
            </div>

            <div className="flex-1 overflow-y-auto p-6 space-y-8 no-scrollbar pb-32">
              <section className="grid grid-cols-2 gap-3">
                <div className="p-4 rounded-2xl bg-white/5 border border-white/5">
                   <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Avg Buy Price</p>
                   <p className="text-lg font-bold text-white">{formatPrice(stock.buyPrice, stock.currency)}</p>
                </div>
                <div className="p-4 rounded-2xl bg-white/5 border border-white/5">
                   <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Returns</p>
                   <p className={`text-lg font-bold ${accentColor}`}>{isProfit ? '+' : ''}{formatPrice(stock.gainVal, stock.currency)}</p>
                </div>
              </section>
              <section className="space-y-3">
                <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2"><Zap size={12} className="text-yellow-500" /> Insight</h4>
                <div className="p-5 rounded-3xl bg-slate-800/40 border border-white/5"><p className="text-sm text-slate-300 font-medium leading-relaxed italic">"{stock.insight || "Market dynamics are influencing this position."}"</p></div>
              </section>
              <MicroLearningCard insight={adaptiveInsight} />
            </div>

            <div className="p-6 bg-[#020617]/90 backdrop-blur-xl border-t border-white/5 flex gap-3 z-20 sticky bottom-0">
               <button onClick={() => onAction('buy', stock)} className="flex-1 py-4 bg-blue-600 text-white rounded-2xl text-[10px] font-black uppercase tracking-widest transition-all shadow-lg flex items-center justify-center gap-2"><Plus size={16} /> Add More</button>
               <button onClick={() => onAction('sell', stock)} className="flex-1 py-4 bg-white/5 hover:bg-rose-600 text-white border border-white/10 rounded-2xl text-[10px] font-black uppercase tracking-widest transition-all flex items-center justify-center gap-2"><Trash2 size={16} /> Exit</button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default PortfolioStockPanel;
