import React, { useMemo, useCallback } from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { formatPrice } from '../utils/formatters';
import DataBadge from './DataBadge';
import { useMarket } from '../context/MarketContext';

function ChartComponent({ data, meta, color = "#3b82f6", height = 300, onPointClick, isIndex = false }) {
  const hasData = Array.isArray(data) && data.length > 0;
  const currency = meta?.currency || (hasData ? data[0]?.currency : null) || 'INR';

  // Intl.DateTimeFormat instances hoisted outside the map loop.
  // toLocaleTimeString/toLocaleDateString/toLocaleString recreate a formatter
  // object on every call when options are passed — on a 365-point 1Y dataset
  // that means 1 095 formatter constructions per useMemo run, causing
  // main-thread lockups and dropped frames on mobile. Hoisting to three
  // stable instances reduces that to exactly 3 constructions per render.
  const timeFmt = useMemo(() => new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' }), []);
  const dateFmt = useMemo(() => new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }), []);
  const fullFmt = useMemo(() => new Intl.DateTimeFormat(undefined, {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
  }), []);

  // Memoize the per-point transformation — was rebuilt on every render
  // (tooltip hover, resize, parent state changes), causing chart stutter.
  const processedData = useMemo(() => {
    if (!hasData) return [];
    return data.map((d, index) => {
      const rawTime = d.date || d.time || d.timestamp;
      let date;
      if (typeof rawTime === 'string') {
        date = new Date(rawTime);
      } else {
        date = rawTime ? new Date(rawTime > 10000000000 ? rawTime : rawTime * 1000) : new Date();
      }
      const value = d.value || d.close || 0;
      return {
        ...d,
        value,
        formattedTime: timeFmt.format(date),
        formattedDate: dateFmt.format(date),
        fullDate:      fullFmt.format(date),
        index
      };
    });
  }, [data, hasData, timeFmt, dateFmt, fullFmt]);

  // Bounds computed once per dataset — single O(n) pass, avoids
  // Math.min/max(...spread) which is O(n) AND allocates a temporary array.
  const { min, max, padding, isDeep, xAxisKey } = useMemo(() => {
    if (processedData.length === 0) {
      return { min: 0, max: 0, padding: 0, isDeep: false, xAxisKey: 'formattedTime' };
    }
    let lo = Infinity, hi = -Infinity;
    for (const p of processedData) {
      const v = p.value;
      if (v < lo) lo = v;
      if (v > hi) hi = v;
    }
    if (!Number.isFinite(lo)) lo = 0;
    if (!Number.isFinite(hi)) hi = 0;
    const deep = processedData.length > 30;
    return {
      min: lo,
      max: hi,
      padding: (hi - lo) * 0.15,
      isDeep: deep,
      xAxisKey: deep ? 'formattedDate' : 'formattedTime'
    };
  }, [processedData]);

  const handleChartClick = useCallback((state) => {
    if (onPointClick && state?.activePayload) onPointClick(state.activePayload[0].payload);
  }, [onPointClick]);

  const yAxisFormatter = useCallback((v) => formatPrice(v, currency, !isIndex), [currency, isIndex]);

  if (!hasData) {
    return (
      <div style={{ height }} className="w-full flex flex-col items-center justify-center gap-2 bg-slate-900/40 rounded-3xl border border-white/5 shadow-inner">
        <div className="w-2 h-2 rounded-full bg-slate-600 animate-pulse" />
        <p className="text-slate-500 font-bold uppercase text-[10px] tracking-[0.2em]">Waiting on price history</p>
        <p className="text-slate-700 text-[9px] font-medium">Try a different timeframe or check back shortly</p>
      </div>
    );
  }

  const CustomTooltip = ({ active, payload }) => {
    if (active && payload && payload.length) {
      const currentPoint = payload[0].payload;
      const prevPoint = processedData[currentPoint.index - 1];
      const change = prevPoint ? ((currentPoint.value - prevPoint.value) / prevPoint.value * 100).toFixed(2) : 0;
      
      return (
        <div className="bg-slate-950/90 backdrop-blur-xl border border-white/10 p-4 rounded-2xl shadow-2xl ring-1 ring-white/5">
          <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest mb-2">
            {isDeep ? currentPoint.formattedDate : currentPoint.formattedTime}
          </p>
          <div className="flex items-end gap-3">
            <p className="text-xl font-black text-white leading-none">
              {formatPrice(currentPoint.value, currentPoint.currency || currency, !isIndex)}
            </p>
            {change !== 0 && (
              <p className={`text-[10px] font-black leading-none mb-0.5 ${Number(change) >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>
                {Number(change) >= 0 ? '+' : ''}{change}%
              </p>
            )}
          </div>
          <DataBadge meta={meta} className="mt-3" />
        </div>
      );
    }
    return null;
  };

  return (
    <div style={{ height }} className="w-full relative group">
      <div className="absolute top-0 right-0 z-10 p-4">
         <DataBadge meta={meta} />
      </div>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart
          data={processedData}
          onClick={handleChartClick}
          margin={{ top: 10, right: 10, left: -20, bottom: 0 }}
        >
          <defs>
            <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={color} stopOpacity={0.4}/>
              <stop offset="95%" stopColor={color} stopOpacity={0}/>
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="4 4" stroke="#1e293b" vertical={false} strokeOpacity={0.3} />
          <XAxis 
            dataKey={xAxisKey} 
            axisLine={false}
            tickLine={false}
            tick={{ fill: '#475569', fontSize: 9, fontWeight: 700 }}
            minTickGap={30}
          />
          <YAxis 
            stroke="#475569" 
            fontSize={9} 
            fontWeight={700}
            tickLine={false} 
            axisLine={false}
            tickFormatter={yAxisFormatter}
            domain={[min - padding, max + padding]}
            orientation="right"
          />
          <Tooltip content={<CustomTooltip />} cursor={{ stroke: color, strokeWidth: 2, strokeDasharray: '6 6' }} />
          <Area type="monotone" dataKey="value" stroke={color} strokeWidth={4} fillOpacity={1} fill="url(#chartGradient)" />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

// Memoize the whole component — chart re-renders skip when props are
// structurally equal (same data ref, meta, color, height).
export default React.memo(ChartComponent);
