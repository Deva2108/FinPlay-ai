import React, { useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, TrendingUp, TrendingDown, Info, Activity, PieChart, Shield, Zap } from 'lucide-react';
import { formatPrice } from '../utils/formatters';

export default function InsightPanel({ isOpen, onClose, content }) {
  // Prevent body scroll when open on mobile
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

  if (!content) return null;

  const { title, explanation, data, insight, actions, type } = content;

  const getIcon = () => {
    switch (type) {
      case 'return': return <TrendingUp className="text-emerald-400" />;
      case 'risk': return <Shield className="text-rose-400" />;
      case 'allocation': return <PieChart className="text-blue-400" />;
      case 'stock': return <Activity className="text-purple-400" />;
      case 'game': return <Zap className="text-yellow-400" />;
      default: return <Info className="text-blue-400" />;
    }
  };

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
            className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-[100]"
          />

          {/* Panel - Bottom Sheet (Mobile) / Side Drawer (Desktop) */}
          <motion.div
            initial={{ y: '100%', x: 0 }}
            animate={{ y: 0, x: 0 }}
            exit={{ y: '100%', x: 0 }}
            transition={{ type: 'spring', damping: 25, stiffness: 200 }}
            className="fixed bottom-0 left-0 right-0 md:bottom-auto md:top-0 md:right-0 md:left-auto md:w-[400px] md:h-screen max-h-[90vh] md:max-h-screen bg-slate-900 border-t md:border-t-0 md:border-l border-white/10 z-[101] rounded-t-3xl md:rounded-none overflow-hidden flex flex-col shadow-2xl"
          >
            {/* Header */}
            <div className="p-6 border-b border-white/5 flex items-center justify-between sticky top-0 bg-slate-900/90 backdrop-blur-md z-10">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-white/5 rounded-xl border border-white/5">
                  {getIcon()}
                </div>
                <h3 className="text-xl font-black text-white tracking-tight">{title}</h3>
              </div>
              <button 
                onClick={onClose}
                className="p-2 text-slate-400 hover:text-white bg-white/5 hover:bg-white/10 rounded-full transition-colors"
              >
                <X size={20} />
              </button>
            </div>

            {/* Scrollable Content */}
            <div className="p-6 overflow-y-auto flex-1 space-y-8 no-scrollbar pb-32 md:pb-6">
              
              {/* Explanation Section */}
              {explanation && (
                <section className="space-y-3">
                  <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
                    <Info size={12} /> What this means
                  </h4>
                  <p className="text-sm text-slate-300 font-medium leading-relaxed bg-white/5 p-4 rounded-2xl border border-white/5">
                    {explanation}
                  </p>
                </section>
              )}

              {/* Data Breakdown Section */}
              {data && data.length > 0 && (
                <section className="space-y-3">
                  <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest">
                    Data Breakdown
                  </h4>
                  <div className="space-y-2">
                    {(data || []).map((item, idx) => (
                      <div key={idx} className="flex items-center justify-between p-3 bg-slate-800/50 rounded-xl border border-white/5">
                        <span className="text-xs font-bold text-slate-300">{item.label}</span>
                        <span className={`text-sm font-black ${item.color || 'text-white'}`}>
                          {item.value}
                        </span>
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {/* Visual Bar Split (if requested in data) */}
              {data && data.some(d => d.progress) && (
                <section className="space-y-2">
                   <div className="h-2 w-full flex rounded-full overflow-hidden gap-0.5 bg-slate-800">
                      {(data || []).filter(d => d.progress).map((item, idx) => (
                         <div 
                           key={idx} 
                           style={{ width: `${item.progress}%`, backgroundColor: item.barColor || '#3b82f6' }}
                           className="h-full"
                         />
                      ))}
                   </div>
                </section>
              )}

              {/* Insight / AI Reasoning */}
              {insight && (
                <section className="space-y-3">
                  <h4 className="text-[10px] font-black text-blue-400 uppercase tracking-widest flex items-center gap-2">
                    <Zap size={12} /> System Intelligence
                  </h4>
                  <div className="p-4 rounded-2xl bg-gradient-to-br from-blue-600/10 to-purple-600/10 border border-blue-500/20 relative overflow-hidden">
                    <div className="absolute top-0 right-0 p-2 opacity-10"><Zap size={40} /></div>
                    <p className="text-sm font-bold text-blue-100 leading-relaxed relative z-10">
                      {insight}
                    </p>
                  </div>
                </section>
              )}

              {/* Action Buttons */}
              {actions && actions.length > 0 && (
                <section className="pt-4 border-t border-white/5 space-y-3">
                  {(actions || []).map((action, idx) => (
                    <button
                      key={idx}
                      onClick={() => {
                        if(action.onClick) action.onClick();
                        if(!action.keepOpen) onClose();
                      }}
                      className={`w-full py-4 rounded-2xl text-xs font-black uppercase tracking-widest transition-all active:scale-95 flex items-center justify-center gap-2 ${
                        action.primary 
                          ? 'bg-blue-600 hover:bg-blue-500 text-white shadow-lg shadow-blue-600/20' 
                          : 'bg-white/5 hover:bg-white/10 text-white border border-white/10'
                      }`}
                    >
                      {action.label}
                    </button>
                  ))}
                </section>
              )}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
