import React from 'react';
import { AreaChart, Area, ResponsiveContainer, YAxis, Tooltip, XAxis } from 'recharts';

export default function MiniChart({ timeframe = '1D', color = '#10b981', data }) {
  // If data is provided externally, use it. Otherwise, use empty array (no more mock data for "real" experience)
  const chartData = data && data.length > 0 ? data : [];

  if (chartData.length === 0) {
    return (
      <div className="h-48 w-full flex flex-col items-center justify-center gap-2 bg-slate-950/20 rounded-2xl border border-dashed border-white/5">
         <div className="w-1.5 h-1.5 bg-blue-500 rounded-full animate-ping" />
         <p className="text-[8px] font-black text-slate-600 uppercase tracking-widest">Mapping data...</p>
      </div>
    );
  }

  return (
    <div className="h-48 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={chartData}>
          <defs>
            <linearGradient id={`gradient-${timeframe}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={color} stopOpacity={0.3}/>
              <stop offset="95%" stopColor={color} stopOpacity={0}/>
            </linearGradient>
          </defs>
          <XAxis dataKey="timestamp" hide />
          <YAxis hide domain={['dataMin', 'dataMax']} />
          <Tooltip
            content={() => null} // Purely visual sparkline
            cursor={{ stroke: color, strokeWidth: 1, strokeDasharray: '4 4' }}
          />
          <Area
            type="monotone"
            dataKey="value"
            stroke={color}
            strokeWidth={3}
            fillOpacity={1}
            fill={`url(#gradient-${timeframe})`}
            animationDuration={1500}
            activeDot={{ r: 4, stroke: '#0f172a', strokeWidth: 2, fill: color }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
