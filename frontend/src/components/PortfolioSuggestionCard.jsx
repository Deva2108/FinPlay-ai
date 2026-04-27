import React, { useMemo } from 'react';
import { motion } from 'framer-motion';
import { Lightbulb, ArrowRight, Compass, ShieldAlert, BarChart3, AlertTriangle, Sparkles, Loader2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export default function PortfolioSuggestionCard({ holdings, totalYield, mentorAdvice, loadingMentor }) {
  const navigate = useNavigate();

  const suggestion = useMemo(() => {
    if (holdings.length === 0) return {
      title: "Start Building",
      text: "Your vault is empty. Explore the market to make your first move and begin your financial journey.",
      icon: <Compass size={24} className="text-blue-400" />,
      color: "blue",
      risk: "None",
      confidence: "95%"
    };

    if (loadingMentor) return {
      title: "Analyzing Portfolio...",
      text: "Our AI Mentor is currently scanning your holdings for deep insights. Please wait...",
      icon: <Loader2 size={24} className="text-blue-400 animate-spin" />,
      color: "blue",
      risk: "Calculating...",
      confidence: "..."
    };

    if (mentorAdvice) {
      return {
        title: "AI Mentor Insight",
        text: mentorAdvice,
        icon: <Sparkles size={24} className="text-blue-400" />,
        color: "blue",
        risk: totalYield < -5 ? "High" : totalYield < 0 ? "Medium" : "Low",
        confidence: "92%"
      };
    }

    if (holdings.length === 1) return {
      title: "Diversification Risk",
      text: "You're heavily concentrated in a single asset. Spreading risk across different sectors can protect your capital.",
      icon: <ShieldAlert size={24} className="text-orange-400" />,
      color: "orange",
      risk: "High",
      confidence: "78%"
    };

    if (totalYield > 5) return {
      title: "Profit Protection",
      text: "Significant gains detected. Consider booking partial profits to secure your wealth against volatility.",
      icon: <Lightbulb size={24} className="text-emerald-400" />,
      color: "emerald",
      risk: "Low",
      confidence: "82%"
    };

    if (totalYield < -3) return {
      title: "Drawdown Strategy",
      text: "Current performance is in a drawdown. Stay focused on your long-term thesis and avoid emotional exits.",
      icon: <ShieldAlert size={24} className="text-rose-400" />,
      color: "rose",
      risk: "Medium",
      confidence: "64%"
    };

    return {
      title: "Strategic Balance",
      text: "Your portfolio is currently stable. Monitor emerging trends and maintain your disciplined approach.",
      icon: <Lightbulb size={24} className="text-blue-400" />,
      color: "blue",
      risk: "Low",
      confidence: "88%"
    };
  }, [holdings, totalYield]);

  const glowColor = suggestion.color === 'emerald' ? 'rgba(16, 185, 129, 0.15)' : 
                    suggestion.color === 'orange' ? 'rgba(245, 158, 11, 0.15)' :
                    suggestion.color === 'rose' ? 'rgba(244, 63, 94, 0.15)' : 'rgba(59, 130, 246, 0.15)';

  const accentColor = suggestion.color === 'emerald' ? 'emerald' : 
                     suggestion.color === 'orange' ? 'orange' :
                     suggestion.color === 'rose' ? 'rose' : 'blue';

  return (
    <motion.div
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      whileHover={{ scale: 1.01 }}
      className={`relative rounded-[2.5rem] p-1 overflow-hidden group shadow-2xl transition-all duration-500`}
    >
      {/* Gradient Border */}
      <div className={`absolute inset-0 bg-gradient-to-br from-blue-500/20 via-purple-500/20 to-blue-500/20 group-hover:opacity-100 opacity-50 transition-opacity`} />
      
      <div className="relative bg-gradient-to-br from-slate-900/90 to-slate-800/60 rounded-[2.4rem] p-6 sm:p-10 backdrop-blur-xl h-full">
        {/* Inner Soft Glow */}
        <div className={`absolute inset-0 bg-${accentColor}-500/5 opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none rounded-[2.4rem] shadow-inner`} />
        
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 items-center relative z-10">
          
          {/* Left Content (70%) */}
          <div className="md:col-span-2 space-y-6">
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <span className="text-[10px] font-black text-slate-500 uppercase tracking-[0.4em]">Next Move</span>
                <div className={`h-[1px] w-12 bg-white/10`} />
              </div>
              <div className="flex items-start gap-4">
                <div className={`p-3 rounded-2xl bg-${accentColor}-500/10 border border-${accentColor}-500/20 shadow-inner`}>
                  {suggestion.icon}
                </div>
                <h4 className="text-2xl sm:text-3xl font-black text-white tracking-tighter leading-tight">
                  {suggestion.title}
                </h4>
              </div>
            </div>

            <p className="text-sm sm:text-base text-slate-400 font-medium leading-relaxed max-w-2xl">
              {suggestion.text}
            </p>

            <div className="flex flex-wrap items-center gap-6 pt-2">
              <div className="flex items-center gap-2 px-4 py-2 bg-white/5 rounded-xl border border-white/5">
                <AlertTriangle size={14} className={suggestion.risk === 'High' ? 'text-rose-400' : 'text-slate-400'} />
                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Risk Level:</span>
                <span className={`text-[10px] font-black uppercase tracking-widest ${
                  suggestion.risk === 'High' ? 'text-rose-400' : suggestion.risk === 'Medium' ? 'text-orange-400' : 'text-emerald-400'
                }`}>{suggestion.risk}</span>
              </div>
              <div className="flex items-center gap-2 px-4 py-2 bg-white/5 rounded-xl border border-white/5">
                <BarChart3 size={14} className="text-blue-400" />
                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Confidence:</span>
                <span className="text-[10px] font-black text-blue-400 uppercase tracking-widest">{suggestion.confidence}</span>
              </div>
            </div>
          </div>

          {/* Right Section (30%) - Buttons */}
          <div className="flex flex-col gap-3 w-full">
            <motion.button 
              whileTap={{ scale: 0.96 }}
              onClick={() => navigate('/insights')}
              className="w-full py-4 bg-gradient-to-r from-blue-600 to-blue-500 hover:from-blue-500 hover:to-blue-400 text-white rounded-2xl text-[10px] font-black uppercase tracking-widest shadow-xl shadow-blue-600/20 transition-all flex items-center justify-center gap-2 group/btn"
            >
              Intelligence Insights <ArrowRight size={14} className="group-hover/btn:translate-x-1 transition-transform" />
            </motion.button>
            <motion.button 
              whileTap={{ scale: 0.96 }}
              onClick={() => navigate('/')}
              className="w-full py-4 bg-white/5 hover:bg-white/10 text-white rounded-2xl text-[10px] font-black uppercase tracking-widest border border-white/10 transition-all flex items-center justify-center gap-2 group/btn"
            >
              Explore Markets <Compass size={14} className="group-hover/btn:rotate-12 transition-transform" />
            </motion.button>
          </div>

        </div>
      </div>
    </motion.div>
  );
}
