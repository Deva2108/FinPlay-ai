import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { 
  getUserPortfolios, 
  getHoldings, 
  executeTrade, 
  getUserInsights, 
  trackDecision as apiTrackDecision 
} from '../services/api';

const TradingContext = createContext();

export function TradingProvider({ children }) {
  const [balance, setBalance] = useState(100000);
  const [portfolio, setPortfolio] = useState([]);
  const [activePortfolioId, setActivePortfolioId] = useState(null);
  const [lastAction, setLastAction] = useState(null);
  const [decisions, setDecisions] = useState([]);
  const [missedOpportunities, setMissedOpportunities] = useState([]);
  const [userInsights, setUserInsights] = useState({
    behaviorType: 'neutral',
    insightMessage: 'Start making decisions to unlock behavioral analysis.'
  });
  const [gameImpact, setGameImpact] = useState({ amount: 0, type: null, timestamp: null });
  const [loading, setLoading] = useState(true);

  const refreshInsights = useCallback(async () => {
    const token = localStorage.getItem('token');
    if (!token) return;
    try {
      const data = await getUserInsights();
      if (data?.data) setUserInsights(data.data);
      else if (data && !data.success) setUserInsights(data); // Fallback for unwrapped
    } catch (err) {
      console.error("Failed to fetch behavior insights", err);
    }
  }, []);

  const refreshData = useCallback(async () => {
    const token = localStorage.getItem('token');
    if (!token) {
      setLoading(false);
      return;
    }
    
    try {
      const res = await getUserPortfolios();
      const portfolioList = res?.data;
      
      if (Array.isArray(portfolioList) && portfolioList.length > 0) {
        const primary = portfolioList[0];
        setActivePortfolioId(primary.portfolioId);
        setBalance(primary.balance || 100000);
        
        const holdingsRes = await getHoldings(primary.portfolioId);
        const holdingsList = holdingsRes?.data?.holdings || [];
        
        const mappedPortfolio = (Array.isArray(holdingsList) ? holdingsList : []).map(h => ({
          symbol: h?.symbol,
          name: h?.companyName || h?.symbol,
          buyPrice: h?.buyPrice,
          invested: (h?.buyPrice || 0) * (h?.quantity || 0),
          quantity: h?.quantity,
          market: h?.market || ((h?.symbol || "").includes('.') ? 'INDIA' : 'US'),
          currentValue: (h?.currentPrice || 0) * (h?.quantity || 0),
          gainVal: h?.gain,
          gainPct: h?.gainPercentage,
          meta: h?.meta // Carry over backend flags (isSimulated, etc)
        }));
        
        setPortfolio(mappedPortfolio);
      }
      await refreshInsights();
    } catch (error) {
      console.error("Failed to fetch trading data", error);
    } finally {
      setLoading(false);
    }
  }, [refreshInsights]);

  useEffect(() => {
    refreshData();
  }, [refreshData]);

  const recordDecision = useCallback(async (action, stock, marketMode) => {
    const newDecision = {
      stock,
      action,
      timestamp: Date.now(),
      marketMode
    };

    setDecisions(prev => [newDecision, ...prev].slice(0, 50));

    try {
      await apiTrackDecision({ 
        symbol: stock.symbol, 
        action, 
        price: typeof stock.price === 'string' ? parseFloat(stock.price.replace(/,/g, '')) : (stock.price || 0), 
        market: marketMode 
      });
      refreshInsights();
    } catch (err) {
      console.error("Failed to track decision", err);
    }
  }, [refreshInsights]);

  const addMissedOpportunity = useCallback((stock, potentialGain) => {
    setMissedOpportunities(prev => [{ stock, potentialGain, timestamp: Date.now() }, ...prev].slice(0, 10));
  }, []);

  const syncGameResult = useCallback(async (amount, type) => {
    const impactAmount = parseFloat(amount) || 0;
    setGameImpact({ amount: impactAmount, type, timestamp: Date.now() });
    
    // Logic for sync with backend removed as no valid endpoint exists for game result balance adjustment
    // and frontend must not modify balance directly.
  }, []);

  const executeBuy = useCallback(async (stock, quantity) => {
    const qty = parseFloat(quantity);
    if (isNaN(qty) || qty <= 0 || !activePortfolioId || !stock) return { success: false, error: "Invalid trade" };

    const price = typeof stock?.price === 'string' ? parseFloat(stock?.price.replace(/,/g, '')) : (stock?.price || 0);
    const cost = qty * price;
    if (cost > balance) return { success: false, error: "Insufficient balance" };

    const prevBalance = balance;
    const prevPortfolio = [...portfolio];

    // Optimistic Update
    setBalance(prev => prev - cost);
    setPortfolio(prev => {
      const existing = prev.find(p => p.symbol === stock.symbol);
      if (existing) {
        return prev.map(p => p.symbol === stock.symbol ? {
          ...p,
          quantity: p.quantity + qty,
          invested: p.invested + cost,
          currentValue: (p.currentValue || p.invested) + cost
        } : p);
      } else {
        return [...prev, {
          symbol: stock.symbol,
          name: stock.name || stock.symbol,
          buyPrice: price,
          invested: cost,
          quantity: qty,
          market: stock.market || 'US',
          currentValue: cost,
          gainVal: 0,
          gainPct: 0
        }];
      }
    });
    
    try {
      const res = await executeTrade({
        portfolioId: activePortfolioId,
        symbol: stock?.symbol,
        quantity: qty,
        price: price,
        type: 'BUY'
      });
      
      if (!res.success) throw new Error(res.message || "Trade failed");

      setLastAction({ type: 'BUY', symbol: stock?.symbol, timestamp: Date.now() });
      await refreshData();
      return { success: true };
    } catch (err) {
      setBalance(prevBalance);
      setPortfolio(prevPortfolio);
      return { success: false, error: err.message || "Trade failed" };
    }
  }, [activePortfolioId, balance, portfolio, refreshData]);

  const executeSell = useCallback(async (symbol, currentValue, quantity) => {
    if (!activePortfolioId) return { success: false, error: "No portfolio" };
    const holding = portfolio.find(p => p.symbol === symbol);
    if (!holding) return { success: false, error: "Holding not found" };

    const sellQty = quantity || holding?.quantity || 0;
    if (sellQty <= 0) return { success: false, error: "Invalid quantity" };
    
    const sellPrice = (currentValue / sellQty) || 0;
    const sellValue = currentValue || (sellPrice * sellQty);

    const prevBalance = balance;
    const prevPortfolio = [...portfolio];

    // Optimistic Update
    setBalance(prev => prev + sellValue);
    setPortfolio(prev => {
      if (holding.quantity <= sellQty) {
        return prev.filter(p => p.symbol !== symbol);
      }
      return prev.map(p => p.symbol === symbol ? {
        ...p,
        quantity: p.quantity - sellQty,
        invested: p.invested * ((p.quantity - sellQty) / p.quantity),
        currentValue: (p.currentValue || p.invested) - sellValue
      } : p);
    });

    try {
      const res = await executeTrade({
        portfolioId: activePortfolioId,
        symbol: symbol,
        quantity: sellQty,
        price: sellPrice,
        type: 'SELL'
      });

      if (!res.success) throw new Error(res.message || "Sell failed");

      setLastAction({ type: 'SELL', symbol, timestamp: Date.now() });
      await refreshData();
      return { success: true };
    } catch (err) {
      setBalance(prevBalance);
      setPortfolio(prevPortfolio);
      return { success: false, error: err.message || "Sell failed" };
    }
  }, [activePortfolioId, balance, portfolio, refreshData]);

  const contextValue = useMemo(() => ({
    balance, portfolio, lastAction, decisions, missedOpportunities, userInsights, gameImpact, loading,
    executeBuy, executeSell, recordDecision, addMissedOpportunity, recordGameResult: syncGameResult, refreshData, refreshInsights, activePortfolioId
  }), [balance, portfolio, lastAction, decisions, missedOpportunities, userInsights, gameImpact, loading, executeBuy, executeSell, recordDecision, addMissedOpportunity, syncGameResult, refreshData, refreshInsights, activePortfolioId]);

  return <TradingContext.Provider value={contextValue}>{children}</TradingContext.Provider>;
}

export function useTrading() {
  const context = useContext(TradingContext);
  if (!context) throw new Error('useTrading must be used within a TradingProvider');
  return context;
}
