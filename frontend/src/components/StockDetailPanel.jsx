import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, TrendingUp, TrendingDown, Info, Activity, PieChart, DollarSign, Users, Briefcase, CheckCircle2 } from 'lucide-react';
import { formatPrice } from '../utils/formatters';
import { useTrading } from '../context/TradingContext';
import BuyModal from './GameMode/BuyModal';

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

  const handleConfirmBuy = (quantity) => {
    const success = executeBuy(stock, quantity, "Purchased from detail view");
    if (success) {
      setShowBuyModal(false);
      setIsPurchased(true);
      setTimeout(() => setIsPurchased(false), 3000);
    }
  };

  if (!stock) return null;

  return (
    <AnimatePresence>
      {isOpen && (
        <>
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
              className="absolute right-0 top-0 h-full w-full max-w-xl bg-[#020617] border-l border-white/10 overflow-y-auto shadow-[-20px_0_50px_rgba(0,0,0,0.5)] pointer-events-auto"
            >
              <div className="p-8 space-y-10 pb-24">
                {/* 1. Header Section */}
                <motion.div custom={0} variants={contentVariants} initial="hidden" animate="visible" className="flex justify-between items-start">
                  <div>
                    <h2 className="text-4xl font-black text-white tracking-tighter leading-none">{stock.symbol}</h2>
                    <p className="text-slate-400 font-bold uppercase tracking-widest text-xs mt-2">{stock.name}</p>
                  </div>
                  <button 
                    onClick={onClose}
                    className="p-2 bg-white/5 hover:bg-white/10 rounded-full transition-colors"
                  >
                    <X size={24} className="text-slate-400" />
                  </button>
                </motion.div>

                {/* 2. Price & Quick Info */}
                <motion.div custom={1} variants={contentVariants} initial="hidden" animate="visible" className="bg-slate-900/40 rounded-3xl p-8 border border-white/10 relative overflow-hidden">
                  <div className="absolute top-0 right-0 w-32 h-32 bg-blue-500/5 blur-3xl rounded-full" />
                  <div className="flex justify-between items-end relative z-10">
                    <div>
                      <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Current Price</p>
                      <p className="text-5xl font-black text-white tracking-tighter">{formatPrice(stock.price, stock.market)}</p>
                    </div>
                    <div className={`flex flex-col items-end gap-1`}>
                      <div className={`flex items-center gap-1 px-3 py-1.5 rounded-xl font-black text-sm ${stock.change.startsWith('+') ? 'bg-emerald-500/10 text-emerald-500 shadow-[0_0_20px_rgba(16,185,129,0.1)]' : 'bg-rose-500/10 text-rose-500 shadow-[0_0_20px_rgba(244,63,94,0.1)]'}`}>
                        {stock.change.startsWith('+') ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
                        {stock.change}
                      </div>
                      <p className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Today's Move</p>
                    </div>
                  </div>

                  {/* Quick Explanation */}
                  <div className="mt-8 pt-6 border-t border-white/5">
                    <p className="text-slate-300 font-medium leading-relaxed italic">
                      "This stock is moving because {stock.explanation?.toLowerCase() || "of current market trends and investor sentiment."}"
                    </p>
                  </div>
                </motion.div>

                {/* 3. Simple Mock Chart Placeholder */}
                <motion.div custom={2} variants={contentVariants} initial="hidden" animate="visible" className="h-32 bg-white/5 rounded-3xl border border-white/5 flex items-center justify-center relative overflow-hidden">
                  <div className="absolute inset-0 flex items-end opacity-20">
                    {[40, 70, 45, 90, 65, 80, 50, 85, 100].map((h, i) => (
                      <div key={i} className="flex-1 bg-blue-500" style={{ height: `${h}%` }} />
                    ))}
                  </div>
                  <span className="text-[10px] font-black text-slate-500 uppercase tracking-[0.2em] relative z-10">Live Performance Graph</span>
                </motion.div>

                {/* 4. Basic Metrics (Beginner Friendly) */}
                <motion.div custom={3} variants={contentVariants} initial="hidden" animate="visible" className="space-y-6">
                  <SectionHeader icon={Activity} title="Financial Health" />
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <MetricCard 
                      label="Market Cap" 
                      value={stock.market === 'US' ? '$2.8 Trillion' : '₹18.5 Lakh Cr'} 
                      explanation="Total value of the company. It tells you how 'big' the company is in the market."
                    />
                    <MetricCard 
                      label="P/E Ratio" 
                      value="28.4" 
                      explanation="How much you pay for $1 of profit. Higher means investors expect future growth."
                    />
                    <MetricCard 
                      label="Revenue" 
                      value={stock.market === 'US' ? '$383 Billion' : '₹9.2 Lakh Cr'} 
                      explanation="Total money the company made from sales before any costs were taken out."
                    />
                    <MetricCard 
                      label="Dividend" 
                      value="0.52%" 
                      explanation="Small cash payments the company gives back to you just for owning the stock."
                    />
                  </div>
                </motion.div>

                {/* 5. Company Info */}
                <motion.div custom={4} variants={contentVariants} initial="hidden" animate="visible" className="space-y-6">
                  <SectionHeader icon={Briefcase} title="About the Company" />
                  <div className="bg-white/5 border border-white/5 rounded-3xl p-6 space-y-6">
                    <p className="text-slate-300 leading-relaxed font-medium">
                      {stock.symbol === 'AAPL' ? "Apple designs, manufactures, and markets smartphones, personal computers, tablets, wearables, and accessories worldwide." : 
                      stock.symbol === 'TSLA' ? "Tesla designs, develops, manufactures, and sells electric vehicles and energy generation systems." :
                      stock.symbol === 'RELIANCE' ? "Reliance Industries is an Indian multinational conglomerate company, headquartered in Mumbai. It has businesses in energy, petrochemicals, retail, and telecommunications." :
                      "A leader in its industry, focusing on innovation and global scale to provide value to its customers and shareholders."}
                    </p>
                    
                    <div className="grid grid-cols-2 gap-6 pt-6 border-t border-white/5">
                      <div>
                        <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Sector</p>
                        <p className="text-white font-bold">{stock.market === 'US' ? 'Technology' : 'Conglomerate'}</p>
                      </div>
                      <div>
                        <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Leader</p>
                        <p className="text-white font-bold">{stock.symbol === 'TSLA' ? 'Elon Musk' : stock.symbol === 'RELIANCE' ? 'Mukesh Ambani' : 'Industry Veteran'}</p>
                      </div>
                    </div>
                  </div>
                </motion.div>

                {/* Footer Actions */}
                <motion.div custom={5} variants={contentVariants} initial="hidden" animate="visible" className="fixed bottom-0 left-0 right-0 p-8 bg-[#020617]/80 backdrop-blur-xl border-t border-white/5 flex gap-4 z-20 pointer-events-auto">
                  <button 
                    onClick={() => setShowBuyModal(true)}
                    disabled={isPurchased}
                    className={`flex-1 py-4 ${isPurchased ? 'bg-emerald-500' : 'bg-blue-600 hover:bg-blue-500'} text-white rounded-2xl font-black uppercase tracking-widest text-xs transition-all active:scale-95 shadow-lg ${isPurchased ? 'shadow-emerald-500/20' : 'shadow-blue-500/20'} flex items-center justify-center gap-2`}
                  >
                    {isPurchased ? (
                      <>
                        <CheckCircle2 size={18} />
                        Order Executed
                      </>
                    ) : (
                      "Paper Trade this Stock"
                    )}
                  </button>
                  <button className="px-6 py-4 bg-white/5 hover:bg-white/10 text-white rounded-2xl font-black transition-all active:scale-95">
                    <Info size={18} />
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
