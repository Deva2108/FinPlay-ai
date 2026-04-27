import { useState, useRef, useMemo, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, TrendingDown, Activity, Bot, Sparkles, Loader2, Info, ArrowRight, MousePointer2, Zap } from 'lucide-react';
import { useLocation } from 'react-router-dom';
import { useMarket } from '../context/MarketContext';
import { explainStock, evaluateDecision, trackDecision } from '../services/api';
import { useBehavior } from '../context/BehaviorContext';
import { useGuide } from '../hooks/useGuide';
import GuideOverlay from '../components/GuideOverlay';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

export default function LiveMarket() {
  const { marketMode, marketCode, currencySymbol } = useMarket();
  const { currentStep, nextStep, setStep } = useGuide(1);
  const location = useLocation();
  const initialStock = location.state?.selectedStock || (marketMode === 'INDIA' ? 'RELIANCE' : 'AAPL');
  
  const [passedStock, setPassedStock] = useState(initialStock);
  const { refreshInsights } = useBehavior();
  
  const [timeframe, setTimeframe] = useState('1W');
  const [decision, setDecision] = useState(null); 
  const [clickedPoint, setClickedPoint] = useState(null);
  const [showLearning, setShowLearning] = useState(false);
  
  const [isExplaining, setIsExplaining] = useState(false);
  const [aiInsight, setAiInsight] = useState(null);
  const [aiObservation, setAiObservation] = useState(null);
  const [evaluation, setEvaluation] = useState(null);
  const [isEvaluating, setIsEvaluating] = useState(false);

  // Sync with region change
  useEffect(() => {
    const newDefault = marketMode === 'INDIA' ? 'RELIANCE' : 'AAPL';
    setPassedStock(newDefault);
    setClickedPoint(null);
    setDecision(null);
    setEvaluation(null);
    setAiInsight(null);
  }, [marketMode]);

  const rawData = timeframe === '1W' 
    ? [
        { time: 'Mon', value: 2850 }, { time: 'Tue', value: 2880 }, { time: 'Wed', value: 2860 },
        { time: 'Thu', value: 2910 }, { time: 'Fri', value: 2940 }, { time: 'Sat', value: 2930 },
        { time: 'Sun', value: 2950 }
      ]
    : [
        { time: 'Week 1', value: 2700 }, { time: 'Week 2', value: 2800 }, { time: 'Week 3', value: 2750 },
        { time: 'Week 4', value: 2950 }, { time: 'Week 5', value: 3000 }, { time: 'Week 6', value: 2950 }
      ];

  const points = useMemo(() => rawData, [timeframe]);

  const [isSyncing, setIsSyncing] = useState(false);

  const handleChartClick = async (data) => {
    if (data && data.activePayload) {
      const point = data.activePayload[0].payload;
      const prevValue = points.indexOf(point) > 0 ? points[points.indexOf(point) - 1].value : point.value;
      
      setClickedPoint({
        price: point.value,
        trend: point.value >= prevValue ? 'Rising' : 'Falling',
        time: point.time
      });
      
      setIsSyncing(true);
      setShowLearning(true);
      setAiInsight(null);
      
      await new Promise(r => setTimeout(r, 600));
      setIsSyncing(false);
      
      if (currentStep === 1) nextStep();
      setTimeout(() => setShowLearning(false), 3000);
    }
  };

  const handleAiExplain = async () => {
    if (isExplaining || !clickedPoint) return;
    setIsExplaining(true);
    setIsSyncing(true);
    try {
      const result = await explainStock({
        symbol: passedStock,
        trend: clickedPoint.trend,
        action: decision || 'observing',
        lang: 'en',
        type: 'graph_point',
        metrics: { price: clickedPoint.price }
      });
      setAiInsight(result.explanation);
      setAiObservation(result.observation);
    } catch (err) {
      setAiInsight("Mentor sync required. Log in to access live market guidance.");
    } finally {
      setIsExplaining(false);
      setIsSyncing(false);
    }
  };

  const handleDecision = async (act) => {
    if (isEvaluating || !clickedPoint) return;
    setIsEvaluating(true);
    setDecision(act);
    try {
      const evalResult = await evaluateDecision({
        symbol: passedStock,
        action: (act || "").toUpperCase(),
        price: clickedPoint.price,
        isPositive: clickedPoint.trend === 'Rising'
      });

      await trackDecision({
        symbol: passedStock,
        action: (act || "").toUpperCase() === 'BUY' ? 'BUY' : 'SKIP',
        price: clickedPoint.price,
        market: marketCode,
        timestamp: new Date().toISOString()
      });

      refreshInsights();
      setEvaluation(evalResult);
      if (currentStep === 2) nextStep();
    } catch (err) {
      setEvaluation({ outcome: 'neutral', message: "Decision logged locally." });
    } finally {
      setIsEvaluating(false);
    }
  };

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="max-w-6xl mx-auto p-6 sm:p-10 space-y-10 pb-32">
      
      <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-6">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h1 className="text-5xl font-black text-white tracking-tighter uppercase">{passedStock}</h1>
            <span className="px-2 py-1 rounded-lg bg-blue-500/10 text-blue-400 text-[10px] font-black border border-blue-500/20">{marketMode} MARKET</span>
          </div>
          <p className="text-2xl font-black text-white">{currencySymbol}{clickedPoint ? clickedPoint.price : points[points.length-1].value}</p>
        </div>

        <div className="flex gap-1 bg-white/5 p-1 rounded-2xl border border-white/5">
          {['1W', '1M'].map((tf) => (
            <button key={tf} onClick={() => { setTimeframe(tf); setClickedPoint(null); setDecision(null); setEvaluation(null); }} className={`px-6 py-2 rounded-xl text-xs font-black transition-all ${timeframe === tf ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'}`}>{tf}</button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-10">
        <div className="lg:col-span-8 space-y-8">
          
          <div className={`p-8 rounded-[3rem] bg-slate-900/40 border border-white/5 relative overflow-hidden transition-all duration-500 ${currentStep === 1 ? 'z-[201] ring-2 ring-blue-500/50 shadow-[0_0_50px_rgba(59,130,246,0.2)]' : ''}`}>
            <div className="absolute top-6 left-8 z-10 flex items-center gap-4">
               <p className="text-[10px] font-black text-blue-400 uppercase tracking-[0.2em] flex items-center gap-2">
                 <MousePointer2 size={12} /> Tap chart to analyze moment
               </p>
               {clickedPoint && (
                 <motion.span initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} className={`px-3 py-1 ${isSyncing ? 'bg-amber-600 animate-pulse' : 'bg-blue-600'} text-white text-[10px] font-black rounded-lg uppercase tracking-widest shadow-lg`}>
                   {isSyncing ? 'Syncing...' : `LOCKED: ${currencySymbol}${clickedPoint.price}`}
                 </motion.span>
               )}
            </div>

            <div className="h-[350px] w-full mt-6">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={points} onClick={handleChartClick}>
                  <defs>
                    <linearGradient id="liveGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/>
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} strokeOpacity={0.5} />
                  <XAxis dataKey="time" hide />
                  <YAxis hide domain={['dataMin - 100', 'dataMax + 100']} />
                  <Tooltip 
                    content={() => null}
                    cursor={{ stroke: '#3b82f6', strokeWidth: 2, strokeDasharray: '4 4' }}
                  />
                  <Area 
                    type="monotone" 
                    dataKey="value" 
                    stroke="#3b82f6" 
                    strokeWidth={4} 
                    fillOpacity={1} 
                    fill="url(#liveGradient)"
                    animationDuration={1500}
                  />
                </AreaChart>
              </ResponsiveContainer>

              <AnimatePresence>
                {showLearning && (
                  <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="absolute bottom-10 left-1/2 -translate-x-1/2 z-40 px-6 py-3 bg-blue-600 text-white text-[10px] font-black uppercase tracking-widest rounded-full shadow-2xl flex items-center gap-2 whitespace-nowrap border border-white/20">
                    <Zap size={14} fill="currentColor" /> Pattern Locked
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>

          <div className="space-y-4">
             <div className="flex items-center justify-between px-2">
                <h3 className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Cognitive Analysis</h3>
                <button 
                  onClick={handleAiExplain}
                  disabled={isExplaining || !clickedPoint}
                  className="flex items-center gap-2 px-6 py-3 bg-indigo-600 hover:bg-indigo-500 text-white rounded-2xl text-[10px] font-black uppercase tracking-[0.2em] transition-all disabled:opacity-30 shadow-lg shadow-indigo-900/20"
                >
                  {isExplaining ? <Loader2 size={12} className="animate-spin" /> : <Bot size={14} />}
                  {isExplaining ? "Decoding..." : "Explain This Point"}
                </button>
             </div>

             <AnimatePresence mode="wait">
               {isSyncing ? (
                 <motion.div key="syncing" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="p-16 text-center bg-slate-900/40 border border-white/5 rounded-[3rem] shadow-inner">
                    <Loader2 size={32} className="animate-spin text-blue-500 mx-auto mb-4" />
                    <p className="text-[10px] text-blue-400 font-black uppercase tracking-[0.4em] animate-pulse">Syncing with Market Pulse...</p>
                 </motion.div>
               ) : aiInsight ? (
                 <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="p-10 rounded-[3rem] bg-[#020617] border border-blue-500/20 relative overflow-hidden shadow-2xl">
                    <div className="absolute top-0 right-0 p-8 opacity-5"><Sparkles size={120} className="text-blue-400" /></div>
                    <div className="flex items-center gap-3 mb-6">
                       <div className="p-2 bg-blue-500/10 rounded-xl"><Bot size={20} className="text-blue-400" /></div>
                       <span className="text-[10px] font-black text-blue-400 uppercase tracking-[0.4em]">Market Mentor</span>
                    </div>
                    <div className="space-y-6">
                      <p className="text-xl font-bold text-blue-50 leading-relaxed italic italic">"{aiInsight}"</p>
                      
                      {aiObservation && (
                        <div className="pt-6 border-t border-white/5">
                           <div className="flex items-start gap-4 text-blue-300">
                              <span className="text-2xl">👀</span>
                              <div>
                                <p className="text-[10px] font-black uppercase tracking-[0.3em] mb-2 text-slate-500">
                                  {(aiObservation || "").toLowerCase().includes('usually') ? 'Macro Prediction' : 'Technical observation'}
                                </p>
                                <p className="text-sm font-bold leading-relaxed text-blue-100">{aiObservation}</p>
                              </div>
                           </div>
                        </div>
                      )}
                    </div>
                 </motion.div>
               ) : !clickedPoint ? (
                 <div className="p-16 text-center border-2 border-dashed border-white/5 rounded-[3rem]">
                    <p className="text-xs text-slate-600 font-black uppercase tracking-[0.3em]">Tap the chart to unlock AI analysis</p>
                 </div>
               ) : null}
             </AnimatePresence>
          </div>
        </div>

        <div className="lg:col-span-4">
          <div className={`p-10 rounded-[3rem] bg-slate-900 border border-white/10 sticky top-10 space-y-10 transition-all duration-500 shadow-2xl ${currentStep === 2 ? 'z-[201] ring-2 ring-blue-500/50' : ''}`}>
            <div className="space-y-2">
              <h3 className="text-2xl font-black text-white tracking-tighter uppercase">Your Move</h3>
              <p className="text-slate-500 text-sm font-bold uppercase tracking-widest text-[10px]">Step into the market flow</p>
            </div>

            <AnimatePresence mode="wait">
              {!evaluation ? (
                <div className="space-y-4">
                  <button onClick={() => handleDecision('buy')} disabled={!clickedPoint || isEvaluating} className="w-full py-5 bg-blue-600 disabled:bg-slate-800 text-white font-black rounded-2xl uppercase tracking-[0.2em] text-xs shadow-xl hover:bg-blue-500 transition-all flex items-center justify-center gap-3">
                    {isEvaluating && <Loader2 size={16} className="animate-spin" />}
                    Buy at {currencySymbol}{clickedPoint ? clickedPoint.price : '...'}
                  </button>
                  <button onClick={() => handleDecision('wait')} disabled={!clickedPoint || isEvaluating} className="w-full py-5 bg-white/5 border border-white/10 disabled:border-white/5 text-white font-black rounded-2xl uppercase tracking-[0.2em] text-xs hover:bg-white/10 transition-all">Wait and Watch</button>
                </div>
              ) : (
                <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} className="space-y-8">
                  <div className={`p-8 rounded-[2rem] border ${evaluation.outcome === 'good' ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400' : evaluation.outcome === 'risky' ? 'bg-rose-500/10 border-rose-500/20 text-rose-400' : 'bg-amber-500/10 border-amber-500/20 text-amber-400'}`}>
                    <div className="w-12 h-12 bg-white/10 rounded-2xl flex items-center justify-center mb-6"><Info size={24}/></div>
                    <p className="text-[10px] font-black uppercase tracking-[0.3em] mb-3 opacity-60">Mentor Response</p>
                    <p className="text-xl font-bold leading-tight mb-4 text-white">"{evaluation.message}"</p>
                  </div>
                  <button onClick={() => { setDecision(null); setEvaluation(null); setClickedPoint(null); setAiInsight(null); }} className="w-full py-5 bg-white/10 text-white rounded-2xl text-[10px] font-black uppercase tracking-[0.3em] transition-all flex items-center justify-center gap-2 hover:bg-white/20">Analyze New Moment <ArrowRight size={14}/></button>
                </motion.div>
              )}
            </AnimatePresence>

            <div className="pt-10 border-t border-white/5 space-y-4">
              <p className="text-[10px] font-black text-slate-600 uppercase tracking-[0.3em]">Institutional flow</p>
              <div className="bg-blue-600/5 p-6 rounded-2xl border border-blue-500/10"><p className="text-[11px] text-blue-300 font-bold leading-relaxed italic">"Markets are driven by psychology, not math. You are learning to read the crowd."</p></div>
            </div>
          </div>
        </div>
      </div>

      <GuideOverlay 
        step={currentStep}
        totalSteps={3}
        active={currentStep < 4}
        onDismiss={() => setStep(4)}
        title={
          currentStep === 1 ? "Module 1: Observation" :
          currentStep === 2 ? "Module 2: Execution" :
          "Module 3: Integration"
        }
        content={
          currentStep === 1 ? "Tap anywhere on the cinematic chart to freeze a market moment and analyze it." :
          currentStep === 2 ? "Now, commit to a decision. Buying or Watching reveals your behavioral bias." :
          "Scroll to decode the Mentor's insight. You've just integrated a new market pattern."
        }
      />
    </motion.div>
  );
}
