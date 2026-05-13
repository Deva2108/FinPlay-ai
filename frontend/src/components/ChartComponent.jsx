import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { formatPrice } from '../utils/formatters';
import DataBadge from './DataBadge';
import { useMarket } from '../context/MarketContext';

export default function ChartComponent({ data, meta, color = "#3b82f6", height = 300, onPointClick }) {
  if (!data || data.length === 0) {
    return (
      <div style={{ height }} className="w-full flex items-center justify-center text-slate-500 font-bold uppercase text-[10px] tracking-[0.2em] bg-slate-900/40 rounded-3xl border border-white/5 shadow-inner">
        Synchronizing Market Link...
      </div>
    );
  }

  const currency = meta?.currency || data[0]?.currency || 'INR';

  const processedData = (data || []).map((d, index) => {
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
      formattedTime: date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      formattedDate: date.toLocaleDateString([], { month: 'short', day: 'numeric' }),
      fullDate: date.toLocaleString(),
      index
    };
  });

  const isDeep = processedData.length > 30;
  const xAxisKey = isDeep ? "formattedDate" : "formattedTime";

  const values = processedData.map(d => d.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const padding = (max - min) * 0.15;

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
              {formatPrice(currentPoint.value, currentPoint.currency || currency)}
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
          onClick={(state) => onPointClick && state?.activePayload && onPointClick(state.activePayload[0].payload)}
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
            tickFormatter={(v) => formatPrice(v, currency)}
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
