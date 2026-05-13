import React from 'react';
import { Shield, Zap, AlertTriangle, Cloud } from 'lucide-react';

/**
 * Standard Data Badge to show simulation/delay status
 */
const DataBadge = ({ meta, className = "" }) => {
  if (!meta) return null;
  const { isSimulated, isDelayed, source } = meta;

  if (isSimulated) {
    return (
      <div className={`flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-[8px] font-black text-amber-400 uppercase tracking-widest backdrop-blur-md shadow-lg ${className}`}>
        <Zap size={10} className="fill-amber-400" />
        Simulated
      </div>
    );
  }

  if (isDelayed) {
    return (
      <div className={`flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-800/60 border border-white/10 text-[8px] font-black text-slate-400 uppercase tracking-widest backdrop-blur-md shadow-lg ${className}`}>
        <AlertTriangle size={10} />
        Delayed (15m)
      </div>
    );
  }

  if (source) {
    return (
      <div className={`flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/20 text-[8px] font-black text-blue-500 uppercase tracking-widest backdrop-blur-md shadow-lg ${className}`}>
        <Cloud size={10} />
        {source}
      </div>
    );
  }

  return null;
};

export default DataBadge;
