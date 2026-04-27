import axios from 'axios';

// Backend Base URL from env or fallback
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

if (!import.meta.env.VITE_API_URL) {
  console.warn("VITE_API_URL is not defined. Falling back to http://localhost:8080");
}

export const API_ENDPOINTS = {
  AUTH: {
    LOGIN: '/api/auth/login',
    REGISTER: '/api/auth/register',
  },
  USER: {
    PROFILE: (email) => `/api/user/profile/${email}`,
  },
  PORTFOLIO: {
    BASE: '/api/portfolios',
    BALANCE: (id) => `/api/portfolios/${id}/balance`,
    MENTOR: (id) => `/api/portfolios/${id}/mentor`,
  },
  HOLDINGS: {
    BASE: '/api/holdings',
    TRANSACTIONS: '/api/holdings/transactions',
    DELETE: (id) => `/api/holdings/${id}`,
  },
  MARKET: {
    QUOTES: '/api/market/quotes',
    QUOTE: '/api/market/quote',
    NEWS: '/api/market/news',
    DETAILS: '/api/market/details',
    SEARCH: '/api/market/search',
    CHART: '/api/market/chart',
    INDICES: '/api/market/indices',
    INDEX_INSIGHT: '/api/market/index-insight',
    VIBE: '/api/market/vibe',
    PULSE: '/api/market/pulse',
    FAMOUS: '/api/market/insights/famous',
  },
  DECISION: {
    BASE: '/api/decision',
    EVALUATE: '/api/decision/evaluate',
    STATS: '/api/decision/stats',
    INSIGHTS: '/api/decision/insights',
    ARCHETYPE: '/api/decision/archetype',
  },
  AI: {
    ONBOARDING: {
      SCENARIO: '/api/ai/onboarding/scenario',
      SCENARIOS: '/api/ai/onboarding/scenarios',
      FEEDBACK: '/api/ai/onboarding/feedback',
      SUMMARY: '/api/ai/onboarding/summary',
    },
    EXPLAIN: '/api/explain',
    TUTORIAL: '/api/tutorial/insight',
  }
};

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

// Interceptor for JWT
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Interceptor for Expiration/Auth Errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      const isAuthPage = window.location.pathname === '/login' || window.location.pathname === '/register';
      if (!isAuthPage) {
        localStorage.removeItem('token');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

/**
 * Standard unwrap for the new ApiResponse structure:
 * { success: true, data: T, message: "" }
 */
const unwrap = (response) => {
  if (!response || !response.data) return null;
  // If backend returns the standardized ApiResponse
  if (response.data.success !== undefined) {
    return response.data.data;
  }
  // Fallback for non-standardized or error responses
  return response.data;
};

// Auth APIs
export const registerUser = async (data) => {
  const response = await api.post(API_ENDPOINTS.AUTH.REGISTER, data);
  return unwrap(response);
};

export const loginUser = async (data) => {
  localStorage.removeItem('token');
  const response = await api.post(API_ENDPOINTS.AUTH.LOGIN, data);
  const result = unwrap(response);
  const token = result?.token; 
  if (token) {
    localStorage.setItem('token', token);
  }
  return result;
};

export const logoutUser = () => {
  localStorage.removeItem('token');
};

// User APIs
export const updateProfile = async (email, data) => {
  const response = await api.put(API_ENDPOINTS.USER.PROFILE(email), data);
  return unwrap(response);
};

// Trading & Portfolio APIs
export const getPortfolio = async () => {
  const response = await api.get(API_ENDPOINTS.PORTFOLIO.BASE);
  return unwrap(response);
};

export const getUserPortfolios = getPortfolio;

export const getPortfolioMentorAdvice = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.PORTFOLIO.MENTOR(portfolioId));
  return unwrap(response);
};

export const createPortfolio = async (name) => {
  const response = await api.post(API_ENDPOINTS.PORTFOLIO.BASE, { portfolioName: name });
  return unwrap(response);
};

export const updatePortfolioBalance = async (portfolioId, amount) => {
  const response = await api.post(API_ENDPOINTS.PORTFOLIO.BALANCE(portfolioId), { amount });
  return unwrap(response);
};

// Holdings APIs
export const getHoldings = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.HOLDINGS.BASE, { params: { portfolioId } });
  return unwrap(response);
};

export const executeTrade = async (tradeData) => {
  const response = await api.post(API_ENDPOINTS.HOLDINGS.BASE, tradeData);
  return unwrap(response);
};

export const getTransactions = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.HOLDINGS.TRANSACTIONS, { params: { portfolioId } });
  return unwrap(response);
};

// Market Data APIs
export const getLiveQuotes = async (symbols) => {
  if (!Array.isArray(symbols) || symbols.length === 0) return [];
  const response = await api.get(API_ENDPOINTS.MARKET.QUOTES, { params: { symbols: symbols.join(',') } });
  return unwrap(response) || [];
};

export const getQuotes = (symbols) => getLiveQuotes(symbols);

export const getNews = async (query = 'stock market') => {
  const response = await api.get(API_ENDPOINTS.MARKET.NEWS, { params: { query } });
  return unwrap(response) || [];
};

export const getStockDetails = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.MARKET.DETAILS, { params: { symbol } });
  return unwrap(response);
};

export const getChartData = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.MARKET.CHART, { params: { symbol } });
  return unwrap(response) || [];
};

export const getIndices = async (marketType = 'US') => {
  const response = await api.get(API_ENDPOINTS.MARKET.INDICES, { params: { marketType } });
  return unwrap(response) || [];
};

export const getIndexInsight = async (symbol, value, change, marketType) => {
  const response = await api.get(API_ENDPOINTS.MARKET.INDEX_INSIGHT, { params: { symbol, value, change, marketType } });
  return unwrap(response);
};

export const getMarketVibe = async (marketType) => {
  const response = await api.get(API_ENDPOINTS.MARKET.VIBE, { params: { marketType } });
  return unwrap(response);
};

export const getMarketPulse = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.MARKET.PULSE, { params: { portfolioId } });
  return unwrap(response);
};

export const getFamousInsights = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.MARKET.FAMOUS, { params: { symbol } });
  return unwrap(response) || [];
};

export const searchStocks = async (q, signal) => {
  const response = await api.get(API_ENDPOINTS.MARKET.SEARCH, { params: { q }, signal });
  return unwrap(response) || [];
};

export const getGainers = async (cap, sector) => {
  const response = await api.get(API_ENDPOINTS.MARKET.GAINERS, { params: { cap, sector } });
  return unwrap(response) || [];
};

export const getLosers = async (cap, sector) => {
  const response = await api.get(API_ENDPOINTS.MARKET.LOSERS, { params: { cap, sector } });
  return unwrap(response) || [];
};

export const getTrending = async () => {
  const response = await api.get(API_ENDPOINTS.MARKET.TRENDING);
  return unwrap(response) || [];
};

export const getBySector = async (name) => {
  const response = await api.get(API_ENDPOINTS.MARKET.SECTOR, { params: { name } });
  return unwrap(response) || [];
};

// Insight & AI APIs
export const trackDecision = async (decisionData) => {
  const response = await api.post(API_ENDPOINTS.DECISION.BASE, decisionData);
  return unwrap(response);
};

export const explainStock = async (stockData) => {
  const response = await api.post(API_ENDPOINTS.AI.EXPLAIN, stockData);
  return unwrap(response);
};

export const getDecisionStats = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.STATS);
  return unwrap(response);
};

export const getUserInsights = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.INSIGHTS);
  return unwrap(response);
};

export const getInsights = (symbol) => {
  // If we have a symbol-specific insight endpoint, use it. Otherwise fallback to user insights.
  return getUserInsights();
};

export const getArchetype = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.ARCHETYPE);
  return unwrap(response);
};

export const evaluateDecision = async (evaluationData) => {
  const response = await api.post(API_ENDPOINTS.DECISION.EVALUATE, evaluationData);
  return unwrap(response);
};

export const getOnboardingScenario = async (userType) => {
  const response = await api.post(API_ENDPOINTS.AI.ONBOARDING.SCENARIO, { userType });
  return unwrap(response);
};

export const getArenaScenarios = async (marketType) => {
  const response = await api.get(API_ENDPOINTS.AI.ONBOARDING.SCENARIOS, { params: { marketType } });
  return unwrap(response) || [];
};

export const getOnboardingFeedback = async (choice, userType) => {
  const response = await api.post(API_ENDPOINTS.AI.ONBOARDING.FEEDBACK, { choice, userType });
  return unwrap(response);
};

export const getArenaSummary = async (decisions) => {
  const response = await api.post(API_ENDPOINTS.AI.ONBOARDING.SUMMARY, { decisions });
  return unwrap(response);
};

export const getTutorialInsight = async (topic, context) => {
  const response = await api.get(API_ENDPOINTS.AI.TUTORIAL, { params: { topic, context } });
  return unwrap(response);
};
