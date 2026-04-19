import { motion } from 'framer-motion';
import ChartComponent from './ChartComponent';

export default function StockCard({ stock, index, onHoverStart, onHoverEnd, isHovered }) {
  const isProfit = stock.gain >= 0;
  const color = isProfit ? '#22c55e' : '#ef4444';
  const glowClass = isProfit ? 'text-glow-green' : 'text-glow-red';

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ delay: index * 0.1 }}
      onHoverStart={onHoverStart}
      onHoverEnd={onHoverEnd}
      className="glass rounded-2xl p-6 relative overflow-hidden group cursor-pointer border border-white/5 hover:border-white/20 transition-all"
    >
      {/* Background gradient hint */}
      <div className={`absolute top-0 right-0 w-32 h-32 blur-[50px] rounded-full opacity-20 pointer-events-none transition-all duration-500 ${isProfit ? 'bg-neonGreen' : 'bg-neonRed'} ${isHovered ? 'scale-150 opacity-40' : ''}`} />

      <div className="flex justify-between items-start mb-6">
        <div>
          <h2 className="text-2xl font-black text-white">{stock.symbol}</h2>
          <p className="text-gray-400 text-sm">{stock.name}</p>
        </div>
        <div className="text-right">
          <h3 className="text-xl font-bold text-white">${stock.currentPrice}</h3>
          <p className={`text-sm font-bold ${isProfit ? 'text-neonGreen' : 'text-neonRed'} ${glowClass}`}>
            {isProfit ? '+' : ''}{stock.gain}%
          </p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 mb-6 text-sm">
        <div className="bg-darker/50 p-3 rounded-xl border border-white/5">
          <span className="text-gray-500 block mb-1">Shares</span>
          <span className="text-white font-bold">{Math.floor(stock.quantity)}</span>
        </div>
        <div className="bg-darker/50 p-3 rounded-xl border border-white/5">
          <span className="text-gray-500 block mb-1">Avg Price</span>
          <span className="text-white font-bold">${stock.avgPrice}</span>
        </div>
      </div>

      <div className="h-16 mb-6 opacity-60 group-hover:opacity-100 transition-opacity">
        <ChartComponent data={stock.chart} color={color} height={60} />
      </div>

      <div className="flex gap-3">
        <button className="flex-1 py-2 rounded-xl bg-neonGreen/10 text-neonGreen border border-neonGreen/20 font-bold hover:bg-neonGreen hover:text-darker hover:shadow-[0_0_15px_rgba(34,197,94,0.4)] transition-all">
          BUY
        </button>
        <button className="flex-1 py-2 rounded-xl bg-neonRed/10 text-neonRed border border-neonRed/20 font-bold hover:bg-neonRed hover:text-white hover:shadow-[0_0_15px_rgba(239,68,68,0.4)] transition-all">
          SELL
        </button>
      </div>
    </motion.div>
  );
}
