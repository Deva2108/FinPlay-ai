import React from 'react';
import { motion } from 'framer-motion';
import { BookOpen, Zap, Info, Lightbulb } from 'lucide-react';

const MicroLearningCard = ({ insight, variant = "default" }) => {
  if (!insight) return null;

  return (
    <motion.div 
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      className="bg-blue-600/10 border border-blue-500/20 rounded-2xl p-4 space-y-3 relative overflow-hidden"
    >
      <div className="absolute top-0 right-0 p-3 opacity-10">
        <BookOpen size={40} className="text-blue-400" />
      </div>

      <div className="flex items-center gap-2">
        <span className="px-2 py-0.5 rounded-lg bg-blue-500 text-white text-[8px] font-black uppercase tracking-widest">
          Micro Learning
        </span>
        <h4 className="text-xs font-black text-blue-400 uppercase tracking-widest">
          {typeof insight.topic === "string" ? insight.topic : insight.topic?.text || "Wealth Intelligence"}
        </h4>
      </div>

      <div className="space-y-1 relative z-10">
        <p className="text-sm font-bold text-white leading-tight">
          {typeof insight.message === "string" ? insight.message : insight.message?.text || insight.message?.message || "Preparing your personalized insight..."}
        </p>
        <p className="text-xs text-slate-400 font-medium leading-relaxed italic opacity-80">
          "{typeof insight.explanation === "string" ? insight.explanation : insight.explanation?.text || insight.explanation?.message || "Our AI is analyzing your latest moves to find the deeper pattern."}"
        </p>
      </div>

      <div className="pt-2 flex items-center gap-2 border-t border-white/5">
        <Lightbulb size={12} className="text-yellow-500" />
        <p className="text-[10px] font-bold text-blue-100/60 uppercase tracking-tighter">
          Why it matters: {typeof insight.why === "string" ? insight.why : insight.why?.text || "Understanding this builds long-term intuition."}
        </p>
      </div>
    </motion.div>
  );
};

export default MicroLearningCard;
