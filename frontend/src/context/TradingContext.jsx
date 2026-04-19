import React, { createContext, useContext, useState, useEffect } from 'react';

const TradingContext = createContext();

export function TradingProvider({ children }) {
  const [balance, setBalance] = useState(100000);
  const [portfolio, setPortfolio] = useState([]);
  const [lastAction, setLastAction] = useState(null); // { type: 'BUY' | 'SELL', symbol, timestamp }
  const [decisions, setDecisions] = useState([]); // [{ stock, choice, isCorrect, timestamp }]
  const [gameImpact, setGameImpact] = useState({ amount: 0, type: null, timestamp: null });

  useEffect(() => {
    const saved = localStorage.getItem('finplay_trading');
    if (saved) {
      try {
        const { balance: b, portfolio: p, lastAction: la, decisions: d, gameImpact: gi } = JSON.parse(saved);
        setBalance(b);
        setPortfolio(p);
        if (la) setLastAction(la);
        if (d) setDecisions(d);
        if (gi) setGameImpact(gi);
      } catch (e) {
        console.error("Failed to parse trading data", e);
      }
    }
  }, []);

  useEffect(() => {
    localStorage.setItem('finplay_trading', JSON.stringify({ balance, portfolio, lastAction, decisions, gameImpact }));
  }, [balance, portfolio, lastAction, decisions, gameImpact]);

  const recordGameResult = (amount, type) => {
    const impactAmount = parseFloat(amount) || 0;
    setGameImpact({ amount: impactAmount, type, timestamp: Date.now() });
    
    // Impact the balance for gains/losses (not missed opportunities as they are hypothetical)
    if (type === 'gain' || type === 'loss') {
      setBalance(prev => prev + impactAmount);
    }
  };

  const recordDecision = (decision) => {
    setDecisions(prev => [{ ...decision, timestamp: Date.now() }, ...prev].slice(0, 50));
  };

  const executeBuy = (stock, quantity, situation = "") => {
    const qty = Math.floor(parseInt(quantity, 10));
    if (isNaN(qty) || qty <= 0) return false;

    const price = typeof stock.price === 'string' ? parseFloat(stock.price.replace(/,/g, '')) : stock.price;
    const totalCost = price * qty;

    if (balance < totalCost) return false;

    setBalance(prev => prev - totalCost);
    setLastAction({ type: 'BUY', symbol: stock.symbol, timestamp: Date.now() });
    setPortfolio(prev => {
      const existing = prev.find(p => p.symbol === stock.symbol);
      if (existing) {
        return prev.map(p => p.symbol === stock.symbol ? {
          ...p,
          invested: p.invested + totalCost,
          quantity: Math.floor(p.quantity + qty),
          buyPrice: (p.invested + totalCost) / (Math.floor(p.quantity + qty)),
          insight: situation || p.insight
        } : p);
      }
      return [...prev, {
        symbol: stock.symbol,
        name: stock.name,
        buyPrice: price,
        invested: totalCost,
        quantity: Math.floor(qty),
        market: stock.market,
        insight: situation
      }];
    });

    return true;
  };

  const executeSell = (symbol, currentValue) => {
    setBalance(prev => prev + currentValue);
    setLastAction({ type: 'SELL', symbol, timestamp: Date.now() });
    setPortfolio(prev => prev.filter(p => p.symbol !== symbol));
    return true;
  };

  return (
    <TradingContext.Provider value={{ 
      balance, 
      portfolio, 
      lastAction, 
      decisions, 
      gameImpact,
      executeBuy, 
      executeSell, 
      recordDecision,
      recordGameResult 
    }}>
      {children}
    </TradingContext.Provider>
  );
}

export function useTrading() {
  const context = useContext(TradingContext);
  if (!context) {
    throw new Error('useTrading must be used within a TradingProvider');
  }
  return context;
}
