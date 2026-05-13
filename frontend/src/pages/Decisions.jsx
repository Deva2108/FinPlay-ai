import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { getRecentDecisions } from '../services/api';
import { History, TrendingUp, TrendingDown, Clock, Activity, ArrowUpRight } from 'lucide-react';
import { formatPrice } from '../utils/formatters';

export default function DecisionHistory() {
  const [decisions, setDecisions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        const res = await getRecentDecisions();
        if (res.success) setDecisions(res.data || []);
      } catch (err) {
        console.error("Failed to fetch history", err);
      } finally {
        setLoading(false);
      }
    };
    fetchHistory();
  }, []);

  return (
    <div className="space-y-8">
      <header className="flex justify-between items-center">
        <div className="space-y-1">
          <h2 className="text-3xl font-black text-white tracking-tighter uppercase">Decision Arena Log</h2>
          <p className="text-slate-500 text-xs font-bold uppercase tracking-widest flex items-center gap-2">
            <History size={12} /> Pattern Recognition Engine
          </p>
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        <div className="lg:col-span-8 space-y-4">
          {loading ? (
            <div className="p-20 flex flex-col items-center justify-center gap-4 bg-white/5 rounded-[2rem] border border-dashed border-white/10">
              <div className="w-10 h-10 border-4 border-blue-500/20 border-t-blue-500 rounded-full animate-spin" />
              <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Reconstructing Behavioral Timeline...</p>
            </div>
          ) : decisions.length > 0 ? (
            decisions.map((d, i) => (
              <motion.div 
                key={d.id || i}
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.05 }}
                className="bg-slate-900/40 border border-white/5 p-6 rounded-3xl hover:bg-slate-900/60 transition-all group flex items-center justify-between"
              >
                <div className="flex items-center gap-6">
                  <div className={`w-12 h-12 rounded-2xl flex items-center justify-center border ${d.action === 'BUY' ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-500' : 'bg-slate-500/10 border-slate-500/20 text-slate-400'}`}>
                    {d.action === 'BUY' ? <TrendingUp size={20} /> : <Activity size={20} />}
                  </div>
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                       <span className="text-lg font-black text-white">{d.symbol}</span>
                       <span className={`text-[8px] font-black uppercase px-2 py-0.5 rounded-md ${d.action === 'BUY' ? 'bg-emerald-500/20 text-emerald-500' : 'bg-slate-500/20 text-slate-400'}`}>{d.action}</span>
                    </div>
                    <p className="text-[10px] text-slate-500 font-bold uppercase tracking-widest flex items-center gap-2">
                       <Clock size={10} /> {new Date(d.timestamp).toLocaleString()}
                    </p>
                  </div>
                </div>
                
                <div className="text-right space-y-1">
                   <p className="text-sm font-black text-white">{formatPrice(d.price, d.market)}</p>
                   <p className="text-[9px] text-slate-600 font-bold uppercase tracking-widest group-hover:text-blue-400 transition-colors">Execution Point</p>
                </div>
              </motion.div>
            ))
          ) : (
            <div className="p-20 text-center text-slate-500 font-black text-xs uppercase tracking-[0.3em] border-2 border-dashed border-white/5 rounded-[3rem]">
              The Arena is quiet. Make your first move.
            </div>
          )}
        </div>

        <div className="lg:col-span-4 space-y-6">
           <div className="bg-gradient-to-br from-indigo-600 to-blue-500 p-8 rounded-[2.5rem] shadow-xl shadow-blue-500/10 relative overflow-hidden group">
              <div className="absolute top-0 right-0 p-6 opacity-10 group-hover:scale-110 transition-transform"><Clock size={120} /></div>
              <div className="relative z-10 space-y-6">
                 <div className="space-y-2">
                    <h3 className="text-xl font-black text-white uppercase tracking-tighter leading-none">Behavioral<br/>Evolution</h3>
                    <p className="text-blue-100/70 text-xs font-medium leading-relaxed">Reviewing past decisions helps identify emotional biases and improves long-term intuition.</p>
                 </div>
                 <div className="pt-4 border-t border-white/10 flex items-center justify-between">
                    <span className="text-[10px] font-black text-white/50 uppercase tracking-widest">Total Logged</span>
                    <span className="text-2xl font-black text-white">{decisions.length}</span>
                 </div>
              </div>
           </div>
        </div>
      </div>
    </div>
  );
}
