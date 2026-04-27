import { useState, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useParams, useNavigate } from 'react-router-dom';
import { getStockDetails, getChartData, getLiveQuotes, getNews, getFamousInsights, explainStock } from '../services/api';
import ChartComponent from '../components/ChartComponent';
import InsightPanel from '../components/InsightPanel';
import { ArrowLeft, TrendingUp, TrendingDown, Activity, Briefcase, Info, Newspaper, Lightbulb, CheckCircle2, AlertTriangle, PlayCircle, Quote as QuoteIcon, ArrowRight, Zap, Target, Loader2 } from 'lucide-react';
import { formatPrice } from '../utils/formatters';
import InfoTooltip from '../components/InfoTooltip';
import { useTrading } from '../context/TradingContext';

export default function StockDetails() {
  const { symbol } = useParams();
  const navigate = useNavigate();
  const { executeBuy, executeSell } = useTrading();
  const [stock, setStock] = useState(null);
  const [quote, setQuote] = useState(null);
  const [chart, setChart] = useState([]);
  const [news, setNews] = useState([]);
  const [famousInsights, setFamousInsights] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('OVERVIEW');
  
  // Gamification & Interaction State
  const [selectedPoint, setSelectedPoint] = useState(null);
  const [insightPanelContent, setInsightPanelContent] = useState(null);
  const [insightScore, setInsightScore] = useState(() => {
    return parseInt(localStorage.getItem('insightScore') || '0', 10);
  });
  const [showScorePopup, setShowScorePopup] = useState(false);

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
        getNews(symbol),
        getFamousInsights(symbol)
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

      if (results[4].status === 'fulfilled') {
        setFamousInsights(results[4].value || []);
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

  const handleChartClick = async (point) => {
    setSelectedPoint(point);
    
    // Call AI Insight Endpoint
    setInsightPanelContent({
      title: "Analyzing Data Point...",
      explanation: `Fetching AI-driven context for ${symbol} at $${point.value}...`,
      type: 'stock',
      data: [
        { label: 'Time', value: point.formattedTime },
        { label: 'Price', value: `$${point.value.toLocaleString()}`, color: 'text-blue-400' }
      ]
    });

    try {
      // Determine trend based on chart index
      const prevPoint = chart[point.index - 1];
      const trend = prevPoint ? (point.value >= prevPoint.value ? 'UP' : 'DOWN') : 'SIDEWAYS';
      const change = prevPoint ? ((point.value - prevPoint.value) / prevPoint.value * 100).toFixed(2) : 0;

      const aiRes = await explainStock({
        symbol,
        trend,
        action: 'observing',
        type: 'graph_point',
        metrics: { price: point.value }
      });

      if (aiRes) {
        setInsightPanelContent({
          title: `${symbol} Analysis`,
          explanation: aiRes.explanation,
          insight: aiRes.observation,
          type: 'stock',
          data: [
            { label: 'Timestamp', value: point.formattedTime },
            { label: 'Snapshot Price', value: `$${point.value.toLocaleString()}`, color: 'text-white' },
            { label: 'Momentum', value: `${change}%`, color: Number(change) >= 0 ? 'text-emerald-500' : 'text-rose-500' }
          ],
          actions: [{ label: 'I Understand', primary: true }]
        });

        // Gamification: Update Score
        const pointsEarned = Number(change) < -1 ? 10 : 5; // +10 for clicking dips
        const newScore = insightScore + pointsEarned;
        setInsightScore(newScore);
        localStorage.setItem('insightScore', newScore.toString());
        setShowScorePopup(pointsEarned);
        setTimeout(() => setShowScorePopup(false), 2000);
      }
    } catch (err) {
      setInsightPanelContent(prev => ({ ...prev, explanation: "AI Mentor is currently analyzing other markets. Please try again in a moment." }));
    }
  };

  const handleTrade = async (e) => {
    e.preventDefault();
    setTradeLoading(true);
    setMessage(null);
    try {
      let res;
      if (tradeType === 'BUY') {
        res = await executeBuy({ symbol, price: quote ? quote.price : 0 }, quantity);
      } else {
        res = await executeSell(symbol, (quote ? quote.price : 0) * quantity, quantity);
      }
      
      if (res && res.success) {
        const actionStr = tradeType === 'BUY' ? 'bought' : 'sold';
        const hopeStr = tradeType === 'BUY' ? 'You are hoping the price goes up 📈' : 'You decided to take your money out 💰';
        setMessage({ type: 'success', text: `🎉 You ${actionStr} ${quantity} shares of ${symbol}! ${hopeStr}` });
      } else {
        setMessage({ type: 'error', text: res?.error || 'Oops! Trade failed. Try a smaller amount? 😅' });
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
      <InsightPanel isOpen={!!insightPanelContent} onClose={() => setInsightPanelContent(null)} content={insightPanelContent} />
      
      {/* Score Popup */}
      <AnimatePresence>
        {showScorePopup && (
          <motion.div 
            initial={{ opacity: 0, y: 50, scale: 0.5 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, scale: 1.5 }}
            className="fixed bottom-32 left-1/2 -translate-x-1/2 z-[200] flex flex-col items-center"
          >
            <div className="bg-blue-600 text-white px-6 py-3 rounded-2xl font-black uppercase tracking-widest text-sm shadow-2xl flex items-center gap-3">
              <Zap size={20} className="fill-white" /> +{showScorePopup} Insight Score
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 5. DATA HEADER IMPROVED */}
      <div className="bg-slate-900/60 backdrop-blur-xl p-6 rounded-[2.5rem] border border-white/5 sticky top-4 z-30 shadow-2xl flex flex-col md:flex-row justify-between items-center gap-6">
        <div className="flex items-center gap-4">
          <button onClick={() => navigate(-1)} className="p-3 hover:bg-white/5 rounded-2xl transition-all text-slate-500 hover:text-white border border-transparent hover:border-white/10 group">
            <ArrowLeft size={20} className="group-hover:-translate-x-1 transition-transform" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-3xl font-black text-white leading-none tracking-tighter">{symbol}</h2>
              <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest bg-white/5 px-2 py-1 rounded-lg">{stock?.profile?.companyName || 'Stock'}</span>
            </div>
            <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mt-1 flex items-center gap-2">
               <Activity size={10} /> Market Data Live • {new Date().toLocaleTimeString()}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-8">
          <div className="text-right">
            <h2 className="text-3xl font-black text-white tracking-tight">{formatPrice(quote.price, 'US')}</h2>
            <div className={`flex items-center justify-end gap-1 font-black text-xs ${isUp ? 'text-emerald-500' : 'text-rose-500'}`}>
               {isUp ? <TrendingUp size={14} /> : <TrendingDown size={14} />}
               <span>{isUp ? '+' : ''}{quote.changesPercentage.toFixed(2)}%</span>
            </div>
          </div>
          <div className="h-10 w-[1px] bg-white/5 hidden md:block" />
          <div className="flex flex-col items-end">
            <span className="text-[9px] font-black text-blue-500 uppercase tracking-[0.2em] mb-1">Insight Score</span>
            <div className="flex items-center gap-2 bg-blue-500/10 px-3 py-1.5 rounded-xl border border-blue-500/20">
               <Target size={14} className="text-blue-500" />
               <span className="text-sm font-black text-white">{insightScore}</span>
            </div>
          </div>
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
                <motion.div key="ov" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="bg-slate-900/40 rounded-[2.5rem] p-8 border border-white/5 space-y-6 relative overflow-hidden group">
                  <div className="absolute top-0 right-0 p-8 opacity-[0.02] group-hover:opacity-5 transition-opacity"><Zap size={200} /></div>
                  
                  <div className="flex items-center justify-between relative z-10">
                    <div>
                      <h4 className="text-white font-black text-lg tracking-tight uppercase">Intraday Performance</h4>
                      <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Click any point for deep-dive analysis</p>
                    </div>
                    <div className="flex gap-2">
                       <span className="px-3 py-1 bg-white/5 rounded-full text-[9px] font-black text-slate-400 uppercase border border-white/5">Real-time</span>
                    </div>
                  </div>

                  <ChartComponent 
                    data={chart} 
                    color={isUp ? "#10b981" : "#f43f5e"} 
                    height={400} 
                    onPointClick={handleChartClick}
                  />

                  <div className="pt-6 border-t border-white/5 flex flex-col md:flex-row justify-between items-center gap-4 relative z-10">
                    <div className="flex items-center gap-3">
                       <div className="w-10 h-10 rounded-xl bg-blue-600/10 flex items-center justify-center border border-blue-500/20"><Info size={20} className="text-blue-500" /></div>
                       <div>
                         <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Simulation Context</p>
                         <p className="text-sm text-blue-100 font-bold">If you invested $1,000 yesterday, your profit would be <span className="text-emerald-400">{formatPrice(Math.max(0, 1000 * (quote.changesPercentage / 100)), 'US')}</span>.</p>
                       </div>
                    </div>
                    <button className="px-6 py-3 bg-white/5 hover:bg-white/10 text-white rounded-xl text-[10px] font-black uppercase tracking-widest transition-all">Export Analysis</button>
                  </div>
                </motion.div>
              )}

              {activeTab === 'LEARN' && (
                <motion.div key="lrn" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="bg-slate-900/40 border border-white/5 p-8 rounded-[2rem] border-l-4 border-l-blue-500">
                      <div className="flex items-center justify-between mb-2">
                        <h4 className="text-slate-400 font-black text-[10px] uppercase tracking-widest flex items-center gap-2">
                           Total Company Value <InfoTooltip concept="marketcap" />
                        </h4>
                      </div>
                      <p className="text-3xl font-black text-white">{formatPrice(stock?.profile?.marketCapitalization / 1e9, 'US')} Billion</p>
                      <p className="text-xs text-slate-500 mt-4 italic font-medium leading-relaxed">"Think of this as the price tag for the entire company if you bought every single share!"</p>
                    </div>
                    <div className="bg-slate-900/40 border border-white/5 p-8 rounded-[2rem] border-l-4 border-l-purple-500">
                      <div className="flex items-center justify-between mb-2">
                        <h4 className="text-slate-400 font-black text-[10px] uppercase tracking-widest flex items-center gap-2">
                           Value Score (P/E Ratio) <InfoTooltip concept="peratio" />
                        </h4>
                      </div>
                      <p className="text-3xl font-black text-white">{stock?.profile?.pe || 'N/A'}</p>
                      <p className="text-xs text-slate-500 mt-4 italic font-medium leading-relaxed">"A high score means investors expect massive future growth and are willing to pay a premium today."</p>
                    </div>
                  </div>

                  {famousInsights.length > 0 && (
                    <div className="space-y-4">
                      <div className="flex items-center gap-3 px-2">
                        <div className="p-2 bg-rose-600 rounded-xl shadow-lg shadow-rose-600/20"><PlayCircle size={16} className="text-white" /></div>
                        <h2 className="text-[10px] font-black text-white uppercase tracking-widest">Investor Wisdom (YouTube)</h2>
                      </div>
                      
                      <div className="grid grid-cols-1 gap-4">
                        {famousInsights.map((insight, i) => (
                          <motion.div 
                            key={i}
                            whileHover={{ scale: 1.01 }}
                            className="bg-slate-900/60 border border-white/5 rounded-[2.5rem] p-10 relative overflow-hidden group cursor-pointer"
                            onClick={() => window.open(insight.podcastUrl, '_blank')}
                          >
                            <div className="absolute top-0 right-0 p-8 opacity-5 group-hover:opacity-10 transition-opacity">
                              <QuoteIcon size={120} className="text-white" />
                            </div>
                            <div className="relative z-10 flex flex-col md:flex-row gap-10 items-center">
                              <div className="w-20 h-20 rounded-3xl bg-rose-600/10 flex items-center justify-center border border-rose-500/20 shrink-0 group-hover:bg-rose-600/20 transition-colors">
                                <PlayCircle size={40} className="text-rose-500" />
                              </div>
                              <div className="space-y-4 text-center md:text-left flex-1">
                                <p className="text-[10px] font-black text-rose-500 uppercase tracking-[0.3em]">{insight.investor} • {insight.title}</p>
                                <h4 className="text-2xl font-bold text-white leading-tight italic">"{insight.message}"</h4>
                                <div className="flex items-center justify-center md:justify-start gap-4">
                                   <span className="text-[10px] text-slate-500 font-black uppercase tracking-widest flex items-center gap-2">
                                      Tap to watch insight <ArrowRight size={14} className="text-blue-500" />
                                   </span>
                                </div>
                              </div>
                            </div>
                          </motion.div>
                        ))}
                      </div>
                    </div>
                  )}
                </motion.div>
              )}

              {activeTab === 'INSIGHTS' && (
                <motion.div key="ins" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-4">
                  <div className={`p-8 rounded-[2rem] border-2 flex items-start gap-6 ${isUp ? 'bg-emerald-500/5 border-emerald-500/20 text-emerald-400' : 'bg-rose-500/5 border-rose-500/20 text-rose-400'}`}>
                    <div className="p-3 bg-white/5 rounded-2xl border border-white/10 shrink-0"><Lightbulb size={28} /></div>
                    <div>
                      <h4 className="font-black uppercase text-lg tracking-tight mb-2 italic">Market Vibe: {isUp ? 'Strong Confidence 😎' : 'Increased Anxiety 😬'}</h4>
                      <p className="text-base font-medium opacity-80 leading-relaxed">
                        {isUp ? "More people are buying than selling. The crowd is feeling confident about this stock's future! This usually suggests a healthy appetite for growth." : "Sellers currently outnumber buyers. Some investors might be worried about recent news or macro-economic shifts. It's a time for caution."}
                      </p>
                    </div>
                  </div>
                  {(generateInsights() || []).map((insight, i) => (
                    <motion.div key={i} whileHover={{ scale: 1.01 }} className={`p-6 rounded-2xl border flex items-start gap-4 ${insight.color} bg-opacity-10 backdrop-blur-md`}>
                      <div className="mt-1 p-2 bg-white/5 rounded-lg">{insight.icon}</div>
                      <div>
                        <h5 className="font-black uppercase tracking-widest text-[10px] mb-1">{insight?.title}</h5>
                        <p className="text-sm font-medium opacity-90 leading-relaxed">{insight?.text}</p>
                      </div>
                    </motion.div>
                  ))}
                </motion.div>
              )}

              {activeTab === 'NEWS' && (
                <motion.div key="news" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} className="bg-slate-900/40 rounded-[2.5rem] p-8 border border-white/5 space-y-6">
                  <div className="flex items-center justify-between px-2">
                    <h4 className="text-white font-black text-lg tracking-tight uppercase flex items-center gap-3">
                       <Newspaper size={20} className="text-blue-500"/> Intelligence Feed
                    </h4>
                  </div>
                  <div className="space-y-4 max-h-[600px] overflow-y-auto pr-4 custom-scrollbar">
                    {news && (news || []).length > 0 ? (news || []).map((n, i) => (
                      <a key={i} href={n?.url} target="_blank" rel="noopener noreferrer" className="block p-6 border border-white/5 bg-white/[0.02] hover:bg-white/[0.05] rounded-3xl transition-all group cursor-pointer hover:border-blue-500/20 hover:scale-[1.01]">
                        <div className="flex flex-col justify-between h-full">
                          <p className="text-lg text-slate-100 font-bold line-clamp-2 leading-tight group-hover:text-blue-400 transition-colors">{n?.headline}</p>
                          <div className="flex items-center justify-between mt-6">
                            <span className="text-[10px] text-slate-500 uppercase font-black tracking-widest">{n?.source}</span>
                            <span className="text-[10px] text-slate-500 uppercase font-black tracking-widest">{n?.datetime ? new Date(n.datetime).toLocaleDateString() : 'Recent'}</span>
                          </div>
                        </div>
                      </a>
                    )) : (
                      <div className="p-20 text-center text-slate-500 font-black text-xs uppercase tracking-[0.3em] border-2 border-dashed border-white/5 rounded-[2rem]">No recent news found for {symbol}</div>
                    )}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        {/* RIGHT COLUMN: Order Book */}
        <div className="lg:col-span-4">
          <div className="bg-slate-900 border border-blue-500/30 p-10 rounded-[3rem] sticky top-32 shadow-[0_30px_100px_rgba(0,0,0,0.5)] overflow-hidden group">
            <div className="absolute top-0 right-0 w-32 h-32 bg-blue-600/5 blur-3xl rounded-full group-hover:bg-blue-600/10 transition-colors" />
            
            <div className="relative z-10 space-y-8">
              <div className="text-center bg-blue-600/10 p-3 rounded-2xl border border-blue-500/20">
                <span className="text-[9px] font-black text-blue-400 uppercase tracking-[0.2em] flex items-center justify-center gap-2">
                  <Shield size={14}/> Practice Mode • No Real Money
                </span>
              </div>

              <AnimatePresence>
                {message && (
                  <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} className={`p-5 rounded-2xl mb-6 text-sm font-black text-center shadow-xl ${message.type === 'success' ? 'bg-emerald-600 text-white' : 'bg-rose-600 text-white'}`}>
                    {message.text}
                  </motion.div>
                )}
              </AnimatePresence>

              <form onSubmit={handleTrade} className="space-y-8">
                <div className="flex gap-2 p-1.5 bg-white/5 rounded-2xl border border-white/5">
                  <button type="button" onClick={() => setTradeType('BUY')} className={`flex-1 py-4 rounded-xl text-xs font-black uppercase tracking-widest transition-all ${tradeType === 'BUY' ? 'bg-emerald-500 text-white shadow-lg' : 'text-slate-500'}`}>BUY</button>
                  <button type="button" onClick={() => setTradeType('SELL')} className={`flex-1 py-4 rounded-xl text-xs font-black uppercase tracking-widest transition-all ${tradeType === 'SELL' ? 'bg-rose-500 text-white shadow-lg' : 'text-slate-500'}`}>SELL</button>
                </div>

                <div className="space-y-3">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex justify-between">
                    Shares to Purchase <span>Max: 1000</span>
                  </label>
                  <input type="number" min="1" max="1000" value={quantity} onChange={e => setQuantity(e.target.value)} className="w-full bg-white/5 border border-white/10 rounded-2xl p-6 text-white text-4xl font-black text-center focus:border-blue-500 focus:bg-white/10 transition-all outline-none" />
                </div>

                <div className="flex justify-between items-center p-6 bg-slate-950/50 rounded-2xl border border-white/5">
                  <span className="text-slate-500 text-[10px] font-black uppercase tracking-widest">Total Value</span>
                  <span className="text-blue-400 font-black text-2xl">{formatPrice(quote.price * (quantity || 0), 'US')}</span>
                </div>

                <button type="submit" disabled={tradeLoading} className={`w-full py-5 rounded-[1.5rem] text-white font-black uppercase tracking-[0.2em] text-xs transition-all ${tradeType === 'BUY' ? 'bg-emerald-600 hover:bg-emerald-500 shadow-emerald-500/20' : 'bg-rose-600 hover:bg-rose-500 shadow-rose-500/20'} hover:scale-[1.02] shadow-2xl active:scale-95 flex items-center justify-center gap-3`}>
                  {tradeLoading ? <Loader2 className="animate-spin" /> : <>EXECUTE {tradeType} ORDER <ArrowRight size={16}/></>}
                </button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
}
