import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { getUserPortfolios, getHoldings, executeTrade, updatePortfolioBalance } from '../services/api';

const TradingContext = createContext();

export function TradingProvider({ children }) {
  const [balance, setBalance] = useState(100000);
  const [portfolio, setPortfolio] = useState([]);
  const [activePortfolioId, setActivePortfolioId] = useState(null);
  const [lastAction, setLastAction] = useState(null);
  const [decisions, setDecisions] = useState([]);
  const [gameImpact, setGameImpact] = useState({ amount: 0, type: null, timestamp: null });
  const [loading, setLoading] = useState(true);

  const refreshData = useCallback(async () => {
    const token = localStorage.getItem('token');
    if (!token) {
      console.log("No token → skipping Trading API calls");
      setLoading(false);
      return;
    }
    
    try {
      const portfoliosResponse = await getUserPortfolios();
      const portfolioList = Array.isArray(portfoliosResponse) ? portfoliosResponse : (portfoliosResponse?.result || []);
      
      if (portfolioList.length > 0) {
        const primary = portfolioList[0];
        setActivePortfolioId(primary.portfolioId);
        setBalance(primary.balance || 100000);
        
        const holdingsResponse = await getHoldings(primary.portfolioId);
        const holdingsList = Array.isArray(holdingsResponse) ? holdingsResponse : (holdingsResponse?.result || []);
        
        // Map backend holdings to frontend format
        const mappedPortfolio = (holdingsList || []).map(h => ({
          symbol: h?.symbol,
          name: h?.companyName || h?.symbol,
          buyPrice: h?.buyPrice,
          invested: (h?.buyPrice || 0) * (h?.quantity || 0),
          quantity: h?.quantity,
          market: h?.market || ((h?.symbol || "").includes('.') ? 'INDIA' : 'US'),
          currentValue: (h?.currentPrice || 0) * (h?.quantity || 0),
          gainVal: h?.gain,
          gainPct: h?.gainPercentage
        }));
        
        setPortfolio(mappedPortfolio);
      }
    } catch (error) {
      console.error("Failed to fetch trading data from backend", error);
      // Fallback to localStorage if backend fails
      const saved = localStorage.getItem('finplay_trading');
      if (saved) {
        const { balance: b, portfolio: p } = JSON.parse(saved);
        setBalance(b);
        setPortfolio(p);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshData();
  }, [refreshData]);

  // Persist decisions and game impact locally as they are behavioral
  useEffect(() => {
    const saved = localStorage.getItem('finplay_behavior');
    if (saved) {
      try {
        const { decisions: d, gameImpact: gi } = JSON.parse(saved);
        if (d) setDecisions(d);
        if (gi) setGameImpact(gi);
      } catch (e) {
        console.error("Failed to parse behavior data", e);
      }
    }
  }, []);

  useEffect(() => {
    localStorage.setItem('finplay_behavior', JSON.stringify({ decisions, gameImpact }));
  }, [decisions, gameImpact]);

  const recordGameResult = useCallback(async (amount, type) => {
    const impactAmount = parseFloat(amount) || 0;
    setGameImpact({ amount: impactAmount, type, timestamp: Date.now() });
    
    if ((type === 'gain' || type === 'loss') && activePortfolioId) {
      try {
        await updatePortfolioBalance(activePortfolioId, impactAmount);
        setBalance(prev => prev + impactAmount);
      } catch (err) {
        console.error("Failed to sync game result to backend", err);
        setBalance(prev => prev + impactAmount); // Optimistic update
      }
    }
  }, [activePortfolioId]);

  const recordDecision = useCallback((decision) => {
    setDecisions(prev => [{ ...decision, timestamp: Date.now() }, ...prev].slice(0, 50));
  }, []);

  const executeBuy = useCallback(async (stock, quantity) => {
    const qty = Math.floor(parseInt(quantity, 10));
    if (isNaN(qty) || qty <= 0 || !activePortfolioId || !stock) {
      return { success: false, error: "Invalid quantity or portfolio" };
    }

    const price = typeof stock?.price === 'string' ? parseFloat(stock?.price.replace(/,/g, '')) : (stock?.price || 0);
    
    try {
      const response = await executeTrade({
        portfolioId: activePortfolioId,
        symbol: stock?.symbol,
        quantity: qty,
        price: price,
        type: 'BUY'
      });
      
      setLastAction({ type: 'BUY', symbol: stock?.symbol, timestamp: Date.now() });
      await refreshData();
      return { success: true, data: response };
    } catch (err) {
      console.error("Trade failed:", err);
      return { success: false, error: err?.response?.data?.message || err?.message || "Trade failed" };
    }
  }, [activePortfolioId, refreshData]);

  const executeSell = useCallback(async (symbol, currentValue, quantity) => {
    if (!activePortfolioId) return { success: false, error: "No active portfolio" };
    
    try {
      // Find the holding to get the current quantity if not passed
      const holding = portfolio.find(p => p.symbol === symbol);
      const sellQty = quantity || holding?.quantity || 1;
      const sellPrice = currentValue / sellQty;

      const response = await executeTrade({
        portfolioId: activePortfolioId,
        symbol: symbol,
        quantity: sellQty,
        price: sellPrice,
        type: 'SELL'
      });
      
      setLastAction({ type: 'SELL', symbol, timestamp: Date.now() });
      await refreshData();
      return { success: true, data: response };
    } catch (err) {
      console.error("Trade failed:", err);
      return { success: false, error: err?.response?.data?.message || err?.message || "Trade failed" };
    }
  }, [activePortfolioId, portfolio, refreshData]);

  const contextValue = useMemo(() => ({
    balance, 
    portfolio, 
    lastAction, 
    decisions, 
    gameImpact,
    loading,
    executeBuy, 
    executeSell, 
    recordDecision,
    recordGameResult,
    refreshData
  }), [
    balance, 
    portfolio, 
    lastAction, 
    decisions, 
    gameImpact,
    loading,
    executeBuy, 
    executeSell, 
    recordDecision,
    recordGameResult,
    refreshData
  ]);

  return (
    <TradingContext.Provider value={contextValue}>
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
