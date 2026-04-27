import React from 'react';
import { AreaChart, Area, ResponsiveContainer, YAxis, Tooltip, XAxis } from 'recharts';

const data = {
  '1D': [
    { time: '09:15', value: 22350 }, { time: '10:00', value: 22380 }, { time: '11:00', value: 22420 },
    { time: '12:00', value: 22400 }, { time: '13:00', value: 22460 }, { time: '14:00', value: 22440 },
    { time: '15:30', value: 22453 },
  ],
  '1W': [
    { time: 'Mon', value: 22100 }, { time: 'Tue', value: 22250 }, { time: 'Wed', value: 22180 },
    { time: 'Thu', value: 22350 }, { time: 'Fri', value: 22453 },
  ],
  '1M': [
    { time: 'W1', value: 21800 }, { time: 'W2', value: 22100 }, { time: 'W3', value: 22300 },
    { time: 'W4', value: 22453 },
  ]
};

export default function MiniChart({ timeframe = '1D', color = '#10b981' }) {
  const chartData = data[timeframe] || data['1D'];

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
          <XAxis dataKey="time" hide />
          <YAxis hide domain={['dataMin - 100', 'dataMax + 100']} />
          <Tooltip
            contentStyle={{ 
              backgroundColor: '#0f172a', 
              border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: '16px',
              fontSize: '11px',
              fontWeight: '900',
              color: '#fff',
              boxShadow: '0 20px 50px rgba(0,0,0,0.5)'
            }}
            itemStyle={{ color: color }}
            labelStyle={{ display: 'none' }}
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
            activeDot={{ r: 6, stroke: '#0f172a', strokeWidth: 2, fill: color }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
