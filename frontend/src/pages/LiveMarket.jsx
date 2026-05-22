import { useState, useMemo, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, TrendingDown, Activity, Bot, Sparkles, Loader2, Info, ArrowRight, MousePointer2, Zap, ArrowLeft, Building2 } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useMarket } from '../context/MarketContext';
import { getChartData, getStockDetails, getLiveQuotes } from '../services/api';
import ChartComponent from '../components/ChartComponent';
import InfoTooltip from '../components/InfoTooltip';

export default function LiveMarket() {
  const { marketMode, currencySymbol } = useMarket();
  const navigate = useNavigate();
  const location = useLocation();
  
  // Default to Nifty 50 or S&P 500
  const defaultIndex = marketMode === 'INDIA' ? '^NSEI' : 'SPY';
  const [symbol, setSymbol] = useState(location.state?.selectedStock || defaultIndex);
  const [details, setDetails] = useState(null);
  const [chartData, setChartData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isSyncing, setIsSyncing] = useState(false);
  const [loadingChart, setLoadingChart] = useState(false);
  const [timeframe, setTimeframe] = useState('1D');
  const [constituents, setConstituents] = useState([]);

  // Fetch constituents based on market
  const constituentsList = useMemo(() => {
    return marketMode === 'INDIA' 
      ? ['RELIANCE.NS', 'TCS.NS', 'HDFCBANK.NS', 'ICICIBANK.NS', 'INFY.NS', 'BHARTIARTL.NS', 'SBI.NS', 'LICI.NS', 'ITC.NS', 'HINDUNILVR.NS']
      : ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META', 'TSLA', 'BRK.B', 'LLY', 'AVGO'];
  }, [marketMode]);

  const fetchData = async () => {
    setLoading(true);
    setIsSyncing(false);
    try {
      const [detailsRes, quotesRes] = await Promise.all([
        getStockDetails(symbol),
        getLiveQuotes(constituentsList)
      ]);
      
      if (detailsRes?.syncing) {
        setIsSyncing(true);
      } else {
        setDetails(detailsRes?.data);
      }
      
      setConstituents(quotesRes?.data || []);
    } catch (err) {
      console.error("Failed to load market hub", err);
    } finally {
      setLoading(false);
    }
  };

  const fetchChart = async () => {
    setLoadingChart(true);
    try {
      const res = await getChartData(symbol, timeframe);
      setChartData(res?.data?.data || []);
    } catch (err) {
      setChartData([]);
    } finally {
      setLoadingChart(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [symbol, marketMode]);

  useEffect(() => {
    fetchChart();
  }, [symbol, timeframe]);

  const isUp = details?.change >= 0;
  const isIndex = symbol?.startsWith('^') || symbol === 'SPY' || symbol === 'QQQ' || symbol === 'DIA';

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="max-w-6xl mx-auto p-6 sm:p-10 space-y-10 pb-32">
      
      {/* Header & Symbol Toggle */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-6">
        <div className="space-y-1">
          <div className="flex items-center gap-4">
            <button onClick={() => setSymbol(defaultIndex)} className={`px-4 py-2 rounded-2xl text-[10px] font-black uppercase tracking-widest transition-all ${symbol === defaultIndex ? 'bg-blue-600 text-white shadow-lg' : 'bg-white/5 text-slate-500 hover:text-white'}`}>
              Global Index
            </button>
            <h1 className="text-4xl font-black text-white tracking-tighter uppercase flex items-center gap-2">
              {symbol.startsWith('^') ? (symbol === '^NSEI' ? 'NIFTY 50' : symbol === '^BSESN' ? 'SENSEX' : symbol) : symbol === 'SPY' ? 'S&P 500' : symbol}
              <InfoTooltip concept="index" />
            </h1>
          </div>
          {isSyncing ? (
            <p className="text-sm font-black text-slate-500 uppercase tracking-widest animate-pulse">Updating market data...</p>
          ) : (
            <p className="text-2xl font-black text-white flex items-center gap-3">
              {isIndex ? (details?.price ?? '--') : `${currencySymbol}${details?.price ?? '--'}`}
              <span className={`text-sm font-black ${isUp ? 'text-emerald-500' : 'text-rose-500'}`}>
                {isUp ? '+' : ''}{details?.change ?? '--'}%
              </span>
            </p>
          )}
        </div>

        <div className="flex gap-1 bg-white/5 p-1 rounded-2xl border border-white/5">
          {['1D', '1W', '1M', '1Y', '5Y'].map((tf) => (
            <button 
              key={tf} 
              onClick={() => setTimeframe(tf)} 
              className={`px-6 py-2 rounded-xl text-xs font-black transition-all ${timeframe === tf ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'}`}
            >
              {tf}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-10">
        {/* Main Chart Section */}
        <div className="lg:col-span-8 space-y-8">
          <div className="p-8 rounded-[3rem] bg-slate-900/40 border border-white/5 relative overflow-hidden group">
            <div className="absolute top-6 left-8 z-10">
               <p className="text-[10px] font-black text-blue-400 uppercase tracking-widest flex items-center gap-2">
                 <Activity size={12} /> Institutional Mirror
               </p>
            </div>

            <div className="h-[350px] w-full mt-6 relative">
              {loadingChart && (
                <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 bg-[#020617]/20 backdrop-blur-sm rounded-3xl">
                  <Zap size={24} className="text-blue-500 animate-spin" />
                  <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest animate-pulse">Mapping History...</span>
                </div>
              )}
              <ChartComponent 
                data={chartData} 
                color={isUp ? '#10b981' : '#f43f5e'} 
                height={350} 
                isIndex={isIndex}
              />
            </div>
          </div>

          {/* AI Mentor Context */}
          <div className="p-10 rounded-[3rem] bg-indigo-600/5 border border-indigo-500/20 relative overflow-hidden">
             <div className="absolute top-0 right-0 p-8 opacity-5"><Bot size={120} className="text-indigo-400" /></div>
             <div className="flex items-center gap-3 mb-4">
                <Bot size={20} className="text-indigo-400" />
                <span className="text-[10px] font-black text-indigo-400 uppercase tracking-widest">Market Mentor</span>
             </div>
             <p className="text-lg font-bold text-white leading-relaxed">
               "{details?.mentorLine || "Select a stock to decode its market DNA with our AI advisor."}"
             </p>
          </div>
        </div>

        {/* Constituents List */}
        <div className="lg:col-span-4 space-y-6">
          <div className="flex items-center justify-between px-2">
            <h3 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
              <Building2 size={12} /> Top Companies
            </h3>
          </div>

          <div className="space-y-3 max-h-[600px] overflow-y-auto pr-2 custom-scrollbar">
            {constituents.map((stock) => (
              <motion.div
                key={stock.symbol}
                whileHover={{ x: 5 }}
                onClick={() => navigate(`/stock/${stock.symbol}`)}
                className="p-4 rounded-2xl bg-white/5 border border-white/5 flex items-center justify-between cursor-pointer hover:bg-white/10 transition-all group"
              >
                <div className="flex items-center gap-3">
                  <div className={`w-1 h-8 rounded-full ${stock.changesPercentage >= 0 ? 'bg-emerald-500' : 'bg-rose-500'}`} />
                  <div>
                    <div className="flex items-center gap-1">
                      <p className="text-sm font-black text-white tracking-tight">{stock.symbol}</p>
                      <InfoTooltip concept="moat" />
                    </div>
                    <p className="text-[9px] font-bold text-slate-500 uppercase truncate max-w-[120px]">{stock.name}</p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-sm font-black text-white tracking-tighter">{currencySymbol}{stock.price ?? '--'}</p>
                  <p className={`text-[10px] font-black ${stock.changesPercentage >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>
                    {stock.changesPercentage >= 0 ? '+' : ''}{stock.changesPercentage ?? '--'}%
                  </p>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </motion.div>
  );
}
