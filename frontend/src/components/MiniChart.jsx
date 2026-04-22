import React from 'react';
import { LineChart, Line, ResponsiveContainer, YAxis, Tooltip, XAxis } from 'recharts';

const data = {
  '1D': [
    { time: '09:15', value: 22350 },
    { time: '10:00', value: 22380 },
    { time: '11:00', value: 22420 },
    { time: '12:00', value: 22400 },
    { time: '13:00', value: 22460 },
    { time: '14:00', value: 22440 },
    { time: '15:30', value: 22453 },
  ],
  '1W': [
    { time: 'Mon', value: 22100 },
    { time: 'Tue', value: 22250 },
    { time: 'Wed', value: 22180 },
    { time: 'Thu', value: 22350 },
    { time: 'Fri', value: 22453 },
  ],
  '1M': [
    { time: 'Week 1', value: 21800 },
    { time: 'Week 2', value: 22100 },
    { time: 'Week 3', value: 22300 },
    { time: 'Week 4', value: 22453 },
  ]
};

export default function MiniChart({ timeframe = '1D', color = '#10b981' }) {
  const chartData = data[timeframe] || data['1D'];

  return (
    <div className="h-48 w-full mt-4">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={chartData}>
          <XAxis 
            dataKey="time" 
            hide 
          />
          <YAxis 
            hide 
            domain={['dataMin - 50', 'dataMax + 50']} 
          />
          <Tooltip
            contentStyle={{ 
              backgroundColor: '#0f172a', 
              border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: '12px',
              fontSize: '10px',
              fontWeight: 'bold',
              color: '#fff'
            }}
            itemStyle={{ color: '#fff' }}
            labelStyle={{ display: 'none' }}
          />
          <Line
            type="monotone"
            dataKey="value"
            stroke={color}
            strokeWidth={3}
            dot={false}
            animationDuration={1500}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
