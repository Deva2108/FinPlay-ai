import { useState, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useParams, useNavigate } from 'react-router-dom';
import { getStockDetails, getChartData, getNews, getFamousInsights, explainStock, addToWatchlist, removeFromWatchlist, checkWatchlist } from '../services/api';
import ChartComponent from '../components/ChartComponent';
import InsightPanel from '../components/InsightPanel';
import { ArrowLeft, TrendingUp, TrendingDown, Activity, Briefcase, Info, Newspaper, Lightbulb, CheckCircle2, AlertTriangle, PlayCircle, Quote as QuoteIcon, ArrowRight, Zap, Target, Loader2, Bookmark, BookmarkCheck, BarChart3, PieChart, Landmark, Bot } from 'lucide-react';
import { formatPrice } from '../utils/formatters';
import InfoTooltip from '../components/InfoTooltip';
import { useTrading } from '../context/TradingContext';
import { useMarket } from '../context/MarketContext';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer, Cell } from 'recharts';
import DataBadge from '../components/DataBadge';

export default function StockDetails() {
  const { symbol } = useParams();
  const navigate = useNavigate();
  const { marketMode } = useMarket();
  
  const [stock, setStock] = useState(null);
  const [chart, setChart] = useState([]);
  const [news, setNews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [timeframe, setTimeframe] = useState('1M');
  const [isInWatchlist, setIsInWatchlist] = useState(false);
  const [watchlistLoading, setWatchlistLoading] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [detailsRes, newsRes, watchlistRes] = await Promise.all([
        getStockDetails(symbol),
        getNews(symbol),
        checkWatchlist(symbol)
      ]);
      setStock(detailsRes?.data);
      setNews(newsRes?.data || []);
      setIsInWatchlist(watchlistRes?.data);
    } finally {
      setLoading(false);
    }
  };

  const fetchChart = async () => {
    setLoadingChart(true);
    try {
      const res = await getChartData(symbol, timeframe);
      setChart(res?.data?.chartData || []);
    } finally {
      setLoadingChart(false);
    }
  };

  useEffect(() => { fetchData(); }, [symbol]);
  useEffect(() => { fetchChart(); }, [symbol, timeframe]);

  const isUp = stock?.change >= 0;
  const currency = stock?.currency || 'INR';

  if (loading) return <div className="h-screen flex items-center justify-center bg-[#020617]"><Loader2 size={40} className="text-blue-500 animate-spin" /></div>;

  return (
    <div className="min-h-screen bg-[#020617]">
      <div className="max-w-7xl mx-auto p-6 sm:p-10 space-y-10 pb-32">
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
          <div className="flex items-center gap-6">
            <button onClick={() => navigate(-1)} className="p-3 bg-white/5 rounded-2xl transition-all border border-white/5"><ArrowLeft size={24} className="text-slate-400" /></button>
            <div className="space-y-1">
              <div className="flex items-center gap-4">
                 <h1 className="text-5xl font-black text-white tracking-tighter uppercase">{stock?.symbol}</h1>
                 <div className={`px-4 py-1.5 rounded-xl font-black text-sm ${isUp ? 'bg-emerald-500/10 text-emerald-500' : 'bg-rose-500/10 text-rose-500'}`}>{isUp ? '+' : ''}{stock?.change}%</div>
              </div>
              <p className="text-xs font-bold text-slate-500 uppercase tracking-widest">Market Terminal</p>
            </div>
          </div>
          <DataBadge meta={stock?.meta} />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10">
          <div className="lg:col-span-8">
            <div className="bg-slate-900/40 rounded-[3rem] border border-white/5 p-8 sm:p-10 relative overflow-hidden group">
               <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6 mb-10">
                  <div>
                    <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Price</p>
                    <p className="text-5xl font-black text-white tracking-tighter">{formatPrice(stock?.price, currency)}</p>
                    <div className="flex items-center gap-2 mt-2">
                       <InfoTooltip concept="pnl">
                          <p className="text-[9px] font-bold text-blue-400/80 uppercase tracking-widest">Master P&L logic to build edge</p>
                       </InfoTooltip>
                    </div>
                  </div>
                  <div className="flex bg-slate-900/80 rounded-2xl p-1 border border-white/5 gap-1">
                    {['1D', '1W', '1M', '1Y'].map((tf) => (
                      <button key={tf} onClick={() => setTimeframe(tf)} className={`px-5 py-2 rounded-xl text-[10px] font-black transition-all ${timeframe === tf ? 'bg-blue-600 text-white' : 'text-slate-500 hover:text-white'}`}>{tf}</button>
                    ))}
                  </div>
               </div>
               <div className="h-[400px] w-full relative">
                  {loadingChart && <div className="absolute inset-0 z-10 bg-[#020617]/40 backdrop-blur-sm flex items-center justify-center rounded-3xl"><Zap size={32} className="text-blue-500 animate-spin" /></div>}
                  <ChartComponent data={chart} meta={stock?.meta} color={isUp ? '#10b981' : '#f43f5e'} height={400} />
               </div>
            </div>
          </div>

          <div className="lg:col-span-4 space-y-8">
             <div className="p-10 rounded-[3rem] bg-gradient-to-br from-indigo-600/20 to-blue-600/5 border border-indigo-500/20 relative overflow-hidden shadow-2xl">
                <div className="absolute top-0 right-0 p-6 opacity-10"><Bot size={100} className="text-indigo-400" /></div>
                <div className="flex items-center gap-3 mb-6"><Bot size={20} className="text-indigo-400" /><span className="text-[10px] font-black text-indigo-400 uppercase tracking-widest">Mentor Analysis</span></div>
                <p className="text-xl font-bold text-white leading-relaxed italic mb-8">"{stock?.mentorLine || "Analyzing market DNA..."}"</p>
             </div>
             <button onClick={() => navigate('/')} className="w-full py-5 bg-white/5 border border-white/10 text-white rounded-[2rem] text-[10px] font-black uppercase tracking-[0.2em] transition-all flex items-center justify-center gap-3"><Activity size={16} /> Enter Trading Arena</button>
          </div>
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value, concept }) {
  return (
    <div className="bg-slate-900/60 p-6 rounded-3xl border border-white/5 space-y-2 hover:bg-slate-800/80 transition-all group">
       <div className="flex items-center justify-between">
          <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest">{label}</p>
          <InfoTooltip concept={concept} />
       </div>
       <p className="text-xl font-black text-white tracking-tighter group-hover:text-blue-400 transition-colors">{value}</p>
    </div>
  );
}
