import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Sparkles, ArrowRight, X, BrainCircuit } from 'lucide-react';

export default function GuideOverlay({ step, totalSteps, title, content, active, onDismiss, onNext }) {
  if (!active) return null;

  return (
    <div className="fixed top-24 left-8 z-[100] pointer-events-none w-full max-w-sm">
      {/* Interactive Guide Card - Floating Version */}
      <motion.div 
        initial={{ opacity: 0, x: -30, scale: 0.95 }}
        animate={{ opacity: 1, x: 0, scale: 1 }}
        exit={{ opacity: 0, scale: 0.9 }}
        onClick={onNext}
        className="relative z-[201] w-full bg-[#0f172a]/95 backdrop-blur-xl border border-blue-500/30 p-6 rounded-3xl shadow-[0_20px_50px_rgba(0,0,0,0.5)] pointer-events-auto overflow-hidden group ring-1 ring-blue-400/20 cursor-pointer"
      >
        {/* Animated Background Orbs */}
        <div className="absolute top-0 right-0 w-24 h-24 bg-blue-600/10 blur-3xl rounded-full -translate-y-1/2 translate-x-1/2 animate-pulse" />
        
        <div className="relative z-10 space-y-4">
          <div className="flex justify-between items-center">
            <div className="flex items-center gap-2">
               <div className="w-8 h-8 rounded-lg bg-blue-600/20 border border-blue-500/30 flex items-center justify-center">
                 <BrainCircuit size={16} className="text-blue-400" />
               </div>
               <span className="text-[9px] font-black text-blue-400 uppercase tracking-[0.4em]">Guide • {step}/{totalSteps}</span>
            </div>
            {onDismiss && (
              <button 
                onClick={(e) => { e.stopPropagation(); onDismiss(); }} 
                className="p-1.5 text-slate-500 hover:text-white hover:bg-white/10 rounded-full transition-all"
              >
                <X size={14} />
              </button>
            )}
          </div>
          
          <div className="space-y-2">
            <h3 className="text-lg font-black text-white tracking-tight uppercase leading-none">{title}</h3>
            <p className="text-blue-100/70 text-xs font-medium leading-relaxed italic border-l-2 border-blue-500/30 pl-4 py-0.5 min-h-[40px]">
              {content}
            </p>
          </div>

          <div className="pt-2 flex items-center justify-between">
            <div className="flex items-center gap-2 text-blue-400 animate-pulse group-hover:scale-105 transition-transform">
              <span className="text-[8px] font-black uppercase tracking-[0.2em]">{step === totalSteps ? 'Finish Tutorial' : 'Tap to continue'}</span>
              <ArrowRight size={12} />
            </div>
            <div className="flex gap-1">
               {Array.from({ length: totalSteps }).map((_, i) => (
                 <div key={i} className={`h-1 rounded-full transition-all duration-500 ${i + 1 === step ? 'w-4 bg-blue-500' : 'w-1 bg-slate-800'}`} />
               ))}
            </div>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
