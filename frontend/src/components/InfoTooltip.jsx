import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { HelpCircle, X, Lightbulb, Zap, BookOpen } from 'lucide-react';

const conceptLibrary = {
  yield: {
    title: "Returns (Yield)",
    explanation: "This shows how much your money has grown in percentage.",
    example: "If you invested ₹100 and it is now ₹110, you have a 10% return.",
    why: "A positive return means your investment is working. A higher percentage means faster growth."
  },
  diversification: {
    title: "Spreading Risk",
    explanation: "This means putting your money into different companies instead of just one.",
    example: "If you buy stocks in both Tech and Food, a crash in Tech won't ruin your whole portfolio.",
    why: "It protects you. If one company fails, your others can still keep your savings safe."
  },
  stoploss: {
    title: "Safety Net (Stop Loss)",
    explanation: "This is a rule to automatically sell a stock if its price falls below a certain point.",
    example: "You decide to sell automatically if your ₹100 stock drops to ₹90 to avoid losing more.",
    why: "It prevents a small mistake from becoming a big disaster. Use it to protect your hard-earned money."
  },
  index: {
    title: "Market Score (Index)",
    explanation: "This is a single number that shows if the whole market is going up or down.",
    example: "The NIFTY 50 tracks the 50 biggest companies in India to show the country's economic health.",
    why: "It helps you see the 'big picture' before you decide to buy or sell individual stocks."
  },
  volatility: {
    title: "Price Swings (Volatility)",
    explanation: "This describes how fast and how much a stock price jumps up and down.",
    example: "Some stocks are like a calm walk, while others are like a wild roller coaster.",
    why: "High swings mean higher risk. For beginners, steadier stocks are often easier to manage."
  },
  pnl: {
    title: "Profit and Loss (P&L)",
    explanation: "This is the final calculation of whether you are making money or losing it.",
    example: "Current Value minus Invested Amount = Your profit or loss.",
    why: "It is the most important number to track. It tells you exactly how your decisions are performing."
  }
};

const InfoTooltip = ({ concept, children }) => {
  const [isOpen, setIsOpen] = useState(false);
  const data = conceptLibrary[concept?.toLowerCase()];

  if (!data) return children;

  return (
    <div className="relative inline-flex items-center gap-1 group">
      {children}
      <button 
        onClick={(e) => {
          e.stopPropagation();
          setIsOpen(true);
        }}
        className="p-1 rounded-full text-blue-400/50 hover:text-blue-400 hover:bg-blue-400/10 transition-all active:scale-90"
      >
        <HelpCircle size={14} />
      </button>

      <AnimatePresence>
        {isOpen && (
          <>
            {/* Backdrop */}
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setIsOpen(false)}
              className="fixed inset-0 bg-slate-950/40 backdrop-blur-[2px] z-[200]"
            />

            {/* Tooltip Content */}
            <motion.div
              initial={{ opacity: 0, scale: 0.9, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.9, y: 10 }}
              className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[90%] max-w-sm bg-slate-900 border border-blue-500/30 rounded-[2rem] shadow-2xl p-6 z-[201] overflow-hidden"
            >
              <div className="absolute top-0 right-0 p-4">
                <button onClick={() => setIsOpen(false)} className="text-slate-500 hover:text-white transition-colors">
                  <X size={20} />
                </button>
              </div>

              <div className="space-y-6">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 bg-blue-600/20 rounded-2xl text-blue-400">
                    <BookOpen size={20} />
                  </div>
                  <h3 className="text-xl font-black text-white uppercase tracking-tight">{data.title}</h3>
                </div>

                <div className="space-y-4">
                  <div className="space-y-1">
                    <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">What it is</p>
                    <p className="text-sm font-medium text-slate-200 leading-relaxed">{data.explanation}</p>
                  </div>

                  <div className="p-4 rounded-2xl bg-white/5 border border-white/5 space-y-1">
                    <p className="text-[10px] font-black text-yellow-500 uppercase tracking-widest flex items-center gap-2">
                       <Zap size={10} /> Real Life Example
                    </p>
                    <p className="text-xs font-medium text-slate-400 italic">"{data.example}"</p>
                  </div>

                  <div className="space-y-1">
                    <p className="text-[10px] font-black text-blue-400 uppercase tracking-widest flex items-center gap-2">
                       <Lightbulb size={10} /> Why it matters to you
                    </p>
                    <p className="text-xs font-bold text-blue-100/80 leading-relaxed">{data.why}</p>
                  </div>
                </div>

                <button 
                  onClick={() => setIsOpen(false)}
                  className="w-full py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-[10px] font-black uppercase tracking-widest transition-all shadow-lg shadow-blue-600/20 active:scale-95"
                >
                  Got it!
                </button>
              </div>

              {/* Decorative Glow */}
              <div className="absolute -bottom-10 -right-10 w-32 h-32 bg-blue-600/10 blur-3xl rounded-full" />
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
};

export default InfoTooltip;
