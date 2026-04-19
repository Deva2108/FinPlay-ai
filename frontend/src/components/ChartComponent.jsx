import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

export default function ChartComponent({ data, color = "#3b82f6" }) {
  if (!data || data.length === 0) {
    return (
      <div className="h-64 w-full flex items-center justify-center text-slate-500 font-bold uppercase text-xs tracking-widest bg-slate-900/50 rounded-xl border border-slate-800">
        Live data unavailable
      </div>
    );
  }

  // Contract is strict: { time, value }
  // Supports both 'time' and 'timestamp' for backend sync resilience
  const processedData = data.map(d => {
    const rawTime = d.time || d.timestamp;
    return {
      ...d,
      formattedTime: rawTime ? new Date(rawTime * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'N/A'
    };
  });

  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={processedData}>
          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} />
          <XAxis 
            dataKey="formattedTime" 
            stroke="#64748b" 
            fontSize={12} 
            tickLine={false} 
            axisLine={false}
          />
          <YAxis 
            stroke="#64748b" 
            fontSize={12} 
            tickLine={false} 
            axisLine={false}
            tickFormatter={(value) => `$${value}`}
            domain={['auto', 'auto']}
          />
          <Tooltip 
            contentStyle={{ backgroundColor: '#0f172a', borderColor: '#1e293b', borderRadius: '8px' }}
            itemStyle={{ color: '#f8fafc' }}
            labelStyle={{ color: '#64748b', marginBottom: '4px' }}
          />
          <Line 
            type="monotone" 
            dataKey="value" 
            stroke={color} 
            strokeWidth={2} 
            dot={false}
            activeDot={{ r: 6, strokeWidth: 0 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
