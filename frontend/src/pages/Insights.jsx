import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { TrendingUp, ChevronRight, Zap, Loader2, PlayCircle } from 'lucide-react';
import { getFamousInsights } from '../services/api';

export default function Insights() {
  const navigate = useNavigate();
  const [insights, setInsights] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchInsights = async () => {
      try {
        const res = await getFamousInsights("ALL");
        setInsights(res?.data || []);
        if (res?.meta?.status === "SYNCING") {
          setLoading(true);
        } else {
          setLoading(false);
        }
      } catch (err) {
        console.error("Failed to fetch famous insights", err);
        setLoading(false);
      }
    };
    fetchInsights();
  }, []);

  if (loading) {
    return (
      <div className="min-h-[60vh] flex flex-col items-center justify-center gap-4">
        <Loader2 className="animate-spin text-blue-500" size={40} />
        <p className="text-[10px] font-black text-blue-500 uppercase tracking-widest animate-pulse">Loading investor commentary</p>
      </div>
    );
  }

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }} 
      animate={{ opacity: 1, y: 0 }} 
      className="max-w-4xl mx-auto space-y-10 p-6 sm:p-10"
    >
      <div className="space-y-2">
        <h2 className="text-3xl font-black text-white tracking-tighter flex items-center gap-3">
          <Zap className="text-blue-500" fill="currentColor" size={24} /> TRENDING INSIGHTS
        </h2>
        <div className="flex items-center gap-3">
           <p className="text-slate-500 font-medium tracking-wide uppercase text-[10px]">Curated commentary from notable investors · refreshed throughout the day</p>
           <div className="h-4 w-[1px] bg-white/10" />
           <span className="text-[10px] font-black text-slate-400 tabular-nums">{insights.length} active</span>
        </div>
      </div>

      {insights.length === 0 ? (
        <div className="p-16 rounded-[2.5rem] bg-slate-900/50 border border-white/5 text-center space-y-4">
          <div className="w-16 h-16 bg-blue-500/10 rounded-full flex items-center justify-center mx-auto border border-blue-500/20">
            <Zap className="text-blue-400" size={32} />
          </div>
          <div className="space-y-2">
            <h3 className="text-xl font-black text-white uppercase tracking-tight">No fresh insights yet</h3>
            <p className="text-slate-500 font-bold max-w-xs mx-auto text-sm leading-relaxed">
              Curated investor commentary updates throughout the trading day. Check back shortly, or explore the live market in the meantime.
            </p>
          </div>
          <button 
            onClick={() => navigate('/market')}
            className="px-6 py-3 bg-white text-slate-950 font-black rounded-xl text-[10px] uppercase tracking-widest hover:bg-slate-200 transition-all"
          >
            Explore Live Market
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {insights.map((item, idx) => (
            <motion.div 
              key={idx}
              whileHover={{ y: -5, scale: 1.02 }}
              className="p-8 rounded-[2.5rem] bg-slate-900/50 border border-white/5 hover:border-blue-500/30 transition-all group relative overflow-hidden"
            >
              <div className="absolute top-0 right-0 p-6 opacity-5 group-hover:opacity-10 transition-opacity">
                <TrendingUp size={100} className="text-blue-500" />
              </div>
              
              <div className="relative z-10 space-y-6">
                <div className="space-y-3">
                  <div className="flex items-center gap-2">
                    <div className="px-2 py-0.5 rounded-md bg-blue-500/10 text-blue-400 border border-blue-500/20 text-[9px] font-black uppercase tracking-widest">
                      {item.investor || "Expert"}
                    </div>
                  </div>
                  <h3 className="text-xl font-black text-white leading-tight tracking-tight">
                    {item.title}
                  </h3>
                  <p className="text-slate-400 text-sm font-medium leading-relaxed italic">
                    "{item.message}"
                  </p>
                </div>

                <div className="flex flex-col gap-3">
                  <button 
                    onClick={() => navigate('/market', { state: { selectedStock: item.stock } })}
                    className="flex items-center gap-2 text-[10px] font-black uppercase tracking-[0.2em] text-blue-400 group-hover:text-blue-300 transition-colors"
                  >
                    Check in Market <ChevronRight size={14} className="group-hover:translate-x-1 transition-transform" />
                  </button>
                  {item.podcastUrl && (
                    <a 
                      href={item.podcastUrl} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      className="flex items-center gap-2 text-[10px] font-black uppercase tracking-[0.2em] text-rose-500 hover:text-rose-400 transition-colors"
                    >
                      <PlayCircle size={14} /> Watch Insight
                    </a>
                  )}
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </motion.div>
  );
}
