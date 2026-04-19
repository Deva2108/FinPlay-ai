const systemThinkingPoints = [
  "Analyzing historical volume patterns during previous breakout attempts.",
  "Cross-referencing sector sentiment with institutional flow data.",
  "Evaluating RSI divergence against recent price action consolidation.",
  "Assessing the impact of macro-economic shifts on current volatility.",
  "Scanning order books for potential buy-side liquidity clusters.",
  "Simulating potential drawdown scenarios based on support levels.",
  "Measuring retail sentiment vs. large-scale block trade activity.",
  "Correlating quarterly earnings projections with current market pricing."
];

const analyticTones = [
  "Conservative analysis suggests caution here. The structure is fragile.",
  "High-velocity signals detected. Momentum is favoring a breakout.",
  "Neutral consolidation pattern identified. Market is searching for direction.",
  "Divergence in volume and price indicates a potential trend reversal.",
  "Institutional fingerprints are visible in the recent price floor.",
  "Volatility expansion expected. The current range is becoming unsustainable."
];

const confidenceLevels = [
  { text: "Moderate conviction", value: "65%" },
  { text: "High probability setup", value: "82%" },
  { text: "Low confidence signal", value: "41%" },
  { text: "Speculative momentum", value: "54%" },
  { text: "Strong structural alignment", value: "78%" }
];

export const getAnalyticalDetails = (stock) => {
  // Use stock data to influence the points, but randomize for flavor
  const shuffledPoints = [...systemThinkingPoints].sort(() => 0.5 - Math.random());
  const selectedPoints = shuffledPoints.slice(0, 3);
  
  const tone = analyticTones[Math.floor(Math.random() * analyticTones.length)];
  const confidence = confidenceLevels[Math.floor(Math.random() * confidenceLevels.length)];

  // Expand the basic situation into a more analytical description
  const expandedSituation = `${stock.situation} ${tone}`;

  return {
    expandedSituation,
    systemThinking: selectedPoints,
    confidence
  };
};
