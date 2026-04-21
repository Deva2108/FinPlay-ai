import React from 'react';
import { HelpCircle, TrendingUp, TrendingDown } from 'lucide-react';
import InfoTooltip from './InfoTooltip';

export default function IndexCard({ symbol, value, change, percent, onClick }) {
  const isPositive = (change || "").startsWith('+');

  return (
    <div 
      onClick={onClick}
      className="bg-slate-900/60 p-3 sm:p-4 rounded-xl border border-white/5 hover:bg-slate-800/80 transition-all cursor-pointer group relative overflow-hidden"
    >
      <div className="absolute inset-0 bg-gradient-to-br from-white/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
      
      <div className="relative z-10">
        <div className="text-[7px] sm:text-[8px] font-black text-slate-500 uppercase tracking-widest mb-1 flex items-center justify-between">
          <span className="truncate pr-1">{symbol}</span>
          <div className="flex items-center gap-1 shrink-0">
            {isPositive ? <TrendingUp size={8} className="text-emerald-500" /> : <TrendingDown size={8} className="text-rose-500" />}
            <InfoTooltip concept="index" />
          </div>
        </div>
        
        <div className="flex justify-between items-end gap-1">
          <p className="text-xs sm:text-sm font-black text-white truncate">{value}</p>
          <div className="flex flex-col items-end shrink-0">
            <span className={`text-[8px] sm:text-[9px] font-black ${isPositive ? 'text-emerald-500' : 'text-rose-500'}`}>
              {percent}
            </span>
            <span className={`text-[6px] sm:text-[7px] font-bold opacity-50 ${isPositive ? 'text-emerald-500' : 'text-rose-500'}`}>
              {change}
            </span>
          </div>
        </div>
      </div>
      
      {/* Subtle background glow */}
      <div className={`absolute -bottom-4 -right-4 w-12 h-12 blur-2xl rounded-full opacity-20 ${isPositive ? 'bg-emerald-500' : 'bg-rose-500'}`} />
    </div>
  );
}
