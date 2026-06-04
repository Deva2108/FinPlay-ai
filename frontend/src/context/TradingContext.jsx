import React, { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { 
  getUserPortfolios, 
  getHoldings, 
  executeTrade, 
  getUserInsights, 
  syncAll,
  trackDecision as apiTrackDecision 
} from '../services/api';

const TradingContext = createContext();
const TradeStateContext = createContext();
const TradeActionContext = createContext();
// Granular leaf context — consumers re-render ONLY when balance changes,
// not when portfolio, decisions, or any other state slice changes.
const BalanceContext = createContext();

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
  const [error, setError] = useState(null);

  // Latched on logout. Once true, every async writeback below bails before its
  // setState, so an in-flight hydration/insight fetch — or a wake-retry that
  // resolves during the hard-redirect unload window — can never repopulate the
  // previous user's data into a now-logged-out tree. A fresh mount (after the
  // hard reload) starts with this back at false.
  const loggedOutRef = useRef(false);

  const refreshInsights = useCallback(async () => {
    const token = localStorage.getItem('token');
    if (!token) return;
    try {
      const data = await getUserInsights();
      if (loggedOutRef.current) return;
      if (data?.data) setUserInsights(data.data);
      else if (data && !data.success) setUserInsights(data); // Fallback for unwrapped
    } catch (err) {
      console.error("Failed to fetch behavior insights", err);
    }
  }, []);

  const refreshData = useCallback(async () => {
    const token = localStorage.getItem('token');
    // No token = nothing to load. The onboarding flag is checked at the route
    // level (App.tsx) for redirects; it should NOT also gate hydration here,
    // because a returning user in a fresh browser (incognito / cleared cookies
    // / different device) has the JWT but no localStorage flag — gating
    // hydration on that left them staring at an empty Dashboard with no error.
    if (!token) {
      setLoading(false);
      return;
    }

    setError(null);
    try {
      // Critical hydration only — portfolios + holdings + balance. Behavior
      // insights are fetched separately below so first paint isn't blocked
      // on the (slower, cache-miss-prone) insight read.
      const res = await syncAll({ light: true });
      if (loggedOutRef.current) return;
      const data = res?.data;

      if (data) {
        if (data.portfolios && data.portfolios.length > 0) {
           setActivePortfolioId(data.portfolios[0].portfolioId);
        }
        setBalance(data.totalBalance || 100000);

        const holdingsList = data.holdings?.holdings || [];
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
          meta: h?.meta
        }));

        setPortfolio(mappedPortfolio);
      }
    } catch (error) {
      console.error("Failed to sync application data", error);
      if (!loggedOutRef.current) setError('Failed to load portfolio data.');
    } finally {
      // Unblock first paint immediately — insights backfill happens below
      // without holding the loading flag.
      if (!loggedOutRef.current) setLoading(false);
    }

    // Deferred insights fetch — fires after first paint, no await on the
    // critical hydration path. Failure here is non-fatal; userInsights keeps
    // its default state. requestIdleCallback when available, else microtask.
    const deferInsights = () => { refreshInsights().catch(() => {}); };
    if (typeof window !== 'undefined' && typeof window.requestIdleCallback === 'function') {
      window.requestIdleCallback(deferInsights, { timeout: 1500 });
    } else {
      setTimeout(deferInsights, 0);
    }
  }, [refreshInsights]);

  useEffect(() => {
    refreshData();
  }, [refreshData]);

  // Hydration watchdog: never let the app sit behind a stuck `loading` flag if
  // the initial sync stalls (cold backend, slow network, transient-retry/wake
  // chain). Cap loading at 8s so consumers render a degraded-but-usable UI
  // instead of a blank/frozen screen. The loggedOut guard is respected — a logout
  // sets loading false, so this no-ops afterwards.
  useEffect(() => {
    if (!loading) return;
    const t = setTimeout(() => setLoading(false), 8000);
    return () => clearTimeout(t);
  }, [loading]);

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

  // Reset all trading state to its initial (logged-out) shape. Storage clearing
  // and the redirect are NOT done here — the auth layer (logoutUser) owns
  // storage and the UI layer (useLogout) owns the hard redirect. This only
  // tears down in-memory React state and latches the loggedOut guard so no
  // in-flight async writeback can repopulate it before the page unloads.
  const logout = useCallback(() => {
    loggedOutRef.current = true;
    setBalance(100000);
    setPortfolio([]);
    setActivePortfolioId(null);
    setLastAction(null);
    setDecisions([]);
    setMissedOpportunities([]);
    setUserInsights({
      behaviorType: 'neutral',
      insightMessage: 'Start making decisions to unlock behavioral analysis.'
    });
    setGameImpact({ amount: 0, type: null, timestamp: null });
    setError(null);
    setLoading(false);
  }, []);

  const executeBuy = useCallback(async (stock, quantity) => {
    const qty = parseFloat(quantity);
    if (isNaN(qty) || qty <= 0 || !activePortfolioId || !stock) return { success: false, error: "Invalid trade" };

    const price = typeof stock?.price === 'string' ? parseFloat(stock?.price.replace(/,/g, '')) : (stock?.price || 0);
    const cost = qty * price;
    if (cost > balance) return { success: false, error: "Insufficient balance" };

    // Snapshot ONLY this symbol's pre-trade holding (or null if new) for a
    // transaction-safe rollback. The optimistic map() below replaces this
    // symbol's entry with a fresh object, so this reference keeps the old
    // values intact for restoration without clobbering other symbols.
    const prevHolding = portfolio.find(p => p.symbol === stock.symbol) || null;

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
      // Transaction-safe rollback: reverse ONLY this trade's effects via
      // functional updates instead of restoring a stale snapshot (which would
      // wipe a concurrent trade's successful change). Balance: undo the -cost.
      // Holdings: if this symbol pre-existed, restore its prior entry in place;
      // if it was newly created by the optimistic add, drop it. Other symbols
      // are left exactly as they currently are.
      setBalance(prev => prev + cost);
      setPortfolio(prev => {
        if (prevHolding) {
          return prev.some(p => p.symbol === stock.symbol)
            ? prev.map(p => p.symbol === stock.symbol ? prevHolding : p)
            : [...prev, prevHolding];
        }
        return prev.filter(p => p.symbol !== stock.symbol);
      });
      return { success: false, error: err.message || "Trade failed" };
    }
  }, [activePortfolioId, balance, portfolio, refreshData]);

  const executeSell = useCallback(async (symbol, currentValue, quantity) => {
    if (!activePortfolioId) return { success: false, error: "No portfolio" };
    const holding = portfolio.find(p => p.symbol === symbol);
    if (!holding) return { success: false, error: "Holding not found" };

    const sellQty = quantity || holding?.quantity || 0;
    if (sellQty <= 0) return { success: false, error: "Invalid quantity" };
    
    // Never sell at a fabricated $0 price. currentValue is derived from the
    // live quote; if the quote is unavailable it arrives as 0/undefined/NaN,
    // which the old `|| 0` fallback turned into a $0 sell that corrupts the
    // portfolio. Reject up front with a user-facing message instead.
    const sellPrice = currentValue / sellQty;
    if (!Number.isFinite(sellPrice) || sellPrice <= 0) {
      return { success: false, error: "Live price unavailable right now. Please try again in a moment." };
    }
    const sellValue = sellPrice * sellQty;

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
      // Transaction-safe rollback: reverse ONLY this trade's effects via
      // functional updates instead of restoring a stale snapshot. Snapshot
      // restore would clobber a concurrent trade's successful balance/holding
      // change. Balance: undo the +sellValue. Holdings: restore just this
      // symbol's pre-trade state, leaving every other symbol untouched.
      setBalance(prev => prev - sellValue);
      setPortfolio(prev => prev.some(p => p.symbol === symbol)
        ? prev.map(p => p.symbol === symbol ? holding : p)
        : [...prev, holding]);
      return { success: false, error: err.message || "Sell failed" };
    }
  }, [activePortfolioId, balance, portfolio, refreshData]);

  const actions = useMemo(() => ({
    executeBuy, executeSell, recordDecision, addMissedOpportunity, recordGameResult: syncGameResult, refreshData, refreshInsights, logout
  }), [executeBuy, executeSell, recordDecision, addMissedOpportunity, syncGameResult, refreshData, refreshInsights, logout]);

  const state = useMemo(() => ({
    balance, portfolio, lastAction, decisions, missedOpportunities, userInsights, gameImpact, loading, error, activePortfolioId
  }), [balance, portfolio, lastAction, decisions, missedOpportunities, userInsights, gameImpact, loading, error, activePortfolioId]);

  const contextValue = useMemo(() => ({
    ...state, ...actions
  }), [state, actions]);

  // BalanceContext isolates the balance scalar so components that only need
  // the current cash amount (StockDetailPanel, etc.) skip re-renders triggered
  // by portfolio / decision / insight state changes.
  const balanceValue = useMemo(() => ({ balance }), [balance]);

  // Nested providers ensure that components subscribing ONLY to actions never
  // rerender when state changes. The combined `TradingContext` remains for
  // backwards compatibility with all existing consumers.
  return (
    <TradeActionContext.Provider value={actions}>
      <TradeStateContext.Provider value={state}>
        <BalanceContext.Provider value={balanceValue}>
          <TradingContext.Provider value={contextValue}>{children}</TradingContext.Provider>
        </BalanceContext.Provider>
      </TradeStateContext.Provider>
    </TradeActionContext.Provider>
  );
}

export function useTrading() {
  const context = useContext(TradingContext);
  if (!context) throw new Error('useTrading must be used within a TradingProvider');
  return context;
}

export function useTradeState() {
  const ctx = useContext(TradeStateContext);
  if (!ctx) throw new Error('useTradeState must be used within a TradingProvider');
  return ctx;
}

export function useTradeActions() {
  const ctx = useContext(TradeActionContext);
  if (!ctx) throw new Error('useTradeActions must be used within a TradingProvider');
  return ctx;
}

// Granular hook — subscribes only to balance changes.
// Use instead of useTrading() in components that purely display/check cash balance.
export function useBalance() {
  const ctx = useContext(BalanceContext);
  if (!ctx) throw new Error('useBalance must be used within a TradingProvider');
  return ctx;
}
