import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, TrendingDown, Info, Link as LinkIcon, Activity, HelpCircle, Bot, Sparkles, Loader2 } from 'lucide-react';
import { explainStock } from '../../services/api';

export default function LiveContextPanel({ stock, gameStep, onShowInsight }) {
  const [isExplaining, setIsExplaining] = useState(false);
  const [aiExplanation, setAiExplanation] = useState(null);

  if (!stock) return null;

  const isRevealed = gameStep === 'OBJECTIVE';

  const handleAiExplain = async () => {
    if (isExplaining) return;
    setIsExplaining(true);
    try {
      const result = await explainStock({
        symbol: stock.symbol,
        situation: stock.situation
      });
      setAiExplanation(result.explanation);
    } catch (err) {
      console.error("AI Explanation Error:", err);
      setAiExplanation("This insight requires an active session. Please log in to unlock AI analysis.");
    } finally {
      setIsExplaining(false);
    }
  };

  const handleLogicClick = () => {
    if (!isRevealed || !onShowInsight) return;
    onShowInsight({
      type: 'game',
      title: 'Market Logic Analysis',
      explanation: 'The system evaluates macro and micro economic factors before rendering a verdict on a stock.',
      insight: stock.explanation,
      data: [
        { label: 'Asset', value: stock.symbol },
        { label: 'Impact Prediction', value: stock.impact, color: stock.isPositive ? 'text-emerald-400' : 'text-rose-400' }
      ],
      actions: [{ label: 'Dismiss' }]
    });
  };

  return (
    <div className="h-full flex flex-col gap-4">
      <div className="bg-slate-900/40 border border-slate-800 rounded-[2rem] p-6 backdrop-blur-md flex-1 overflow-y-auto">
        <div className="flex items-center gap-2 mb-6">
          <div className="p-1.5 bg-blue-500/10 rounded-lg">
            <Activity size={16} className="text-blue-500" />
          </div>
          <h3 className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Live Context</h3>
        </div>

        <div className="space-y-6">
          {/* Sector Trend */}
          <div>
            <span className="text-[10px] font-black text-slate-600 uppercase tracking-[0.2em] block mb-2">Sector Trend</span>
            <div className="flex items-center gap-3">
              <div className={`p-2 rounded-xl ${stock.isPositive ? 'bg-emerald-500/10' : 'bg-rose-500/10'}`}>
                {stock.isPositive ? <TrendingUp size={18} className="text-emerald-500" /> : <TrendingDown size={18} className="text-rose-500" />}
              </div>
              <div>
                <p className="text-sm font-black text-white">{stock.market === 'IN' ? 'NSE Sector Index' : 'S&P 500 Sector'}</p>
                <p className={`text-xs font-bold ${stock.isPositive ? 'text-emerald-500' : 'text-rose-500'}`}>
                  {stock.isPositive ? '+1.8%' : '-0.9%'} Today
                </p>
              </div>
            </div>
          </div>

          {/* Logic/Reason */}
          <div>
            <span className="text-[10px] font-black text-slate-600 uppercase tracking-[0.2em] block mb-2">Market Logic</span>
            <div 
              className={`bg-slate-800/40 rounded-2xl p-4 border border-white/5 relative group ${isRevealed ? 'cursor-pointer hover:bg-slate-800/80 transition-colors' : ''}`}
              onClick={handleLogicClick}
            >
              {isRevealed && <HelpCircle size={14} className="absolute top-2 right-2 text-blue-400 opacity-0 group-hover:opacity-100 transition-opacity" />}
              {isRevealed ? (
                <motion.p 
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  className="text-xs text-slate-300 font-medium leading-relaxed pr-4"
                >
                  {stock.explanation}
                </motion.p>
              ) : (
                <div className="space-y-2">
                  <div className="h-2 w-full bg-slate-700/50 rounded-full animate-pulse" />
                  <div className="h-2 w-2/3 bg-slate-700/50 rounded-full animate-pulse" />
                  <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mt-3">Analyzing sentiment...</p>
                </div>
              )}
            </div>
          </div>

          {/* AI Explanation Feature */}
          <div className="pt-2">
            {!aiExplanation ? (
              <button 
                onClick={handleAiExplain}
                disabled={isExplaining}
                className="w-full py-3 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 disabled:opacity-50 text-white rounded-xl text-[10px] font-black uppercase tracking-[0.2em] flex items-center justify-center gap-2 shadow-lg shadow-blue-900/20 active:scale-95 transition-all"
              >
                {isExplaining ? <Loader2 size={14} className="animate-spin" /> : <Bot size={14} />}
                {isExplaining ? "Thinking..." : "Explain This 🤖"}
              </button>
            ) : (
              <motion.div 
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="bg-indigo-500/10 border border-indigo-500/20 rounded-2xl p-4 relative overflow-hidden"
              >
                <div className="absolute top-0 right-0 p-2 opacity-10">
                  <Sparkles size={24} className="text-indigo-400" />
                </div>
                <div className="flex items-center gap-2 mb-2">
                   <Bot size={12} className="text-indigo-400" />
                   <span className="text-[9px] font-black text-indigo-400 uppercase tracking-widest">AI Insight</span>
                </div>
                <p className="text-xs text-indigo-100 font-medium leading-relaxed">
                  {aiExplanation}
                </p>
                <button 
                  onClick={() => setAiExplanation(null)}
                  className="mt-3 text-[9px] font-black text-indigo-400/60 uppercase tracking-widest hover:text-indigo-400 transition-colors"
                >
                  Clear Insight
                </button>
              </motion.div>
            )}
          </div>

          {/* Similar Stocks */}
          <div>
            <span className="text-[10px] font-black text-slate-600 uppercase tracking-[0.2em] block mb-3">Similar Moves</span>
            <div className="space-y-2">
              {[1, 2].map((i) => (
                <div key={i} className="flex items-center justify-between p-3 rounded-xl bg-white/5 border border-white/5 opacity-50">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-slate-800 flex items-center justify-center text-[10px] font-black text-slate-500">?</div>
                    <div className="h-2 w-16 bg-slate-800 rounded-full" />
                  </div>
                  <LinkIcon size={12} className="text-slate-600" />
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="bg-blue-600/10 border border-blue-500/20 rounded-2xl p-4">
        <div className="flex items-start gap-3">
          <Info size={16} className="text-blue-500 shrink-0 mt-0.5" />
          <p className="text-[10px] text-blue-300 font-bold leading-relaxed uppercase tracking-wide">
            Decisions here affect your learning score and virtual portfolio performance.
          </p>
        </div>
      </div>
    </div>
  );
}
