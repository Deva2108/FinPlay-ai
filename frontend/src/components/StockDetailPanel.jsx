import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, TrendingUp, TrendingDown, Info, Activity, PieChart, DollarSign, Users, Briefcase, CheckCircle2, Zap, Target } from 'lucide-react';
import { formatPrice } from '../utils/formatters';
import { useTrading } from '../context/TradingContext';
import { getChartData, explainStock } from '../services/api';
import ChartComponent from './ChartComponent';
import BuyModal from './GameMode/BuyModal';
import InsightPanel from './InsightPanel';

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
  
  const [insightPanelContent, setInsightPanelContent] = useState(null);
  const [insightScore, setInsightScore] = useState(() => parseInt(localStorage.getItem('insightScore') || '0', 10));
  const [showScorePopup, setShowScorePopup] = useState(false);

  useEffect(() => {
    if (isOpen && stock) {
      setLoadingChart(true);
      getChartData(stock.symbol).then(data => {
        setChartData(data || []);
        setLoadingChart(false);
      }).catch(() => setLoadingChart(false));
    }
  }, [isOpen, stock]);

  const handleConfirmBuy = (quantity) => {
    const success = executeBuy(stock, quantity, "Purchased from detail view");
    if (success) {
      setShowBuyModal(false);
      setIsPurchased(true);
      setTimeout(() => setIsPurchased(false), 3000);
    }
  };

  const handleChartClick = async (point) => {
    setInsightPanelContent({
      title: "Syncing AI Mentor...",
      explanation: `Connecting to market logic for ${stock.symbol}...`,
      type: 'stock',
      data: [{ label: 'Price', value: `$${point.value}` }]
    });

    try {
      const prevPoint = chartData[point.index - 1];
      const trend = prevPoint ? (point.value >= prevPoint.value ? 'UP' : 'DOWN') : 'SIDEWAYS';
      const change = prevPoint ? ((point.value - prevPoint.value) / prevPoint.value * 100).toFixed(2) : 0;

      const aiRes = await explainStock({
        symbol: stock.symbol,
        trend,
        action: 'observing',
        type: 'graph_point',
        metrics: { price: point.value }
      });

      if (aiRes) {
        setInsightPanelContent({
          title: "Technical Insight",
          explanation: aiRes.explanation,
          insight: aiRes.observation,
          type: 'stock',
          data: [
            { label: 'Time', value: point.formattedTime },
            { label: 'Price', value: `$${point.value}`, color: 'text-white' },
            { label: 'Momentum', value: `${change}%`, color: Number(change) >= 0 ? 'text-emerald-500' : 'text-rose-500' }
          ],
          actions: [{ label: 'Got it!', primary: true }]
        });

        const pointsEarned = Number(change) < -1 ? 10 : 5;
        const newScore = insightScore + pointsEarned;
        setInsightScore(newScore);
        localStorage.setItem('insightScore', newScore.toString());
        setShowScorePopup(pointsEarned);
        setTimeout(() => setShowScorePopup(false), 2000);
      }
    } catch (err) {}
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

          {/* Score Popup */}
          <AnimatePresence>
            {showScorePopup && (
              <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: -40 }} exit={{ opacity: 0 }} className="fixed bottom-24 right-1/2 translate-x-1/2 z-[200]">
                <div className="bg-blue-600 text-white px-4 py-2 rounded-xl font-black text-[10px] uppercase tracking-widest shadow-2xl flex items-center gap-2">
                  <Zap size={14} className="fill-white" /> +{showScorePopup} Score
                </div>
              </motion.div>
            )}
          </AnimatePresence>

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
                      <h2 className="text-4xl font-black text-white tracking-tighter leading-none">{stock.symbol}</h2>
                      <p className="text-slate-400 font-bold uppercase tracking-widest text-[10px] mt-2">{stock.name}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    <div className="flex flex-col items-end">
                      <span className="text-[9px] font-black text-blue-500 uppercase tracking-widest mb-1">Total Insights</span>
                      <div className="flex items-center gap-2 bg-blue-500/10 px-3 py-1 rounded-lg border border-blue-500/20">
                        <Target size={12} className="text-blue-500" />
                        <span className="text-xs font-black text-white">{insightScore}</span>
                      </div>
                    </div>
                    <button onClick={onClose} className="p-2 bg-white/5 hover:bg-white/10 rounded-full transition-colors"><X size={24} className="text-slate-400" /></button>
                  </div>
                </motion.div>

                {/* 2. Price & Quick Info */}
                <motion.div custom={1} variants={contentVariants} initial="hidden" animate="visible" className="bg-slate-900/40 rounded-3xl p-8 border border-white/10 relative overflow-hidden group">
                  <div className="absolute top-0 right-0 w-32 h-32 bg-blue-500/5 blur-3xl rounded-full" />
                  <div className="flex justify-between items-end relative z-10">
                    <div>
                      <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Current Price</p>
                      <p className="text-5xl font-black text-white tracking-tighter">{formatPrice(stock.price, stock.market)}</p>
                    </div>
                    <div className="flex flex-col items-end gap-1">
                      <div className={`flex items-center gap-1 px-3 py-1.5 rounded-xl font-black text-sm ${(stock.change || "").startsWith('+') ? 'bg-emerald-500/10 text-emerald-500' : 'bg-rose-500/10 text-rose-500'}`}>
                        {(stock.change || "").startsWith('+') ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
                        <span>{stock.change}</span>
                      </div>
                      <p className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Today's Move</p>
                    </div>
                  </div>

                  <div className="mt-8 pt-6 border-t border-white/5">
                    <p className="text-slate-300 font-medium leading-relaxed italic text-sm">
                      "This stock is moving because {stock.explanation?.toLowerCase() || "of current market trends and investor sentiment."}"
                    </p>
                  </div>
                </motion.div>

                {/* 3. Interactive Chart */}
                <motion.div custom={2} variants={contentVariants} initial="hidden" animate="visible" className="space-y-4">
                   <div className="flex items-center justify-between px-2">
                      <SectionHeader icon={Activity} title="Interactive Performance" />
                      <span className="text-[9px] font-black text-blue-400 uppercase tracking-widest animate-pulse">Click point for AI insight</span>
                   </div>
                   <div className="bg-slate-900/60 rounded-3xl border border-white/5 p-6 h-[300px]">
                      {loadingChart ? (
                        <div className="h-full flex items-center justify-center gap-3">
                           <Zap size={16} className="text-blue-500 animate-spin" />
                           <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Syncing Chart...</span>
                        </div>
                      ) : (
                        <ChartComponent 
                          data={chartData} 
                          color={(stock.change || "").startsWith('+') ? '#10b981' : '#f43f5e'} 
                          height={250} 
                          onPointClick={handleChartClick}
                        />
                      )}
                   </div>
                </motion.div>

                {/* 4. Basic Metrics */}
                <motion.div custom={3} variants={contentVariants} initial="hidden" animate="visible" className="space-y-6">
                  <SectionHeader icon={PieChart} title="Financial Health" />
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <MetricCard label="Market Cap" value={stock.market === 'US' ? '$2.8T' : '₹18.5L Cr'} explanation="Total value of the company." />
                    <MetricCard label="P/E Ratio" value="28.4" explanation="Investor expectations score." />
                    <MetricCard label="Revenue" value={stock.market === 'US' ? '$383B' : '₹9.2L Cr'} explanation="Total annual sales." />
                    <MetricCard label="Dividend" value="0.52%" explanation="Cash back for shareholders." />
                  </div>
                </motion.div>

                {/* 5. Company Info */}
                <motion.div custom={4} variants={contentVariants} initial="hidden" animate="visible" className="space-y-6">
                  <SectionHeader icon={Briefcase} title="About the Company" />
                  <div className="bg-white/5 border border-white/5 rounded-3xl p-6 space-y-6">
                    <p className="text-slate-300 leading-relaxed font-medium text-sm">
                      {stock.symbol === 'AAPL' ? "Apple designs, manufactures, and markets smartphones, personal computers, tablets, wearables, and accessories worldwide." : 
                      stock.symbol === 'TSLA' ? "Tesla designs, develops, manufactures, and sells electric vehicles and energy generation systems." :
                      stock.symbol === 'RELIANCE' ? "Reliance Industries is an Indian multinational conglomerate company, headquartered in Mumbai. It has businesses in energy, petrochemicals, retail, and telecommunications." :
                      "A leader in its industry, focusing on innovation and global scale to provide value to its customers and shareholders."}
                    </p>
                  </div>
                </motion.div>

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
