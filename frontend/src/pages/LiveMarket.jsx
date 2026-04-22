import { useState, useRef, useMemo, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, TrendingDown, Activity, PieChart, BarChart3, ChevronRight, MousePointer2, Zap, Bot, Sparkles, Loader2, Info, ArrowRight } from 'lucide-react';
import { useLocation } from 'react-router-dom';
import { useMarket } from '../context/MarketContext';
import { explainStock, evaluateDecision, trackDecision } from '../services/api';
import { useBehavior } from '../context/BehaviorContext';
import { useGuide } from '../hooks/useGuide';
import GuideOverlay from '../components/GuideOverlay';

export default function LiveMarket() {
  const { marketMode, marketCode, currencySymbol } = useMarket();
  const { currentStep, nextStep, setStep } = useGuide(1);
  const location = useLocation();
  const initialStock = location.state?.selectedStock || (marketMode === 'INDIA' ? 'RELIANCE' : 'AAPL');
  
  const [passedStock, setPassedStock] = useState(initialStock);
  const { refreshInsights } = useBehavior();
  
  const [timeframe, setTimeframe] = useState('1W');
  const [decision, setDecision] = useState(null); 
  const [hoveredPoint, setHoveredPoint] = useState(null);
  const [clickedPoint, setClickedPoint] = useState(null);
  const [showLearning, setShowLearning] = useState(false);
  
  // New polished states
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
  
  const svgRef = useRef(null);

  const learningTips = [
    "More buyers = price goes up 🚀",
    "Selling pressure = price drops 📉",
    "Markets move in waves, not straight lines.",
    "Higher volume often confirms the trend."
  ];

  const randomTip = useMemo(() => learningTips[Math.floor(Math.random() * (learningTips?.length || 1))], [clickedPoint]);

  const points = timeframe === '1W' 
    ? [2850, 2880, 2860, 2910, 2940, 2930, 2950]
    : [2700, 2750, 2800, 2780, 2850, 2900, 2950, 2920, 2980, 3000, 2950];

  const max = Math.max(...((points?.length > 0 ? points : [0]) || [0]));
  const min = Math.min(...((points?.length > 0 ? points : [0]) || [0]));
  const range = (max - min) || 1;
  const width = 500;
  const height = 150;

  const getCoordinates = (index, value) => {
    const x = (index / ((points?.length - 1) || 1)) * width;
    const y = height - ((value - min) / range) * height;
    return { x, y };
  };

  const svgPath = (points || []).map((p, i) => {
    const { x, y } = getCoordinates(i, p);
    return `${i === 0 ? 'M' : 'L'} ${x} ${y}`;
  }).join(' ');

  const handleMouseMove = (e) => {
    if (!svgRef.current || !points || points.length === 0) return;
    const rect = svgRef.current.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * width;
    const index = Math.round((x / width) * (points.length - 1));
    const safeIndex = Math.max(0, Math.min(points.length - 1, index));
    const price = points[safeIndex];
    const prevPrice = safeIndex > 0 ? points[safeIndex - 1] : price;
    setHoveredPoint({
      index: safeIndex,
      price,
      trend: price >= prevPrice ? 'Rising' : 'Falling',
      ...getCoordinates(safeIndex, price)
    });
  };

  const [isSyncing, setIsSyncing] = useState(false);

  const handleGraphClick = async () => {
    if (hoveredPoint) {
      setClickedPoint(hoveredPoint);
      setIsSyncing(true);
      setShowLearning(true);
      setAiInsight(null); // Clear previous AI insight for new point
      
      // Simulate magnetic lock-on feel
      await new Promise(r => setTimeout(resolve => r(), 600));
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
      // 1. Emotive Evaluation
      const evalResult = await evaluateDecision({
        symbol: passedStock,
        action: (act || "").toUpperCase(),
        price: clickedPoint.price,
        isPositive: clickedPoint.trend === 'Rising'
      });

      // 2. Persistent Tracking
      await trackDecision({
        symbol: passedStock,
        action: (act || "").toUpperCase() === 'BUY' ? 'BUY' : 'SKIP',
        price: clickedPoint.price,
        market: marketCode,
        timestamp: new Date().toISOString()
      });

      // 3. Sync User Insights
      refreshInsights();

      setEvaluation(evalResult);
      if (currentStep === 2) nextStep();
    } catch (err) {
      console.error("Decision evaluation/tracking failed:", err);
      setEvaluation({ outcome: 'neutral', message: "Decision logged locally. Log in to sync with your AI profile." });
    } finally {
      setIsEvaluating(false);
    }
  };

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="max-w-6xl mx-auto p-6 sm:p-10 space-y-10 pb-32">
      
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-6">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h1 className="text-5xl font-black text-white tracking-tighter">{passedStock}</h1>
            <span className="px-2 py-1 rounded-lg bg-blue-500/10 text-blue-400 text-[10px] font-black border border-blue-500/20">{marketMode} MARKET</span>
          </div>
          <p className="text-2xl font-black text-white">{currencySymbol}{clickedPoint ? clickedPoint.price : points[points.length-1]}</p>
        </div>

        <div className="flex gap-1 bg-white/5 p-1 rounded-2xl border border-white/5">
          {['1W', '1M'].map((tf) => (
            <button key={tf} onClick={() => { setTimeframe(tf); setClickedPoint(null); setDecision(null); setEvaluation(null); }} className={`px-6 py-2 rounded-xl text-xs font-black transition-all ${timeframe === tf ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'}`}>{tf}</button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-10">
        <div className="lg:col-span-8 space-y-8">
          
          {/* Interactive Graph */}
          <div className={`p-8 rounded-[2.5rem] bg-slate-900/40 border border-white/5 relative overflow-hidden group cursor-crosshair transition-all duration-500 ${currentStep === 1 ? 'z-[201] ring-2 ring-blue-500/50 shadow-[0_0_50px_rgba(59,130,246,0.2)]' : ''}`}>
            <div className="absolute top-6 left-8 z-10 flex items-center gap-4">
               <p className="text-[10px] font-black text-blue-400 uppercase tracking-[0.2em] flex items-center gap-2">
                 <MousePointer2 size={12} /> Click graph to select point
               </p>
               {clickedPoint && (
                 <motion.span initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} className={`px-3 py-1 ${isSyncing ? 'bg-amber-600 animate-pulse' : 'bg-blue-600'} text-white text-[10px] font-black rounded-lg uppercase tracking-widest shadow-lg`}>
                   {isSyncing ? 'Syncing Point...' : `POINT: ${currencySymbol}${clickedPoint.price}`}
                 </motion.span>
               )}
            </div>

            <div className="h-[280px] w-full mt-6 relative">
              <svg ref={svgRef} width="100%" height="100%" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" onMouseMove={handleMouseMove} onMouseLeave={() => setHoveredPoint(null)} onClick={handleGraphClick}>
                {hoveredPoint && <line x1={hoveredPoint.x} y1="0" x2={hoveredPoint.x} y2={height} stroke="rgba(59, 130, 246, 0.3)" strokeWidth="1" />}
                <motion.path initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 1.5 }} d={svgPath} fill="none" stroke="#3b82f6" strokeWidth="4" strokeLinecap="round" />
                {hoveredPoint && <circle cx={hoveredPoint.x} cy={hoveredPoint.y} r="6" fill="#3b82f6" className="drop-shadow-[0_0_8px_rgba(59,130,246,0.8)]" />}
                {clickedPoint && <circle cx={clickedPoint.x} cy={clickedPoint.y} r="10" fill="none" stroke="#fff" strokeWidth="2" />}
              </svg>

              <AnimatePresence>
                {hoveredPoint && (
                  <motion.div initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }} style={{ left: `${(hoveredPoint.x / width) * 100}%`, top: `${(hoveredPoint.y / height) * 100}%` }} className="absolute z-20 pointer-events-none -translate-x-1/2 -translate-y-12 px-3 py-1 bg-white text-slate-900 text-[10px] font-black rounded-lg shadow-xl whitespace-nowrap">₹{hoveredPoint.price}</motion.div>
                )}
              </AnimatePresence>

              <AnimatePresence>
                {showLearning && (
                  <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="absolute bottom-4 left-1/2 -translate-x-1/2 z-40 px-6 py-2 bg-blue-600 text-white text-[10px] font-black uppercase tracking-widest rounded-full shadow-2xl flex items-center gap-2 whitespace-nowrap"><Zap size={14} fill="currentColor" /> {randomTip}</motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>

          {/* AI Explanation Section */}
          <div className="space-y-4">
             <div className="flex items-center justify-between px-2">
                <h3 className="text-sm font-black text-slate-500 uppercase tracking-widest">Market Logic</h3>
                <button 
                  onClick={handleAiExplain}
                  disabled={isExplaining || !clickedPoint}
                  className="flex items-center gap-2 px-4 py-2 bg-indigo-600/10 hover:bg-indigo-600/20 text-indigo-400 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all disabled:opacity-30 disabled:cursor-not-allowed"
                >
                  {isExplaining ? <Loader2 size={12} className="animate-spin" /> : <Bot size={12} />}
                  {isExplaining ? "Analyzing..." : "Explain This Point 🤖"}
                </button>
             </div>

             <AnimatePresence mode="wait">
               {isSyncing ? (
                 <motion.div key="syncing" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="p-10 text-center bg-indigo-500/5 border border-dashed border-indigo-500/20 rounded-3xl">
                    <Loader2 size={24} className="animate-spin text-indigo-500 mx-auto mb-4" />
                    <p className="text-[10px] text-indigo-400 font-black uppercase tracking-[0.2em]">Syncing with Market Pulse...</p>
                 </motion.div>
               ) : aiInsight ? (
                 <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="p-6 rounded-3xl bg-indigo-500/5 border border-indigo-500/10 relative overflow-hidden">
                    <div className="absolute top-0 right-0 p-4 opacity-5"><Sparkles size={60} className="text-indigo-400" /></div>
                    <div className="flex items-center gap-2 mb-3">
                       <Bot size={14} className="text-indigo-400" />
                       <span className="text-[10px] font-black text-indigo-400 uppercase tracking-widest">Market Mentor</span>
                    </div>
                    <div className="space-y-4">
                      <p className="text-sm text-indigo-100 font-medium leading-relaxed max-w-2xl">{aiInsight}</p>
                      
                      {aiObservation && (
                        <div className="pt-3 border-t border-indigo-500/10">
                           <div className="flex items-start gap-2 text-indigo-300">
                              <span className="text-lg">👀</span>
                              <div>
                                <p className="text-[9px] font-black uppercase tracking-[0.2em] mb-1">
                                  {(aiObservation || "").toLowerCase().includes('usually') || (aiInsight || "").toLowerCase().includes('happened') ? 'Next Step:' : 'Watch this:'}
                                </p>
                                <p className="text-xs font-bold leading-relaxed">{aiObservation}</p>
                              </div>
                           </div>
                        </div>
                      )}

                    </div>
                 </motion.div>
               ) : !clickedPoint ? (
                 <div className="p-10 text-center border-2 border-dashed border-white/5 rounded-3xl">
                    <p className="text-xs text-slate-600 font-bold uppercase tracking-widest">Select a point on the graph to unlock AI analysis</p>
                 </div>
               ) : null}
             </AnimatePresence>
          </div>

          {/* Performance Data */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-4">
            {[
              { label: "Revenue", value: "↑ 12%", sub: "Year over Year" },
              { label: "Stability", value: "High", sub: "Low Volatility" },
              { label: "Market Interest", value: "Strong", sub: "Bullish Sentiment" }
            ].map((n, i) => (
              <div key={i} className="p-6 rounded-3xl bg-white/5 border border-white/5"><p className="text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">{n?.label}</p><p className="text-lg font-black text-white">{n?.value}</p><p className="text-[9px] font-bold text-slate-600">{n?.sub}</p></div>
            ))}
          </div>
        </div>

        {/* Action Sidebar */}
        <div className="lg:col-span-4">
          <div className={`p-8 rounded-[2.5rem] bg-slate-900 border border-white/10 sticky top-10 space-y-8 transition-all duration-500 ${currentStep === 2 ? 'z-[201] ring-2 ring-blue-500/50 shadow-[0_0_50px_rgba(59,130,246,0.2)]' : ''}`}>
            <div>
              <h3 className="text-xl font-black text-white tracking-tight mb-2">Decision Point</h3>
              <p className="text-slate-500 text-sm font-medium">Every click is a lesson. What's your move?</p>
            </div>

            <AnimatePresence mode="wait">
              {!evaluation ? (
                <div className="space-y-4">
                  <button onClick={() => handleDecision('buy')} disabled={!clickedPoint || isEvaluating} className="w-full py-5 bg-blue-600 disabled:bg-slate-800 text-white font-black rounded-2xl uppercase tracking-[0.2em] text-[10px] shadow-xl hover:bg-blue-500 transition-all flex items-center justify-center gap-2">
                    {isEvaluating && <Loader2 size={14} className="animate-spin" />}
                    Buy at {currencySymbol}{clickedPoint ? clickedPoint.price : '...'}
                  </button>
                  <button onClick={() => handleDecision('wait')} disabled={!clickedPoint || isEvaluating} className="w-full py-5 bg-white/5 border border-white/10 disabled:border-white/5 text-white font-black rounded-2xl uppercase tracking-[0.2em] text-[10px] hover:bg-white/10 transition-all">Wait and Watch</button>
                  {!clickedPoint && <p className="text-center text-[9px] font-black text-blue-500 animate-pulse uppercase tracking-widest pt-2">Select a point on graph first</p>}
                </div>
              ) : (
                <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} className="space-y-6">
                  <div className={`p-6 rounded-3xl border ${evaluation.outcome === 'good' ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400' : evaluation.outcome === 'risky' ? 'bg-rose-500/10 border-rose-500/20 text-rose-400' : 'bg-amber-500/10 border-amber-500/20 text-amber-400'}`}>
                    <p className="text-xs font-black uppercase tracking-[0.2em] mb-3 opacity-60">Result Analysis</p>
                    <p className="text-lg font-black leading-tight mb-2">{evaluation.message}</p>
                    <div className="flex items-center gap-2 pt-2"><Info size={12}/> <span className="text-[10px] font-bold uppercase tracking-wide">Lesson Learned</span></div>
                  </div>
                  <button onClick={() => { setDecision(null); setEvaluation(null); setClickedPoint(null); setAiInsight(null); }} className="w-full py-4 bg-white/5 text-slate-500 hover:text-white rounded-2xl text-[10px] font-black uppercase tracking-widest transition-all flex items-center justify-center gap-2">Explore New Point <ArrowRight size={14}/></button>
                </motion.div>
              )}
            </AnimatePresence>

            <div className="pt-8 border-t border-white/5 space-y-4">
              <p className="text-[10px] font-black text-slate-600 uppercase tracking-[0.2em]">User Pattern</p>
              <div className="bg-blue-600/5 p-4 rounded-2xl border border-blue-500/10"><p className="text-[10px] text-blue-300 font-bold leading-relaxed uppercase">"Most users who buy during rising trends tend to have higher overall confidence scores."</p></div>
            </div>
          </div>
        </div>
      </div>

      {/* Reusable Guide Overlay */}
      <GuideOverlay 
        step={currentStep}
        totalSteps={3}
        active={currentStep < 4}
        onDismiss={() => setStep(4)}
        title={
          currentStep === 1 ? "Step 1: Explore" :
          currentStep === 2 ? "Step 2: Decide" :
          "Step 3: Analyze"
        }
        content={
          currentStep === 1 ? "Click any point on the graph to analyze a specific market moment." :
          currentStep === 2 ? "Now, make a move! Buying or Waiting reveals the Mentor's secret logic." :
          "Scroll down to see the AI analysis. The Market Mentor has decoded this move for you."
        }
      />
    </motion.div>
  );
}
