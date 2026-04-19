import React, { createContext, useContext, useState, useEffect } from 'react';

const MarketContext = createContext();

export function MarketProvider({ children }) {
  const [marketMode, setMarketMode] = useState(() => {
    return localStorage.getItem('finplay_market_mode') || 'INDIA';
  });

  useEffect(() => {
    localStorage.setItem('finplay_market_mode', marketMode);
  }, [marketMode]);

  const currencySymbol = marketMode === "INDIA" ? "₹" : "$";
  const marketCode = marketMode === "INDIA" ? "IN" : "US";

  return (
    <MarketContext.Provider value={{ marketMode, setMarketMode, currencySymbol, marketCode }}>
      {children}
    </MarketContext.Provider>
  );
}

export function useMarket() {
  const context = useContext(MarketContext);
  if (!context) {
    throw new Error('useMarket must be used within a MarketProvider');
  }
  return context;
}
