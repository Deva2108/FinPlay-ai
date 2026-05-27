import React, { useMemo } from 'react';
import { AreaChart, Area, ResponsiveContainer, YAxis, Tooltip, XAxis } from 'recharts';

export default function MiniChart({ timeframe = '1D', color = '#10b981', data }) {
  // Downsample: cap at 60 SVG points — sparklines need even fewer.
  // type="linear" replaces "monotone": linear skips the cubic bezier
  // interpolation pass that monotone runs per-segment, cutting path CPU ~3×.
  // isAnimationActive={false} removes the 1 500ms entrance animation that
  // was blocking interaction and burning frames on mobile every panel open.
  const chartData = useMemo(() => {
    const raw = data && data.length > 0 ? data : [];
    if (raw.length === 0) return [];
    if (raw.length <= 60) return raw;
    const n = Math.ceil(raw.length / 60);
    // Always include last point so the trail endpoint is accurate.
    return raw.filter((_, i) => i % n === 0 || i === raw.length - 1);
  }, [data]);

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
            content={() => null}
            cursor={{ stroke: color, strokeWidth: 1, strokeDasharray: '4 4' }}
          />
          <Area
            type="linear"
            dataKey="value"
            stroke={color}
            strokeWidth={3}
            fillOpacity={1}
            fill={`url(#gradient-${timeframe})`}
            isAnimationActive={false}
            activeDot={{ r: 4, stroke: '#0f172a', strokeWidth: 2, fill: color }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
