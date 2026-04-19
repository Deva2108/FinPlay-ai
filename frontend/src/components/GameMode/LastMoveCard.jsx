import React from 'react';
import { motion } from 'framer-motion';
import { CheckCircle2, XCircle, ArrowRight, Lightbulb, TrendingUp, TrendingDown } from 'lucide-react';

export default function LastMoveCard({ decision, onNext }) {
  if (!decision) return null;

  const { stock, choice, isCorrect } = decision;

  return (
    <section>
      <div className="flex items-center justify-between mb-6 ml-2">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-blue-500/10 rounded-xl">
            <CheckCircle2 size={20} className="text-blue-500" />
          </div>
          <h2 className="text-xl sm:text-2xl font-black text-white uppercase tracking-tighter">Your Last Move</h2>
        </div>
        <button 
          onClick={onNext}
          className="group flex items-center gap-2 text-[10px] font-black text-blue-500 uppercase tracking-widest hover:text-blue-400 transition-colors"
        >
          Play Next <ArrowRight size={14} className="group-hover:translate-x-1 transition-transform" />
        </button>
      </div>

      <motion.div 
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-slate-900/40 border border-slate-800 rounded-[2.5rem] overflow-hidden shadow-2xl backdrop-blur-md relative"
      >
        <div className="p-8">
          <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6 mb-8">
            <div className="flex items-center gap-6">
              <div className="w-16 h-16 rounded-[1.5rem] bg-slate-800 flex items-center justify-center font-black text-white text-xl shadow-inner border border-white/5">
                {stock.symbol[0]}
              </div>
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <h4 className="font-black text-white text-xl tracking-tight">{stock.symbol}</h4>
                  <span className={`px-2 py-0.5 rounded-lg text-[10px] font-black uppercase tracking-widest ${isCorrect ? 'bg-emerald-500/10 text-emerald-500' : 'bg-rose-500/10 text-rose-500'}`}>
                    {isCorrect ? 'Masterful' : 'Learning'}
                  </span>
                </div>
                <p className="text-sm text-slate-500 font-bold uppercase tracking-wider">{stock.name}</p>
              </div>
            </div>

            <div className="flex items-center gap-8 bg-slate-800/30 px-6 py-4 rounded-2xl border border-white/5">
              <div>
                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest block mb-1">Your Decision</span>
                <span className={`text-sm font-black uppercase tracking-widest ${choice === 'buy' ? 'text-blue-500' : 'text-slate-400'}`}>
                  {choice === 'buy' ? 'BUY' : 'SKIP'}
                </span>
              </div>
              <div className="h-8 w-[1px] bg-white/10" />
              <div>
                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest block mb-1">Market Outcome</span>
                <div className="flex items-center gap-1">
                  {stock.isPositive ? <TrendingUp size={14} className="text-emerald-500" /> : <TrendingDown size={14} className="text-rose-500" />}
                  <span className={`text-sm font-black uppercase tracking-widest ${stock.isPositive ? 'text-emerald-500' : 'text-rose-500'}`}>
                    {stock.impact}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div className="bg-blue-600/5 border border-blue-500/10 rounded-3xl p-6 relative overflow-hidden group">
            <div className="absolute top-0 right-0 p-4 opacity-5 group-hover:scale-110 transition-transform">
              <Lightbulb size={80} className="text-blue-500" />
            </div>
            <div className="flex items-start gap-4 z-10 relative">
              <div className="p-2 bg-blue-500/20 rounded-xl mt-1">
                <Lightbulb size={20} className="text-blue-500" />
              </div>
              <div>
                <h5 className="text-xs font-black text-blue-400 uppercase tracking-[0.2em] mb-2">The Lesson</h5>
                <p className="text-slate-300 font-medium leading-relaxed italic">
                  "{stock.explanation}"
                </p>
              </div>
            </div>
          </div>
        </div>

        <div className="px-8 py-4 bg-slate-900/60 border-t border-white/5 flex items-center justify-between">
           <p className="text-[10px] font-black text-slate-600 uppercase tracking-widest">Decision ID: #FP-{Math.floor(Math.random()*9000)+1000}</p>
           <button 
             onClick={onNext}
             className="text-[10px] font-black text-white bg-blue-600 px-6 py-2 rounded-xl uppercase tracking-widest hover:bg-blue-500 transition-all shadow-lg shadow-blue-600/20 active:scale-95"
           >
             Continue Training
           </button>
        </div>
      </motion.div>
    </section>
  );
}
