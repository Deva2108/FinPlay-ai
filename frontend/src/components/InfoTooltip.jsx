import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { HelpCircle, X, Lightbulb, Zap, BookOpen } from 'lucide-react';

import { useMarket } from '../context/MarketContext';
import { formatPrice } from '../utils/formatters';

const getConceptLibrary = (currency) => ({
  yield: {
    title: "Returns (Yield)",
    explanation: "This shows how much your money has grown in percentage.",
    example: `If you invested ${formatPrice(100, currency)} and it is now ${formatPrice(110, currency)}, you have a 10% return.`,
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
    example: `You decide to sell automatically if your ${formatPrice(100, currency)} stock drops to ${formatPrice(90, currency)} to avoid losing more.`,
    why: "It prevents a small mistake from becoming a big disaster. Use it to protect your hard-earned money."
  },
  index: {
    title: "Market Score (Index)",
    explanation: "This is a single number that shows if the whole market is going up or down.",
    example: currency === 'INR' ? "The NIFTY 50 tracks the 50 biggest companies in India to show the country's economic health." : "The S&P 500 tracks the 500 biggest companies in the US to show the country's economic health.",
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
  },
  moat: {
    title: "Competitive Edge (Moat)",
    explanation: "A unique advantage that protects a company from its competitors, like a castle's moat.",
    example: "Apple's brand and ecosystem or Coca-Cola's secret formula are powerful moats.",
    why: "Companies with 'Wide Moats' are usually safer long-term investments because they are harder to beat."
  },
  peratio: {
    title: "Value Score (P/E Ratio)",
    explanation: "Price-to-Earnings ratio. It shows how much investors are willing to pay for every ₹1 of profit.",
    example: `If a company earns ${formatPrice(10, currency)} and its stock price is ${formatPrice(200, currency)}, its P/E ratio is 20.`,
    why: "A high P/E might mean a stock is expensive, while a low P/E might suggest it's a bargain (or in trouble)."
  },
  marketcap: {
    title: "Price Tag (Market Cap)",
    explanation: "The total value of a company if you bought every single share.",
    example: `A company with 1 million shares at ${formatPrice(100, currency)} each has a Market Cap of ${formatPrice(100000000, currency)}.`,
    why: "It helps you categorize companies into Large, Mid, or Small-cap, which determines their stability and growth potential."
  },
  compounding: {
    title: "Money Multiplier (Compounding)",
    explanation: "Earning interest on your interest. It makes your money grow faster over time.",
    example: `If you earn 10% on ${formatPrice(100, currency)}, you have ${formatPrice(110, currency)}. Next year, you earn 10% on ${formatPrice(110, currency)}, not just the original ${formatPrice(100, currency)}.`,
    why: "It is the most powerful tool for building wealth. The earlier you start, the more 'magic' happens."
  },
  liquidity: {
    title: "Cash-Out Speed (Liquidity)",
    explanation: "How easily you can turn an investment back into cash without losing value.",
    example: "A popular stock like Apple is very liquid; a house is not because it takes months to sell.",
    why: "You need liquidity for emergencies. If an investment is 'illiquid', your money is locked away."
  },
  inflation: {
    title: "Hidden Cost (Inflation)",
    explanation: "The rate at which the prices of things increase, reducing what your money can buy.",
    example: `If a loaf of bread costs ${formatPrice(50, currency)} today but ${formatPrice(55, currency)} next year, inflation is 10%.`,
    why: "If your investments don't grow faster than inflation, you are actually losing wealth."
  }
});

const InfoTooltip = ({ concept, children }) => {
  const [isOpen, setIsOpen] = useState(false);
  const { marketCode } = useMarket();
  const currency = marketCode === 'IN' ? 'INR' : 'INR';
  const conceptLibrary = getConceptLibrary(currency);
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
        className="relative p-1 rounded-full text-blue-400 hover:text-white transition-all active:scale-90"
      >
        <HelpCircle size={16} className="relative z-10" />
        <span className="absolute inset-0 bg-blue-500/20 rounded-full animate-ping group-hover:bg-blue-500/40" />
      </button>

      <AnimatePresence>
        {isOpen && (
          <>
            {/* Backdrop - Transparent to allow clicking out */}
            <div 
              className="fixed inset-0 z-[190]" 
              onClick={(e) => {
                e.stopPropagation();
                setIsOpen(false);
              }}
            />

            {/* Tooltip Content - Small Cloud Style Popping UP */}
            <motion.div
              initial={{ opacity: 0, scale: 0.5, y: 10, x: '-50%' }}
              animate={{ opacity: 1, scale: 1, y: 0, x: '-50%' }}
              exit={{ opacity: 0, scale: 0.5, y: 10, x: '-50%' }}
              className="absolute bottom-full left-1/2 mb-4 w-72 bg-slate-900 border border-blue-500/40 rounded-[2rem] shadow-[0_10px_40px_rgba(0,0,0,0.6)] p-6 z-[200] overflow-hidden"
            >
              {/* Triangle Pointer */}
              <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 w-4 h-4 bg-slate-900 border-r border-b border-blue-500/40 rotate-45" />

              <div className="absolute top-0 right-0 p-3">
                <button onClick={(e) => { e.stopPropagation(); setIsOpen(false); }} className="text-slate-500 hover:text-white transition-colors">
                  <X size={14} />
                </button>
              </div>

              <div className="space-y-4 relative z-10">
                <div className="flex items-center gap-2">
                  <div className="p-1.5 bg-blue-600/20 rounded-xl text-blue-400">
                    <BookOpen size={14} />
                  </div>
                  <h3 className="text-xs font-black text-white uppercase tracking-tight">{data.title}</h3>
                </div>

                <div className="space-y-3">
                  <p className="text-[11px] font-medium text-slate-200 leading-relaxed">{data.explanation}</p>

                  <div className="p-3 rounded-xl bg-white/5 border border-white/5 space-y-1">
                    <p className="text-[8px] font-black text-yellow-500 uppercase tracking-widest flex items-center gap-1">
                       <Zap size={8} /> Example
                    </p>
                    <p className="text-[10px] font-medium text-slate-400 italic leading-snug">"{data.example}"</p>
                  </div>

                  <p className="text-[9px] font-bold text-blue-400/80 leading-snug italic">"{data.why}"</p>
                </div>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
};

export default InfoTooltip;
