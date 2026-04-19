/**
 * Adaptive Learning Engine for FinPlay
 * Generates personalized insights based on user behavior data.
 */

export const getLearningInsight = (userData) => {
  const { 
    decisions = [], 
    missedOpportunities = [], 
    holdings = [], 
    totalCurrentValue = 0
  } = userData;

  // Rule 1: Missing Momentum (Too Cautious)
  const highConfidenceSkips = missedOpportunities.filter(opp => parseFloat(opp.potentialGain) > 5).length;
  if (highConfidenceSkips >= 2) {
    return {
      topic: "Buying the Trend",
      message: "You are skipping stocks that are already moving up.",
      explanation: "This means you might be waiting too long for a 'perfect' price. Many people buy when a trend is strong to catch the next move.",
      why: "Try taking a small entry when you see strong upward movement."
    };
  }

  // Rule 2: Over-Concentration (Risk Management)
  if (holdings.length > 0 && totalCurrentValue > 0) {
    const maxWeight = Math.max(...holdings.map(h => (h.currentValue / totalCurrentValue) * 100));
    if (maxWeight > 70) {
      return {
        topic: "Diversification",
        message: "Most of your money is in just one stock.",
        explanation: "If this stock falls, your whole portfolio will take a big hit. Spreading your money helps reduce this risk.",
        why: "Consider looking at other sectors to balance your portfolio."
      };
    }
  }

  // Rule 3: Holding Losses (Stop Loss)
  const losingTrades = holdings.filter(h => h.gainPct < -5);
  if (losingTrades.length >= 2) {
    return {
      topic: "Protecting Capital",
      message: "You are holding onto losing trades for too long.",
      explanation: "Investors use a 'Stop Loss' to automatically sell a stock if it drops too much. This prevents a small loss from becoming a big one.",
      why: "Set a limit for yourself: if a stock drops 5-10%, it might be time to exit."
    };
  }

  // Rule 4: Impulsive Buying (Overtrading)
  const recentDecisions = decisions.slice(0, 10);
  const buyCount = recentDecisions.filter(d => d.action === 'buy').length;
  if (buyCount > 8 && recentDecisions.length >= 8) {
    return {
      topic: "Being Selective",
      message: "You are buying almost every stock you see.",
      explanation: "Buying too many stocks makes it hard to manage them. Successful investors wait for only the best opportunities.",
      why: "Try to skip more setups and only buy the ones you feel most confident about."
    };
  }

  // Rule 5: Profit Taking (Knowing when to exit)
  const highProfitHoldings = holdings.filter(h => h.gainPct > 15);
  if (highProfitHoldings.length >= 2) {
    return {
      topic: "Booking Profits",
      message: "You have some stocks with very high gains.",
      explanation: "A profit is only 'real' once you sell and take the money. If the stock falls tomorrow, your paper gains will disappear.",
      why: "Consider selling a part of your position to lock in your success."
    };
  }

  // Rule 6: Market Timing Bias
  const bearMarketBuys = decisions.filter(d => d.marketMode === 'BEAR' && d.action === 'buy').length;
  if (bearMarketBuys > 3) {
    return {
      topic: "Market Trends",
      message: "You are buying while the overall market is falling.",
      explanation: "It is much harder to make money when most stocks are going down. This is called 'swimming against the tide'.",
      why: "Wait for the market to show signs of recovery before making big buys."
    };
  }

  // Default: General Tip
  return {
    topic: "The Power of Time",
    message: "Consistency is your best friend in investing.",
    explanation: "Small, regular gains that grow over time lead to wealth. You don't need to find a 'lottery' stock to succeed.",
    why: "Focus on making sound decisions today to benefit your future self."
  };
};
