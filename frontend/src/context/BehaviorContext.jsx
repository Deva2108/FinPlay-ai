import React, { createContext, useContext, useState, useEffect } from 'react';
import { getUserInsights, trackDecision } from '../services/api';

const BehaviorContext = createContext();

export const BehaviorProvider = ({ children }) => {
  const [behavior, setBehavior] = useState({
    decisions: [], 
    missedOpportunities: [], 
    lastActions: [],
  });

  const [userInsights, setUserInsights] = useState({
    behaviorType: 'neutral',
    insightMessage: 'Start making decisions to unlock behavioral analysis.'
  });

  const refreshInsights = async () => {
    const token = localStorage.getItem('token');
    if (!token) return;
    
    try {
      const data = await getUserInsights();
      setUserInsights(data);
    } catch (err) {
      console.error("Failed to fetch behavior insights", err);
    }
  };

  useEffect(() => {
    refreshInsights();
  }, []);

  const recordDecision = (action, stock, marketMode) => {
    const newDecision = {
      stock,
      action,
      timestamp: Date.now(),
      marketMode
    };

    // Fire and forget to backend
    trackDecision({ 
      symbol: stock.symbol, 
      action, 
      price: typeof stock.price === 'string' ? parseFloat(stock.price.replace(/,/g, '')) : (stock.price || 0), 
      market: marketMode 
    }).catch(console.error);

    setBehavior(prev => {
      const updatedDecisions = [newDecision, ...prev.decisions].slice(0, 50); 
      const updatedActions = [{ type: 'decision', ...newDecision }, ...prev.lastActions].slice(0, 10);
      
      return {
        ...prev,
        decisions: updatedDecisions,
        lastActions: updatedActions
      };
    });
  };

  const addMissedOpportunity = (stock, potentialGain) => {
    setBehavior(prev => ({
      ...prev,
      missedOpportunities: [{ stock, potentialGain, timestamp: Date.now() }, ...prev.missedOpportunities].slice(0, 10)
    }));
  };

  return (
    <BehaviorContext.Provider value={{ ...behavior, userInsights, recordDecision, addMissedOpportunity, refreshInsights }}>
      {children}
    </BehaviorContext.Provider>
  );
};

export const useBehavior = () => {
  const context = useContext(BehaviorContext);
  if (!context) {
    throw new Error('useBehavior must be used within a BehaviorProvider');
  }
  return context;
};
