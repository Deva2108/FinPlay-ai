import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Sparkles, ArrowRight } from 'lucide-react';

export default function GuideOverlay({ step, totalSteps, title, content, active, onDismiss }) {
  if (!active) return null;

  return (
    <div className="fixed inset-0 z-[200] pointer-events-none flex items-center justify-center p-6">
      {/* 1. Backdrop (UI Lock) */}
      <motion.div 
        initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
        className="absolute inset-0 bg-slate-950/60 backdrop-blur-[2px] pointer-events-auto"
      />

      {/* 2. Tooltip Card */}
      <motion.div 
        initial={{ opacity: 0, y: 20, scale: 0.9 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        className="relative z-[201] w-full max-w-sm bg-slate-900 border border-blue-500/30 p-8 rounded-[2.5rem] shadow-2xl pointer-events-auto"
      >
        <div className="absolute top-0 right-0 p-6 opacity-10"><Sparkles size={80} className="text-blue-400" /></div>
        
        <div className="space-y-6 relative z-10">
          <div className="flex justify-between items-center">
            <span className="text-[10px] font-black text-blue-400 uppercase tracking-[0.3em]">Module 01 • Step {step}/{totalSteps}</span>
            {onDismiss && <button onClick={onDismiss} className="text-slate-500 hover:text-white text-[10px] font-bold uppercase">Skip</button>}
          </div>
          
          <div className="space-y-2">
            <h3 className="text-xl font-black text-white uppercase tracking-tight">{title}</h3>
            <p className="text-slate-400 text-sm leading-relaxed">{content}</p>
          </div>

          <div className="pt-2 flex items-center gap-2 text-blue-400 animate-pulse">
            <span className="text-[10px] font-black uppercase tracking-widest">Waiting for action</span>
            <ArrowRight size={12} />
          </div>
        </div>
      </motion.div>
    </div>
  );
}
