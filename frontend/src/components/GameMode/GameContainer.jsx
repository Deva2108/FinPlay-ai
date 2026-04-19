import React, { useState, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Zap, Target } from 'lucide-react';
import GameStockCard from './GameStockCard';
import ActionPanel from './ActionPanel';
import OutcomeOverlay from './OutcomeOverlay';

const MOCK_STOCKS = [
  {
    symbol: 'NVDA',
    name: 'NVIDIA Corporation',
    price: '894.52',
    change: '+2.45%',
    situation: 'Data center demand reaching new peaks.',
    context: 'Earnings beat expectations with massive demand for AI chips.',
    impact: '+8.1%',
    isPositive: true,
    explanation: 'Record-breaking revenue growth made this a "must-buy" for many.'
  },
  {
    symbol: 'TSLA',
    name: 'Tesla, Inc.',
    price: '175.22',
    change: '+1.05%',
    situation: 'Global EV competition intensifying.',
    context: 'Reported a drop in sales in a key international market.',
    impact: '-4.2%',
    isPositive: false,
    explanation: 'Investors worried about demand, causing a sell-off in the short term.'
  },
  {
    symbol: 'AAPL',
    name: 'Apple Inc.',
    price: '182.41',
    change: '-0.45%',
    situation: 'AI race heating up in Silicon Valley.',
    context: 'Releasing new AI-powered features for iPhones.',
    impact: '+3.5%',
    isPositive: true,
    explanation: 'AI features boosted investor confidence, leading to a quick rally.'
  }
];

export default function GameContainer() {
  const [currentIdx, setCurrentIdx] = useState(0);
  const [isDecided, setIsDecided] = useState(false);
  const [userChoice, setUserChoice] = useState(null);
  const [score, setScore] = useState(0);
  const [feedback, setFeedback] = useState("");
  const [isGameOver, setIsGameOver] = useState(false);

  const currentStock = MOCK_STOCKS[currentIdx];

  const handleDecision = (choice) => {
    setUserChoice(choice);
    setIsDecided(true);

    const isCorrect = (choice === 'buy' && currentStock.isPositive) || (choice === 'skip' && !currentStock.isPositive);
    
    if (isCorrect) {
      setScore(prev => prev + 1);
      setFeedback("Good call!");
    } else {
      setFeedback("Risky move!");
    }
  };

  const handleNext = () => {
    if (currentIdx === MOCK_STOCKS.length - 1) {
      setIsGameOver(true);
      return;
    }
    setIsDecided(false);
    setUserChoice(null);
    setFeedback("");
    setCurrentIdx(prev => prev + 1);
  };

  useEffect(() => {
    if (isDecided && !isGameOver) {
      const timer = setTimeout(handleNext, 3500);
      return () => clearTimeout(timer);
    }
  }, [isDecided, isGameOver]);

  if (isGameOver) {
    return (
      <motion.div 
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        className="text-center p-10 bg-slate-900/60 rounded-3xl border border-blue-500/20"
      >
        <h2 className="text-3xl font-black text-white mb-4 uppercase tracking-tighter">Arena Completed!</h2>
        <p className="text-slate-400 font-bold mb-8 uppercase tracking-widest">Score: {score} / {MOCK_STOCKS.length}</p>
        <button 
          onClick={() => window.location.reload()}
          className="px-8 py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-xl font-black uppercase tracking-widest text-xs transition-all"
        >
          Try Again
        </button>
      </motion.div>
    );
  }

  return (
    <div className="max-w-xl mx-auto space-y-6">
      <div className="flex items-center justify-between px-2">
        <div className="flex items-center gap-2">
          <Zap size={18} className="text-blue-500" />
          <span className="text-xs font-black text-white uppercase tracking-widest">Layer 1: Game Mode</span>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5 bg-slate-900/50 border border-slate-800 px-3 py-1 rounded-lg">
            <Target size={12} className="text-blue-500" />
            <span className="text-[10px] font-black text-white uppercase tracking-widest">{score} Correct</span>
          </div>
          <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">
            {currentIdx + 1} / {MOCK_STOCKS.length}
          </span>
        </div>
      </div>

      <AnimatePresence mode="wait">
        {!isDecided ? (
          <motion.div
            key="question"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
          >
            <GameStockCard stock={currentStock} />
            <ActionPanel onDecision={handleDecision} />
          </motion.div>
        ) : (
          <motion.div
            key="outcome"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -20 }}
          >
            <OutcomeOverlay 
              stock={currentStock} 
              choice={userChoice} 
              feedback={feedback} 
              onNext={handleNext} 
            />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
