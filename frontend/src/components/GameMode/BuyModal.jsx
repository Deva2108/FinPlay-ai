import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Plus, Minus, Wallet, ShoppingCart, ShieldCheck, TrendingUp, Zap } from 'lucide-react';
import { formatPrice } from '../../utils/formatters';

export default function BuyModal({ stock, balance, isOpen, onClose, onConfirm }) {
  const [qty, setQty] = useState(1);
  const [orderType, setOrderType] = useState('MARKET'); // 'MARKET' or 'LIMIT'
  const [limitPrice, setLimitPrice] = useState(0);

  useEffect(() => {
    if (stock) {
      const price = typeof stock.price === 'string' ? parseFloat(stock.price.replace(/,/g, '')) : stock.price;
      setLimitPrice(price);
    }
  }, [stock]);

  if (!stock) return null;

  const currentMarketPrice = typeof stock.price === 'string' ? parseFloat(stock.price.replace(/,/g, '')) : stock.price;
  const executionPrice = orderType === 'LIMIT' ? limitPrice : currentMarketPrice;
  const totalRequired = executionPrice * qty;
  const remainingBalance = balance - totalRequired;
  const canAfford = balance >= totalRequired && totalRequired > 0;

  const handleQtyChange = (val) => {
    const newQty = Math.max(1, Math.floor(val));
    setQty(newQty);
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-[200] flex items-end sm:items-center justify-center p-0 sm:p-4">
          <motion.div 
            initial={{ opacity: 0 }} 
            animate={{ opacity: 1 }} 
            exit={{ opacity: 0 }} 
            onClick={onClose} 
            className="absolute inset-0 bg-black/80 backdrop-blur-md" 
          />
          
          <motion.div 
            initial={{ y: "100%", opacity: 0 }} 
            animate={{ y: 0, opacity: 1 }} 
            exit={{ y: "100%", opacity: 0 }} 
            transition={{ type: "spring", damping: 25, stiffness: 200 }}
            className="relative w-full max-w-lg bg-[#020617] border-t sm:border border-white/10 rounded-t-[3rem] sm:rounded-[3.5rem] p-8 sm:p-10 shadow-[0_-20px_100px_rgba(0,0,0,0.7)] space-y-8 pb-12 sm:pb-10"
          >
            {/* Header */}
            <div className="flex justify-between items-start">
              <div className="space-y-1">
                <div className="flex items-center gap-2 mb-1">
                  <span className="px-2 py-0.5 bg-blue-600/20 text-blue-500 rounded text-[10px] font-black uppercase tracking-widest border border-blue-500/20">Buy Order</span>
                  <div className="w-1 h-1 rounded-full bg-slate-700" />
                  <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">{stock.symbol}</span>
                </div>
                <h3 className="text-2xl sm:text-3xl font-black text-white leading-tight tracking-tighter">{stock.name}</h3>
              </div>
              <button onClick={onClose} className="p-2.5 bg-white/5 hover:bg-white/10 rounded-2xl transition-all text-slate-400 hover:text-white">
                <X size={20}/>
              </button>
            </div>

            {/* Content Split: Type & Qty */}
            <div className="grid grid-cols-1 gap-6">
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                   <p className="text-[10px] font-black text-slate-500 uppercase tracking-[0.2em]">Order Configuration</p>
                   <div className="flex bg-slate-900 rounded-xl p-1 border border-white/5">
                      {['MARKET', 'LIMIT'].map(t => (
                        <button 
                          key={t}
                          onClick={() => setOrderType(t)}
                          className={`px-4 py-1.5 rounded-lg text-[10px] font-black transition-all ${orderType === t ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'}`}
                        >
                          {t}
                        </button>
                      ))}
                   </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="bg-slate-900/40 rounded-3xl p-5 border border-white/5 space-y-3">
                    <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Quantity</p>
                    <div className="flex items-center justify-between gap-4">
                      <button onClick={() => handleQtyChange(qty - 1)} className="w-8 h-8 flex items-center justify-center bg-white/5 hover:bg-white/10 rounded-lg text-white transition-all"><Minus size={16}/></button>
                      <input 
                        type="number" 
                        value={qty} 
                        onChange={(e) => handleQtyChange(e.target.value)}
                        className="bg-transparent text-xl font-black text-white w-full text-center focus:outline-none tabular-nums"
                      />
                      <button onClick={() => handleQtyChange(qty + 1)} className="w-8 h-8 flex items-center justify-center bg-white/5 hover:bg-white/10 rounded-lg text-white transition-all"><Plus size={16}/></button>
                    </div>
                  </div>

                  <div className="bg-slate-900/40 rounded-3xl p-5 border border-white/5 space-y-3">
                    <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">{orderType === 'LIMIT' ? 'Limit Price' : 'Market Price'}</p>
                    {orderType === 'LIMIT' ? (
                      <input 
                        type="number" 
                        value={limitPrice} 
                        onChange={(e) => setLimitPrice(parseFloat(e.target.value) || 0)}
                        className="bg-transparent text-xl font-black text-blue-500 w-full text-center focus:outline-none tabular-nums"
                      />
                    ) : (
                      <div className="text-xl font-black text-white w-full text-center tabular-nums">
                        {formatPrice(currentMarketPrice, stock.market)}
                      </div>
                    )}
                  </div>
                </div>
              </div>

              {/* Calculations */}
              <div className="bg-slate-900/40 rounded-[2rem] p-6 border border-white/5 space-y-5">
                <div className="flex justify-between items-center text-[10px] font-black uppercase tracking-widest">
                  <span className="text-slate-500">Available Balance</span>
                  <span className="text-white">{formatPrice(balance, stock.market)}</span>
                </div>
                <div className="h-px bg-white/5" />
                <div className="flex justify-between items-center">
                  <div className="space-y-1">
                    <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Est. Order Value</p>
                    <p className={`text-xl font-black ${canAfford ? 'text-white' : 'text-rose-500'}`}>{formatPrice(totalRequired, stock.market)}</p>
                  </div>
                  <div className="text-right space-y-1">
                    <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Post-Trade Balance</p>
                    <p className="text-xs font-black text-slate-300">{formatPrice(remainingBalance, stock.market)}</p>
                  </div>
                </div>
              </div>
            </div>

            {/* Actions */}
            <div className="space-y-4">
              <button
                disabled={!canAfford}
                onClick={() => onConfirm(qty)}
                className="w-full py-5 bg-blue-600 hover:bg-blue-500 disabled:bg-slate-800 disabled:text-slate-500 disabled:cursor-not-allowed text-white rounded-[2rem] font-black uppercase tracking-[0.2em] text-sm transition-all active:scale-95 shadow-2xl shadow-blue-600/20 flex items-center justify-center gap-3 group"
              >
                <ShoppingCart size={20} className="group-hover:scale-110 transition-transform" />
                BUY {qty} {qty === 1 ? 'SHARE' : 'SHARES'}
              </button>
              
              {!canAfford && totalRequired > 0 && (
                <p className="text-center text-rose-500 text-[10px] font-black uppercase tracking-widest animate-pulse">Insufficient Funds in Wallet</p>
              )}
              
              <div className="flex items-center justify-center gap-2 text-slate-500 pt-2">
                <ShieldCheck size={14} />
                <span className="text-[10px] font-bold uppercase tracking-widest">Instant execution via simulation engine</span>
              </div>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}

