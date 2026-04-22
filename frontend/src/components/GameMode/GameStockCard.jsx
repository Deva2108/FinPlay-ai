import React, { useMemo } from 'react';
import { motion } from 'framer-motion';
import { formatPrice } from '../../utils/formatters';
import { getAnalyticalDetails } from '../../utils/gameLogic';
import { Cpu, ChevronRight, Activity } from 'lucide-react';

const containerVariants = {
  hidden: { opacity: 0, scale: 0.98 },
  visible: { 
    opacity: 1, 
    scale: 1,
    transition: { 
      staggerChildren: 0.12,
      delayChildren: 0.1,
      type: "spring",
      stiffness: 260,
      damping: 20
    }
  }
};

const itemVariants = {
  hidden: { opacity: 0, x: -10 },
  visible: { 
    opacity: 1, 
    x: 0,
    transition: { type: "spring", stiffness: 300, damping: 25 }
  }
};

const pointVariants = {
  hidden: { opacity: 0, y: 5 },
  visible: (i) => ({
    opacity: 1,
    y: 0,
    transition: { delay: 0.5 + (i * 0.1), duration: 0.4 }
  })
};

export default function GameStockCard({ stock, onClick }) {
  const analytics = useMemo(() => getAnalyticalDetails(stock), [stock?.symbol]);

  return (
    <motion.div 
      variants={containerVariants}
      initial="hidden"
      animate="visible"
      whileHover={{ scale: 1.005, borderColor: "rgba(59, 130, 246, 0.4)" }}
      onClick={onClick}
      className="bg-slate-900/40 border border-slate-800 p-6 rounded-[2.5rem] cursor-pointer transition-all group relative overflow-hidden shadow-2xl z-10"
    >
      {/* Subtle Background Animation */}
      <div className="absolute inset-0 pointer-events-none">
        <motion.div 
          animate={{ 
            background: [
              "radial-gradient(circle at 0% 0%, rgba(59, 130, 246, 0.03) 0%, transparent 50%)",
              "radial-gradient(circle at 100% 100%, rgba(59, 130, 246, 0.03) 0%, transparent 50%)",
              "radial-gradient(circle at 0% 100%, rgba(59, 130, 246, 0.03) 0%, transparent 50%)",
              "radial-gradient(circle at 100% 0%, rgba(59, 130, 246, 0.03) 0%, transparent 50%)",
            ]
          }}
          transition={{ duration: 10, repeat: Infinity, ease: "linear" }}
          className="absolute inset-0"
        />
        <div className="absolute inset-0 bg-[url('https://www.transparenttextures.com/patterns/carbon-fibre.png')] opacity-[0.02]" />
      </div>

      <div className="space-y-6 relative z-10">
        <motion.div variants={itemVariants} className="flex justify-between items-center">
          <div className="flex items-center gap-2">
            <div className="p-1.5 bg-blue-500/10 rounded-lg">
              <Cpu size={14} className="text-blue-500" />
            </div>
            <p className="text-[10px] font-black text-slate-500 uppercase tracking-[0.2em]">Neural Analysis Engine</p>
          </div>
          <div className="flex items-center gap-1.5 px-3 py-1 bg-white/5 rounded-full border border-white/5">
             <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
             <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Active</span>
          </div>
        </motion.div>

        <motion.div variants={itemVariants}>
          <p className="text-sm sm:text-base font-bold text-slate-200 leading-relaxed mb-1">
            {analytics?.expandedSituation}
          </p>
          <div className="h-px w-12 bg-blue-500/50 rounded-full" />
        </motion.div>

        <motion.div variants={itemVariants} className="flex justify-between items-center py-5 border-y border-white/[0.05]">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-2xl bg-slate-800 flex items-center justify-center font-black text-white text-xl border border-white/5 shadow-inner">
              {(stock?.symbol || "")[0]}
            </div>
            <div>
              <h3 className="text-3xl font-black text-white leading-none tracking-tight">{stock?.symbol}</h3>
              <p className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mt-1.5">{stock?.name}</p>
            </div>
          </div>
          <div className="text-right">
            <p className="text-2xl font-black text-white tabular-nums tracking-tighter">{formatPrice(stock?.price, stock?.market)}</p>
            <p className={`text-[10px] font-black px-2.5 py-1 rounded-lg mt-2 inline-block ${(stock?.change || "").startsWith('+') ? 'bg-emerald-500/10 text-emerald-500' : 'bg-rose-500/10 text-rose-500'}`}>
              {stock?.change} Today
            </p>

          </div>
        </motion.div>

        {/* System Thinking Section */}
        <motion.div variants={itemVariants} className="bg-slate-900/50 border border-white/5 rounded-3xl p-5 space-y-3">
          <div className="flex items-center gap-2 mb-1">
            <Activity size={14} className="text-blue-500" />
            <span className="text-[10px] font-black text-blue-500 uppercase tracking-widest">System Thinking</span>
          </div>
          <div className="space-y-2.5">
            {(analytics?.systemThinking || []).map((point, i) => (
              <motion.div 
                key={i} 
                custom={i}
                variants={pointVariants}
                className="flex items-start gap-3"
              >
                <ChevronRight size={14} className="text-slate-700 shrink-0 mt-0.5" />
                <p className="text-xs text-slate-400 font-medium leading-snug">{point}</p>
              </motion.div>
            ))}
          </div>
        </motion.div>

        <motion.div variants={itemVariants} className="flex items-center justify-between pt-1">
           <div className="flex flex-col">
              <span className="text-[9px] font-black text-slate-600 uppercase tracking-widest mb-1">Impact Weight</span>
              <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Learning Score x1.5</span>
           </div>
           <div className="text-right">
              <span className="text-[9px] font-black text-slate-600 uppercase tracking-widest mb-1 block">Decision Impact</span>
              <span className="text-[10px] font-black text-blue-500 uppercase tracking-widest">Portfolio Beta Adjust</span>
           </div>
        </motion.div>
      </div>
    </motion.div>
  );
}

