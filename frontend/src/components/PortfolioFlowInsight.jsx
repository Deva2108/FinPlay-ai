import React from 'react';
import { motion } from 'framer-motion';
import { Activity, AlertTriangle, CheckCircle2 } from 'lucide-react';

const sectorData = [
  { name: "Tech", value: 65 },
  { name: "Banking", value: 35 },
  { name: "Energy", value: 5 }
];

const PortfolioFlowInsight = () => {
  const overexposedSector = sectorData.find(s => s.value > 60);

  return (
    <motion.div 
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="bg-slate-900/40 border border-slate-800 p-4 rounded-3xl space-y-3 shadow-xl backdrop-blur-md"
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Activity size={14} className="text-blue-400" />
          <h3 className="text-sm font-semibold text-white tracking-tight">Portfolio Flow</h3>
        </div>
        {overexposedSector ? (
          <div className="flex items-center gap-1 text-[10px] font-bold text-rose-400 uppercase tracking-tighter">
            <AlertTriangle size={10} /> Overexposed
          </div>
        ) : (
          <div className="flex items-center gap-1 text-[10px] font-bold text-emerald-400 uppercase tracking-tighter">
            <CheckCircle2 size={10} /> Balanced
          </div>
        )}
      </div>

      <div className="space-y-2.5">
        {(sectorData || []).map((sector) => (
          <div key={sector.name} className="space-y-1">
            <div className="flex justify-between items-center text-[10px] font-medium">
              <span className="text-slate-400 opacity-70 uppercase tracking-wider">{sector.name}</span>
              <span className="text-white">{sector.value}%</span>
            </div>
            <div className="w-full bg-slate-800 rounded-full h-1.5 overflow-hidden p-0.5">
              <motion.div 
                initial={{ width: 0 }}
                animate={{ width: `${sector.value}%` }}
                transition={{ duration: 1, ease: "easeOut" }}
                className={`h-full rounded-full transition-all ${sector.value > 60 ? 'bg-rose-500 shadow-[0_0_8px_rgba(244,63,94,0.4)]' : 'bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.4)]'}`}
              />
            </div>
          </div>
        ))}
      </div>

      <div className="pt-2 border-t border-white/5">
        <p className="text-[11px] font-medium text-slate-300 leading-relaxed italic">
          {overexposedSector 
            ? `You are overexposed to ${overexposedSector.name}. Consider diversifying into other sectors to reduce volatility.`
            : "Your portfolio is reasonably balanced across sectors."}
        </p>
      </div>
    </motion.div>
  );
};

export default PortfolioFlowInsight;
