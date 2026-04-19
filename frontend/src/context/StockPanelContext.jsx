import React, { createContext, useContext, useState } from 'react';

const StockPanelContext = createContext();

export function StockPanelProvider({ children }) {
  const [selectedStock, setSelectedStock] = useState(null);
  const [isOpen, setIsOpen] = useState(false);
  const [recentlyViewed, setRecentlyViewed] = useState([]);

  const openStockPanel = (stock) => {
    setSelectedStock(stock);
    setIsOpen(true);
    
    // Update recently viewed
    setRecentlyViewed(prev => {
      const filtered = prev.filter(s => s.symbol !== stock.symbol);
      return [stock, ...filtered].slice(0, 5);
    });
  };

  const closeStockPanel = () => {
    setIsOpen(false);
    setTimeout(() => setSelectedStock(null), 300);
  };

  return (
    <StockPanelContext.Provider value={{ selectedStock, isOpen, recentlyViewed, openStockPanel, closeStockPanel }}>
      {children}
    </StockPanelContext.Provider>
  );
}

export function useStockPanel() {
  const context = useContext(StockPanelContext);
  if (!context) {
    throw new Error('useStockPanel must be used within a StockPanelProvider');
  }
  return context;
}
