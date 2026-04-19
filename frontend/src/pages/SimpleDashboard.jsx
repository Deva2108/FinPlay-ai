import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Zap, TrendingUp, ChevronRight, CheckCircle2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

const SimpleDashboard = () => {
  const navigate = useNavigate();
  const [data, setData] = useState({ decisions: [], profit: 0 });

  useEffect(() => {
    try {
      const raw = localStorage.getItem("finplay");
      const finplayData = raw ? JSON.parse(raw) : null;
      if (finplayData?.user) {
        setData({
          decisions: finplayData.user.decisions || [],
          profit: finplayData.user.profit || 0
        });
      }
    } catch (e) {
      console.error("Dashboard Load Error", e);
    }
  }, []);

  const handleEnterMarket = () => {
    navigate("/arena");
  };

  return (
    <div className="min-h-screen bg-[#0b0b0b] text-white flex items-center justify-center p-6">
      <div className="max-w-md w-full space-y-10">
        
        {/* Header */}
        <motion.div 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="text-center space-y-2"
        >
          <h1 className="text-3xl font-black uppercase tracking-tighter">Your Journey Starts Here</h1>
          <p className="text-slate-500 text-xs font-bold uppercase tracking-[0.2em]">Session Overview</p>
        </motion.div>

        {/* Stats Card */}
        <motion.div 
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.2 }}
          className="p-8 rounded-[2.5rem] bg-white/5 border border-white/10 space-y-8"
        >
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-blue-600/20 rounded-xl text-blue-400">
                <CheckCircle2 size={18} />
              </div>
              <h3 className="text-sm font-black uppercase tracking-widest text-slate-400">Decisions Taken</h3>
            </div>
            <p className="text-lg font-bold text-white leading-relaxed">
              {data.decisions.length > 0 ? data.decisions.join(", ") : "None yet"}
            </p>
          </div>

          <div className="h-px w-full bg-white/5" />

          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-emerald-600/20 rounded-xl text-emerald-400">
                <TrendingUp size={18} />
              </div>
              <h3 className="text-sm font-black uppercase tracking-widest text-slate-400">Total Profit</h3>
            </div>
            <p className="text-4xl font-black text-emerald-400 tracking-tighter">
              ₹{data.profit}
            </p>
          </div>
        </motion.div>

        {/* Next Step */}
        <motion.div 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
          className="space-y-6"
        >
          <div className="text-center space-y-1">
            <h4 className="text-sm font-black uppercase tracking-widest text-white">Next Step</h4>
            <p className="text-slate-400 text-sm font-medium">Now let’s try real market decisions.</p>
          </div>

          <motion.button
            onClick={handleEnterMarket}
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            className="relative z-50 cursor-pointer pointer-events-auto w-full bg-blue-600 text-white font-black py-6 rounded-3xl uppercase tracking-widest text-sm shadow-xl shadow-blue-600/20 flex items-center justify-center gap-3 group"
          >
            Enter Arena <ChevronRight size={18} className="group-hover:translate-x-1 transition-transform" />
          </motion.button>
        </motion.div>

        {/* Background Glow */}
        <div className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-blue-600/5 blur-[120px] rounded-full pointer-events-none -z-10" />
      </div>
    </div>
  );
};

export default SimpleDashboard;
