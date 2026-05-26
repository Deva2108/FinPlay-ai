import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, TrendingUp, TrendingDown, Info, Activity, PieChart, DollarSign, Users, Briefcase, CheckCircle2, Zap, Target } from 'lucide-react';
import { formatPrice } from '../utils/formatters';
import { useTrading } from '../context/TradingContext';
import { getChartData, explainStock } from '../services/api';
import ChartComponent from './ChartComponent';
import BuyModal from './GameMode/BuyModal';
import InsightPanel from './InsightPanel';
import DataBadge from './DataBadge';
import InfoTooltip from './InfoTooltip';

const contentVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: (i) => ({
    opacity: 1,
    y: 0,
    transition: {
      delay: 0.1 + (i * 0.05),
      type: "spring",
      stiffness: 300,
      damping: 25
    }
  })
};

const SectionHeader = ({ icon: Icon, title, color = "text-blue-500" }) => (
  <div className="flex items-center gap-2 mb-4">
    <div className={`p-1.5 rounded-lg ${color.replace('text', 'bg')}/10`}>
      <Icon size={16} className={color} />
    </div>
    <h3 className="text-sm font-black text-white uppercase tracking-widest">{title}</h3>
  </div>
);

export default function StockDetailPanel({ stock, isOpen, onClose }) {
  const { balance, executeBuy } = useTrading();
  const [isPurchased, setIsPurchased] = useState(false);
  const [showBuyModal, setShowBuyModal] = useState(false);
  const [chartData, setChartData] = useState([]);
  const [loadingChart, setLoadingChart] = useState(false);
  const [timeframe, setTimeframe] = useState('1M');
  
  const [insightPanelContent, setInsightPanelContent] = useState(null);

  const isIndex = stock?.symbol?.startsWith('^') || stock?.symbol === 'SPY' || stock?.symbol === 'QQQ' || stock?.symbol === 'DIA';

  useEffect(() => {
    if (isOpen && stock) {
      setLoadingChart(true);
      getChartData(stock.symbol, timeframe).then(res => {
        // res.data is the map {symbol, currency, data: []}
        setChartData(res?.data?.data || []);
        setLoadingChart(false);
      }).catch(() => setLoadingChart(false));
    }
  }, [isOpen, stock, timeframe]);

  const handleConfirmBuy = async (quantity) => {
    const result = await executeBuy(stock, quantity, "Purchased from detail view");
    if (result.success) {
      setShowBuyModal(false);
      setIsPurchased(true);
      setTimeout(() => setIsPurchased(false), 3000);
    }
  };

  const handleChartClick = async (point) => {
    const prevPoint = chartData[point.index - 1];
    const trend = prevPoint ? (point.value >= prevPoint.value ? 'UP' : 'DOWN') : 'SIDEWAYS';
    const change = prevPoint ? ((point.value - prevPoint.value) / prevPoint.value * 100).toFixed(2) : 0;

    // Open immediately with deterministic math so there's no "syncing" theater.
    // The AI call (if available) enriches this in-place when it returns.
    const baseData = [
      { label: 'Time', value: point.formattedTime },
      { label: isIndex ? 'Points' : 'Price', value: formatPrice(point.value, stock.currency, !isIndex), color: 'text-white' },
      { label: 'Move', value: `${Number(change) >= 0 ? '+' : ''}${change}%`, color: Number(change) >= 0 ? 'text-emerald-500' : 'text-rose-500' }
    ];

    setInsightPanelContent({
      title: `${stock.symbol} · point detail`,
      explanation: prevPoint
        ? `${Number(change) >= 0 ? 'Up' : 'Down'} ${Math.abs(Number(change))}% from the prior point on the ${timeframe} chart.`
        : `First point on the ${timeframe} chart — no prior reference.`,
      type: 'stock',
      data: baseData
    });

    try {
      const res = await explainStock({
        symbol: stock.symbol,
        trend,
        action: 'observing',
        type: 'graph_point',
        metrics: { price: point.value }
      });
      const aiRes = res?.data;
      if (aiRes && (aiRes.richInsight || aiRes.observation || aiRes.explanation)) {
        setInsightPanelContent({
          title: `${stock.symbol} · point detail`,
          explanation: aiRes.explanation || `${Number(change) >= 0 ? 'Up' : 'Down'} ${Math.abs(Number(change))}% from the prior point.`,
          insight: aiRes.richInsight || aiRes.observation,
          type: 'stock',
          data: baseData,
          actions: [{ label: 'Close', primary: true }]
        });
      }
    } catch (err) {
      // Math fallback is already on screen — no need to surface the failure as theater.
    }
  };

  if (!stock) return null;

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <InsightPanel isOpen={!!insightPanelContent} onClose={() => setInsightPanelContent(null)} content={insightPanelContent} />
          
          <BuyModal 
            stock={stock} 
            balance={balance} 
            isOpen={showBuyModal} 
            onClose={() => setShowBuyModal(false)} 
            onConfirm={handleConfirmBuy}
          />

          <div key="panel-container" className="fixed inset-0 z-[100] pointer-events-none">
            {/* Backdrop */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={onClose}
              className="absolute inset-0 bg-black/80 backdrop-blur-md cursor-pointer pointer-events-auto"
            />

            {/* Side Panel */}
            <motion.div
              initial={{ x: '100%' }}
              animate={{ x: 0 }}
              exit={{ x: '100%' }}
              transition={{ type: 'spring', damping: 28, stiffness: 220 }}
              className="absolute right-0 top-0 h-full w-full max-w-xl bg-[#020617] border-l border-white/10 overflow-y-auto shadow-[-20px_0_50px_rgba(0,0,0,0.5)] pointer-events-auto no-scrollbar"
            >
              <div className="p-8 space-y-10 pb-24">
                {/* 1. Header Section */}
                <motion.div custom={0} variants={contentVariants} initial="hidden" animate="visible" className="flex justify-between items-start">
                  <div className="flex items-center gap-4">
                    <div>
                      <h2 className="text-4xl font-black text-white tracking-tighter leading-none flex items-center gap-3">
                        {stock.symbol}
                        <DataBadge meta={stock.meta} />
                      </h2>
                      <p className="text-slate-400 font-bold uppercase tracking-widest text-[10px] mt-2">{stock.name}</p>
                    </div>
                  </div>
                  <button onClick={onClose} className="p-2 bg-white/5 hover:bg-white/10 rounded-full transition-colors"><X size={24} className="text-slate-400" /></button>
                </motion.div>

                {/* 2. Price & Quick Info */}
                <motion.div custom={1} variants={contentVariants} initial="hidden" animate="visible" className="bg-slate-900/40 rounded-3xl p-8 border border-white/10 relative overflow-hidden group">
                  <div className="absolute top-0 right-0 w-32 h-32 bg-blue-500/5 blur-3xl rounded-full" />
                  <div className="flex justify-between items-end relative z-10">
                    <div>
                      <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">{isIndex ? 'Current Points' : 'Current Price'}</p>
                      <p className="text-5xl font-black text-white tracking-tighter">{formatPrice(stock.price, stock.currency || 'INR', !isIndex)}</p>
                      <div className="flex items-center gap-2 mt-2">
                        <InfoTooltip concept="volatility">
                          <p className="text-[9px] font-bold text-blue-400/80 uppercase tracking-widest">Price movements reflect market sentiment</p>
                        </InfoTooltip>
                      </div>
                    </div>
                    <div className="flex flex-col items-end gap-1">
                      <div className={`flex items-center gap-1 px-3 py-1.5 rounded-xl font-black text-sm ${(stock.change || "").startsWith('+') ? 'bg-emerald-500/10 text-emerald-500' : 'bg-rose-500/10 text-rose-500'}`}>
                        {(stock.change || "").startsWith('+') ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
                        <span>{stock.change}</span>
                      </div>
                      <p className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Today's Move</p>
                    </div>
                  </div>

                  {stock.explanation && (
                    <div className="mt-8 pt-6 border-t border-white/5">
                      <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-2">Backend note</p>
                      <p className="text-slate-300 font-medium leading-relaxed text-sm">{stock.explanation}</p>
                    </div>
                  )}
                </motion.div>

                {/* 3. Interactive Chart */}
                <motion.div custom={2} variants={contentVariants} initial="hidden" animate="visible" className="space-y-4">
                   <div className="flex items-center justify-between px-2">
                      <SectionHeader icon={Activity} title="Interactive Performance" />
                      <div className="flex bg-slate-900/80 rounded-xl p-1 border border-white/5 gap-1">
                        {['1D', '1W', '1M', '1Y', '5Y'].map((tf) => (
                          <button
                            key={tf}
                            onClick={() => setTimeframe(tf)}
                            className={`px-3 py-1 rounded-lg text-[10px] font-black transition-all ${
                              timeframe === tf 
                              ? 'bg-blue-600 text-white shadow-lg' 
                              : 'text-slate-500 hover:text-white hover:bg-white/5'
                            }`}
                          >
                            {tf}
                          </button>
                        ))}
                      </div>
                   </div>
                   <div className="bg-slate-900/60 rounded-3xl border border-white/5 p-6 h-[300px]">
                      {loadingChart ? (
                        <div className="h-full flex flex-col items-center justify-center gap-3">
                           <div className="w-8 h-8 border-2 border-white/10 border-t-blue-500 rounded-full animate-spin" />
                           <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Loading {timeframe} history</span>
                        </div>
                      ) : (
                        <ChartComponent
                          data={chartData}
                          color={(stock.change || "").startsWith('+') ? '#10b981' : '#f43f5e'}
                          height={250}
                          onPointClick={handleChartClick}
                          isIndex={isIndex}
                        />
                      )}
                   </div>
                   <p className="text-[9px] font-bold text-slate-500 uppercase tracking-widest text-center">Click any point for detail</p>
                </motion.div>

                {/* 4. Fundamentals — only what's actually populated on the stock object */}
                {(stock.marketCap || stock.peRatio || stock.revenue || stock.dividendYield || stock.high52 || stock.low52) && (
                  <motion.div custom={3} variants={contentVariants} initial="hidden" animate="visible" className="space-y-6">
                    <SectionHeader icon={PieChart} title="Fundamentals" />
                    <div className="grid grid-cols-2 gap-px bg-white/5 border border-white/5 rounded-2xl overflow-hidden">
                      {stock.marketCap && (
                        <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
                          <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Market Cap</span>
                          <span className="text-sm font-black text-white">{stock.marketCap}</span>
                        </div>
                      )}
                      {stock.peRatio && (
                        <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
                          <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">P/E</span>
                          <span className="text-sm font-black text-white tabular-nums">{stock.peRatio}</span>
                        </div>
                      )}
                      {stock.high52 && (
                        <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
                          <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">52W High</span>
                          <span className="text-sm font-black text-emerald-400 tabular-nums">{formatPrice(stock.high52, stock.currency, !isIndex)}</span>
                        </div>
                      )}
                      {stock.low52 && (
                        <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
                          <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">52W Low</span>
                          <span className="text-sm font-black text-rose-400 tabular-nums">{formatPrice(stock.low52, stock.currency, !isIndex)}</span>
                        </div>
                      )}
                      {stock.revenue && (
                        <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
                          <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Revenue</span>
                          <span className="text-sm font-black text-white">{stock.revenue}</span>
                        </div>
                      )}
                      {stock.dividendYield && (
                        <div className="bg-slate-900/60 px-4 py-3 flex flex-col gap-0.5">
                          <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Dividend</span>
                          <span className="text-sm font-black text-white">{stock.dividendYield}</span>
                        </div>
                      )}
                    </div>
                  </motion.div>
                )}

                {/* Footer Actions */}
                <motion.div custom={5} variants={contentVariants} initial="hidden" animate="visible" className="fixed bottom-0 left-0 right-0 p-8 bg-[#020617]/90 backdrop-blur-xl border-t border-white/5 flex gap-4 z-20 pointer-events-auto">
                  <button 
                    onClick={() => setShowBuyModal(true)}
                    disabled={isPurchased}
                    className={`flex-1 py-4 ${isPurchased ? 'bg-emerald-500' : 'bg-blue-600 hover:bg-blue-500'} text-white rounded-2xl font-black uppercase tracking-widest text-xs transition-all active:scale-95 shadow-xl flex items-center justify-center gap-2`}
                  >
                    {isPurchased ? <><CheckCircle2 size={18} /> Order Executed</> : "Execute Paper Trade"}
                  </button>
                </motion.div>
              </div>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}

function MetricCard({ label, value, explanation }) {
  return (
    <div className="bg-slate-900/40 border border-white/5 rounded-2xl p-5 group hover:border-blue-500/30 transition-all">
      <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">{label}</p>
      <p className="text-xl font-black text-white mb-2">{value}</p>
      <div className="flex gap-2">
        <div className="min-w-[2px] h-auto bg-blue-500/20 rounded-full" />
        <p className="text-[10px] text-slate-400 font-medium leading-tight">
          {explanation}
        </p>
      </div>
    </div>
  );
}
