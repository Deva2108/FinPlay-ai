import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { TrendingUp, ChevronRight, Zap } from 'lucide-react';

export default function Insights() {
  const navigate = useNavigate();

  const insights = [
    {
      id: 1,
      title: "RELIANCE is getting strong attention today 👀",
      subtext: "More people are buying than usual",
      stock: "RELIANCE"
    },
    {
      id: 2,
      title: "Tech sector is showing recovery signs 🚀",
      subtext: "Investors are moving back into growth stocks",
      stock: "INFY"
    }
  ];

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
        <p className="text-slate-500 font-medium tracking-wide uppercase text-[10px]">What the market is thinking right now</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {insights.map((item) => (
          <motion.div 
            key={item.id}
            whileHover={{ y: -5, scale: 1.02 }}
            className="p-8 rounded-[2.5rem] bg-slate-900/50 border border-white/5 hover:border-blue-500/30 transition-all group relative overflow-hidden"
          >
            <div className="absolute top-0 right-0 p-6 opacity-5 group-hover:opacity-10 transition-opacity">
              <TrendingUp size={100} className="text-blue-500" />
            </div>
            
            <div className="relative z-10 space-y-6">
              <div className="space-y-2">
                <h3 className="text-xl font-black text-white leading-tight tracking-tight">
                  {item.title}
                </h3>
                <p className="text-slate-400 text-sm font-medium">
                  {item.subtext}
                </p>
              </div>

              <button 
                onClick={() => navigate('/market', { state: { selectedStock: item.stock } })}
                className="flex items-center gap-2 text-[10px] font-black uppercase tracking-[0.2em] text-blue-400 group-hover:text-blue-300 transition-colors"
              >
                Check in Market <ChevronRight size={14} className="group-hover:translate-x-1 transition-transform" />
              </button>
            </div>
          </motion.div>
        ))}
      </div>
    </motion.div>
  );
}
