import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useParams, useNavigate } from 'react-router-dom';
import { getStockDetails, getChartData, executeTrade, getLiveQuotes, getNews } from '../services/api';
import ChartComponent from '../components/ChartComponent';
import { ArrowLeft, TrendingUp, TrendingDown, Activity, Briefcase, Info, Newspaper, Lightbulb, CheckCircle2, AlertTriangle } from 'lucide-react';
import { formatPrice } from '../utils/formatters';

export default function StockDetails() {
  const { symbol } = useParams();
  const navigate = useNavigate();
  const [stock, setStock] = useState(null);
  const [quote, setQuote] = useState(null);
  const [chart, setChart] = useState([]);
  const [news, setNews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('OVERVIEW');
  
  const [tradeType, setTradeType] = useState('BUY');
  const [quantity, setQuantity] = useState(1);
  const [tradeLoading, setTradeLoading] = useState(false);
  const [message, setMessage] = useState(null);

  const fetchData = async (isBackground = false) => {
    if (!isBackground) setLoading(true);
    try {
      const results = await Promise.allSettled([
        getStockDetails(symbol),
        getChartData(symbol),
        getLiveQuotes([symbol]),
        getNews(symbol)
      ]);
      
      if (results[0].status === 'fulfilled') {
        const stockData = results[0].value;
        setStock(stockData);
        
        // Save to Recently Viewed
        const recentlyViewed = JSON.parse(localStorage.getItem('recentlyViewed') || '[]');
        const newItem = {
          symbol: stockData.symbol,
          name: stockData.profile?.companyName || stockData.symbol,
          price: results[2].status === 'fulfilled' && results[2].value?.[0]?.price ? results[2].value[0].price : 0,
          market: 'US' // Default for this page's symbols
        };
        
        const filtered = recentlyViewed.filter(item => item.symbol !== symbol);
        const updated = [newItem, ...filtered].slice(0, 5);
        localStorage.setItem('recentlyViewed', JSON.stringify(updated));
      }

      if (results[1].status === 'fulfilled') {
        setChart(results[1].value || []);
      } else {
        setChart([]);
      }

      if (results[2].status === 'fulfilled') {
        const liveQuote = results[2].value;
        if (liveQuote && liveQuote.length > 0) {
          setQuote(liveQuote[0]);
        }
      }

      if (results[3].status === 'fulfilled') {
        setNews(results[3].value || []);
      } else {
        setNews([]);
      }
    } catch (err) {
      // Background fail is silent
    }
    if (!isBackground) setLoading(false);
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(() => fetchData(true), 15000); // 15s High-speed refresh
    return () => clearInterval(interval);
  }, [symbol]);

  const handleTrade = async (e) => {
    e.preventDefault();
    setTradeLoading(true);
    setMessage(null);
    try {
      const res = await executeTrade({
        symbol,
        quantity: parseInt(quantity, 10),
        price: quote ? quote.price : 0,
        type: tradeType
      });
      if (res) {
        const actionStr = tradeType === 'BUY' ? 'bought' : 'sold';
        const hopeStr = tradeType === 'BUY' ? 'You are hoping the price goes up 📈' : 'You decided to take your money out 💰';
        setMessage({ type: 'success', text: `🎉 You ${actionStr} ${quantity} shares of ${symbol}! ${hopeStr}` });
        setTimeout(() => navigate('/portfolio'), 2500);
      }
    } catch (err) {
      console.error('TRADE ERROR', err);
      setMessage({ type: 'error', text: err.message || err || 'Oops! Trade failed. Try a smaller amount? 😅' });
    }
    setTradeLoading(false);
  };

  if (loading) {
    return (
      <div className="h-[80vh] w-full flex flex-col items-center justify-center gap-4">
        <div className="w-12 h-12 border-4 border-blue-500/20 border-t-blue-500 rounded-full animate-spin" />
        <p className="text-[10px] font-black text-blue-500 uppercase tracking-[0.3em] animate-pulse">Connecting to Liquidity... ⚡</p>
      </div>
    );
  }

  if (!quote) {
    return (
      <div className="h-[80vh] w-full flex flex-col items-center justify-center p-10 text-center">
        <div className="p-8 bg-rose-500/5 border border-rose-500/20 rounded-[2.5rem] max-w-sm space-y-6">
          <div className="w-16 h-16 bg-rose-500/10 rounded-2xl flex items-center justify-center mx-auto">
             <AlertTriangle size={32} className="text-rose-500" />
          </div>
          <div className="space-y-2">
            <h2 className="text-xl font-black text-white uppercase tracking-tight">Sync Interrupted</h2>
            <p className="text-slate-400 text-sm leading-relaxed">We couldn't bridge the live market link for <span className="text-white font-bold">{symbol}</span>. This usually happens during maintenance or rate limits.</p>
          </div>
          <button 
            onClick={() => window.location.reload()}
            className="w-full py-4 bg-rose-600 text-white font-black rounded-2xl uppercase tracking-widest text-[10px] hover:bg-rose-500 transition-colors shadow-lg shadow-rose-900/20"
          >
            Re-establish Link
          </button>
        </div>
      </div>
    );
  }

  const isUp = quote.changesPercentage >= 0;

  const generateInsights = () => {
    const insights = [];
    if (quote.changesPercentage > 2) {
      insights.push({ icon: <TrendingUp className="text-emerald-500"/>, title: "Strong Bullish Momentum", text: `What you're seeing: More people are buying ${symbol} today, pushing the price up by ${quote.changesPercentage.toFixed(2)}%.`, color: "bg-emerald-500/10 border-emerald-500/20 text-emerald-400" });
    } else if (quote.changesPercentage < -2) {
      insights.push({ icon: <TrendingDown className="text-rose-500"/>, title: "Market Weakness Detected", text: `What you're seeing: Sellers outnumber buyers today, causing a ${Math.abs(quote.changesPercentage).toFixed(2)}% drop. Often happens after bad news.`, color: "bg-rose-500/10 border-rose-500/20 text-rose-400" });
    } else {
      insights.push({ icon: <Activity className="text-blue-500"/>, title: "Low Volatility Consolidation", text: `What you're seeing: The price is stable. Buyers and sellers are currently balanced.`, color: "bg-blue-500/10 border-blue-500/20 text-blue-400" });
    }
    
    const hasNegativeNews = news.some(n => (n.title || "").toLowerCase().match(/(drop|fall|loss|lawsuit|fail|crash)/));
    if (hasNegativeNews) {
      insights.push({ icon: <AlertTriangle className="text-amber-500"/>, title: "Risk Alert: Negative Sentiments", text: "Reason: Recent news headlines contain bearish keywords. Confidence: Medium", color: "bg-amber-500/10 border-amber-500/20 text-amber-400" });
    }
    return insights;
  };

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="max-w-7xl mx-auto space-y-6 pb-20 px-4">
      {/* Sticky Header */}
      <div className="flex justify-between items-center bg-darker/50 backdrop-blur-xl p-4 rounded-2xl border border-white/5 sticky top-4 z-30">
        <button onClick={() => navigate(-1)} className="p-2 hover:bg-white/10 rounded-xl transition-colors text-slate-400 hover:text-white">
          <ArrowLeft size={20} />
        </button>
        <div className="text-center">
          <h2 className="text-xl font-black text-white leading-tight">{symbol}</h2>
          <p className={`text-[10px] font-black uppercase ${isUp ? 'text-emerald-400' : 'text-rose-400'}`}>
            {isUp ? '📈 SURGING' : '📉 DIPPING'} {Math.abs(quote.changesPercentage).toFixed(2)}%
          </p>
        </div>
        <div className="text-right">
          <h2 className="text-xl font-black text-white">{formatPrice(quote.price, 'US')}</h2>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        <div className="lg:col-span-8 space-y-6">
          
          {/* Tab Navigation */}
          <div className="flex gap-4 border-b border-white/5 px-2">
            {['OVERVIEW', 'LEARN', 'NEWS', 'INSIGHTS'].map(t => (
              <button 
                key={t} onClick={() => setActiveTab(t)}
                className={`pb-4 text-xs font-black tracking-widest transition-all relative ${activeTab === t ? 'text-blue-400' : 'text-slate-500 hover:text-slate-300'}`}
              >
                {t}
                {activeTab === t && <motion.div layoutId="tab" className="absolute bottom-0 left-0 right-0 h-1 bg-blue-500 rounded-full" />}
              </button>
            ))}
          </div>

          {/* Dynamic Content */}
          <div className="min-h-[400px]">
            <AnimatePresence mode="wait">
              {activeTab === 'OVERVIEW' && (
                <motion.div key="ov" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="glass-card p-6 border-slate-800 space-y-4">
                  <h4 className="text-slate-400 font-bold text-[10px] uppercase tracking-widest mb-4">Intraday Performance</h4>
                  <ChartComponent data={chart} color={isUp ? "#10b981" : "#f43f5e"} height={350} />
                  <div className="pt-4 border-t border-white/5">
                    <p className="text-[10px] font-black text-slate-500 uppercase mb-1">💰 Simulation Tip</p>
                    <p className="text-sm text-blue-400 font-bold">If you invested $1000 here yesterday, today it would be {formatPrice(1000 * (1 + quote.changesPercentage / 100), 'US')}.</p>
                  </div>
                </motion.div>
              )}

              {activeTab === 'LEARN' && (
                <motion.div key="lrn" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="glass-card p-6 border-l-4 border-l-blue-500">
                    <h4 className="text-white font-black text-sm uppercase mb-2">Total Company Value</h4>
                    <p className="text-2xl font-bold text-blue-400">{formatPrice(stock?.profile?.marketCapitalization / 1e9, 'US')} Billion</p>
                    <p className="text-xs text-slate-500 mt-2 italic">Think of this as the price tag for the whole company!</p>
                  </div>
                  <div className="glass-card p-6 border-l-4 border-l-purple-500">
                    <h4 className="text-white font-black text-sm uppercase mb-2">P/E Ratio (Value Score)</h4>
                    <p className="text-2xl font-bold text-purple-400">{stock?.profile?.pe || 'N/A'}</p>
                    <p className="text-xs text-slate-500 mt-2 italic">A high score means people expect huge growth! 🚀</p>
                  </div>
                </motion.div>
              )}

              {activeTab === 'INSIGHTS' && (
                <motion.div key="ins" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-4">
                  <div className={`p-6 rounded-2xl border-2 flex items-start gap-4 ${isUp ? 'bg-emerald-500/5 border-emerald-500/20 text-emerald-400' : 'bg-rose-500/5 border-rose-500/20 text-rose-400'}`}>
                    <Lightbulb size={24} className="shrink-0 mt-1" />
                    <div>
                      <h4 className="font-black uppercase text-sm tracking-tight mb-1">Market Sentiment: {isUp ? 'Happy 😎' : 'Nervous 😬'}</h4>
                      <p className="text-sm font-medium opacity-80">
                        {isUp ? "More people are buying than selling. The crowd is feeling confident about this stock's future!" : "Sellers currently outnumber buyers. Some investors might be worried about recent news."}
                      </p>
                    </div>
                  </div>
                  {(generateInsights() || []).map((insight, i) => (
                    <motion.div key={i} whileHover={{ scale: 1.01 }} className={`p-5 rounded-xl border flex items-start gap-4 ${insight.color} bg-opacity-10 backdrop-blur-md`}>
                      <div className="mt-1">{insight.icon}</div>
                      <div>
                        <h5 className="font-black uppercase tracking-widest text-xs mb-1">{insight?.title}</h5>
                        <p className="text-sm font-medium opacity-90">{insight?.text}</p>
                      </div>
                    </motion.div>
                  ))}
                </motion.div>
              )}

              {activeTab === 'NEWS' && (
                <motion.div key="news" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} className="glass-card p-6 border-slate-800 space-y-4">
                  <h4 className="text-slate-400 font-bold text-[10px] uppercase tracking-widest mb-4 flex items-center gap-2"><Newspaper size={14}/> Latest Headlines</h4>
                  <div className="space-y-4 max-h-[500px] overflow-y-auto no-scrollbar">
                    {news && (news || []).length > 0 ? (news || []).map((n, i) => (
                      <a key={i} href={n?.url} target="_blank" rel="noopener noreferrer" className="block flex gap-4 p-4 border border-white/5 bg-white/[0.02] hover:bg-white/[0.05] rounded-xl transition-all group cursor-pointer hover:border-white/10">
                        <div className="flex flex-col justify-between">
                          <p className="text-sm text-slate-200 font-bold line-clamp-2 leading-tight group-hover:text-blue-400 transition-colors">{n?.headline}</p>
                          <span className="text-[10px] text-slate-500 uppercase font-black tracking-widest mt-2 block">{n?.source} • {n?.datetime ? new Date(n.datetime).toLocaleDateString() : 'Recent'}</span>
                        </div>
                      </a>
                    )) : (
                      <div className="p-8 text-center text-slate-500 font-bold text-xs uppercase tracking-widest border border-dashed border-slate-700 rounded-xl">No recent news found for {symbol}</div>
                    )}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        {/* RIGHT COLUMN: Order Book */}
        <div className="lg:col-span-4">
          <div className="glass-card p-8 border-blue-500/20 sticky top-28 shadow-2xl">
            <div className="text-center bg-blue-500/10 p-2 rounded-lg border border-blue-500/20 mb-6">
              <span className="text-[10px] font-black text-blue-400 uppercase tracking-widest flex items-center justify-center gap-1">
                <Info size={12}/> PRACTICE TRADE (NOT REAL MONEY)
              </span>
            </div>

            <AnimatePresence>
              {message && (
                <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} className={`p-4 rounded-xl mb-6 text-xs font-black text-center ${message.type === 'success' ? 'bg-emerald-500 text-white' : 'bg-rose-500 text-white'}`}>
                  {message.text}
                </motion.div>
              )}
            </AnimatePresence>

            <form onSubmit={handleTrade} className="space-y-6">
              <div className="flex gap-2 p-1 bg-white/5 rounded-xl border border-white/5">
                <button type="button" onClick={() => setTradeType('BUY')} className={`flex-1 py-3 rounded-lg text-xs font-black uppercase transition-all ${tradeType === 'BUY' ? 'bg-emerald-500 text-white' : 'text-slate-500'}`}>BUY</button>
                <button type="button" onClick={() => setTradeType('SELL')} className={`flex-1 py-3 rounded-lg text-xs font-black uppercase transition-all ${tradeType === 'SELL' ? 'bg-rose-500 text-white' : 'text-slate-500'}`}>SELL</button>
              </div>

              <div>
                <label className="text-[10px] font-black text-slate-500 uppercase block mb-2">Quantity</label>
                <input type="number" min="1" max="1000" value={quantity} onChange={e => setQuantity(e.target.value)} className="w-full bg-white/5 border border-white/10 rounded-xl p-4 text-white text-2xl font-black text-center focus:border-blue-500 transition-colors" />
              </div>

              <div className="flex justify-between items-center p-4 bg-slate-900 rounded-xl border border-slate-800">
                <span className="text-slate-500 text-[10px] font-black uppercase">Est. Total</span>
                <span className="text-blue-400 font-black text-xl">{formatPrice(quote.price * (quantity || 0), 'US')}</span>
              </div>

              <button type="submit" disabled={tradeLoading} className={`w-full py-4 rounded-2xl text-white font-black uppercase tracking-widest transition-all ${tradeType === 'BUY' ? 'bg-emerald-600 hover:bg-emerald-500 shadow-emerald-500/20' : 'bg-rose-600 hover:bg-rose-500 shadow-rose-500/20'} hover:scale-[1.02] shadow-xl`}>
                {tradeLoading ? 'EXECUTING...' : `CONFIRM ${tradeType}`}
              </button>
            </form>
          </div>
        </div>
      </div>
    </motion.div>
  );
}
