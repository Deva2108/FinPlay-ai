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
    GAINERS: '/api/market/gainers',
    LOSERS: '/api/market/losers',
    TRENDING: '/api/market/trending',
    SECTOR: '/api/market/sector',
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
  },
  WATCHLIST: {
    BASE: '/api/watchlist',
    CHECK: (symbol) => `/api/watchlist/check/${symbol}`,
    TOGGLE: (symbol) => `/api/watchlist/${symbol}`,
  },
  VAULT: {
    DAILY: '/api/vault/daily',
    CARDS: '/api/vault/cards',
  }
};

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 45000,
});

// Interceptor for JWT
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Interceptor for Expiration/Auth Errors + transient retry
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config;

    // Retry once on network/timeout failures (covers Render cold-start hangs)
    const isTransient = !error.response &&
      (error.code === 'ECONNABORTED' || error.code === 'ERR_NETWORK' || error.message === 'Network Error');

    if (isTransient && !config._retried) {
      config._retried = true;
      await new Promise(resolve => setTimeout(resolve, 1000));
      return api(config);
    }

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
 * Returns ONLY the 'data' field to maintain backward compatibility with components.
 */
export const unwrapApiResponse = (response) => {
  const res = response?.data;
  if (!res) return { error: true, message: "Network Error" };

  if (res.success) return res.data;
  return { error: true, message: res.message || "Request failed" };
};

/**
 * Returns the FULL envelope {success, data, meta} for components that need to check meta.status
 */
export const unwrapEnvelope = (response) => {
  const res = response?.data;
  if (!res) return { success: false, error: true, message: "Network Error" };

  return {
    success: res.success,
    data: res.data,
    meta: res.meta,
    message: res.message
  };
};

export const readApiEnvelope = unwrapEnvelope;

// Auth APIs
function persistAuth(result) {
  const token = result?.data?.token;
  const user  = result?.data?.user;
  if (token) localStorage.setItem('token', token);
  if (user)  localStorage.setItem('finplay_user', JSON.stringify(user));
}

export const registerUser = async (data) => {
  const response = await api.post(API_ENDPOINTS.AUTH.REGISTER, data);
  const result = readApiEnvelope(response);
  persistAuth(result);
  return result;
};

export const loginUser = async (data) => {
  localStorage.removeItem('token');
  localStorage.removeItem('finplay_user');
  const response = await api.post(API_ENDPOINTS.AUTH.LOGIN, data);
  const result = readApiEnvelope(response);
  persistAuth(result);
  return result;
};

export const logoutUser = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('finplay_user');
};

// User APIs
export const updateProfile = async (email, data) => {
  const response = await api.put(API_ENDPOINTS.USER.PROFILE(email), data);
  return readApiEnvelope(response);
};

// Trading & Portfolio APIs
export const getPortfolio = async () => {
  const response = await api.get(API_ENDPOINTS.PORTFOLIO.BASE);
  return readApiEnvelope(response);
};

export const getUserPortfolios = getPortfolio;

export const getPortfolioMentorAdvice = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.PORTFOLIO.MENTOR(portfolioId));
  return readApiEnvelope(response);
};

export const createPortfolio = async (name) => {
  const response = await api.post(API_ENDPOINTS.PORTFOLIO.BASE, { portfolioName: name });
  return readApiEnvelope(response);
};

export const updatePortfolioBalance = async (portfolioId, amount) => {
  const response = await api.post(API_ENDPOINTS.PORTFOLIO.BALANCE(portfolioId), { amount });
  return readApiEnvelope(response);
};

// User-facing /api/portfolios/{id}/reset was removed (now admin-only at
// /api/admin/users/{id}/reset). Keeping the export intentionally absent so any
// stale UI call surfaces as a build error rather than a silent 404.

// Holdings APIs
export const getHoldings = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.HOLDINGS.BASE, { params: { portfolioId } });
  return readApiEnvelope(response);
};

export const executeTrade = async (tradeData) => {
  const response = await api.post(API_ENDPOINTS.HOLDINGS.BASE, tradeData);
  return readApiEnvelope(response);
};

export const getTransactions = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.HOLDINGS.TRANSACTIONS, { params: { portfolioId } });
  return readApiEnvelope(response);
};

// Market Data APIs
export const getLiveQuote = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.MARKET.QUOTE, { params: { symbol } });
  return readApiEnvelope(response);
};

export const getLiveQuotes = async (symbols) => {
  if (!Array.isArray(symbols) || symbols.length === 0) return { success: true, data: [], meta: { status: 'OK' } };
  const response = await api.get(API_ENDPOINTS.MARKET.QUOTES, { params: { symbols: symbols.join(',') } });
  return readApiEnvelope(response);
};

export const getQuotes = (symbols) => getLiveQuotes(symbols);

export const getNews = async (query = 'stock market') => {
  const response = await api.get(API_ENDPOINTS.MARKET.NEWS, { params: { query } });
  return readApiEnvelope(response);
};

export const getStockDetails = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.MARKET.DETAILS, { params: { symbol } });
  return readApiEnvelope(response);
};

export const getChartData = async (symbol, timeframe = '1M') => {
  const response = await api.get(API_ENDPOINTS.MARKET.CHART, { params: { symbol, timeframe } });
  return readApiEnvelope(response);
};

export const getIndices = async (marketType = 'US') => {
  const response = await api.get(API_ENDPOINTS.MARKET.INDICES, { params: { marketType } });
  return readApiEnvelope(response);
};

export const getIndexInsight = async (symbol, value, change, marketType) => {
  const response = await api.get(API_ENDPOINTS.MARKET.INDEX_INSIGHT, { params: { symbol, value, change, marketType } });
  return readApiEnvelope(response);
};

export const getMarketVibe = async (marketType) => {
  const response = await api.get(API_ENDPOINTS.MARKET.VIBE, { params: { marketType } });
  return readApiEnvelope(response);
};

export const getMarketVibeResponse = getMarketVibe;

export const getMarketPulse = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.MARKET.PULSE, { params: { portfolioId } });
  return readApiEnvelope(response);
};

export const getFamousInsights = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.MARKET.FAMOUS, { params: { symbol } });
  return readApiEnvelope(response);
};

export const searchStocks = async (q, signal) => {
  const response = await api.get(API_ENDPOINTS.MARKET.SEARCH, { params: { q }, signal });
  return readApiEnvelope(response);
};

export const getGainers = async (cap, sector) => {
  const response = await api.get(API_ENDPOINTS.MARKET.GAINERS, { params: { cap, sector } });
  return readApiEnvelope(response);
};

export const getLosers = async (cap, sector) => {
  const response = await api.get(API_ENDPOINTS.MARKET.LOSERS, { params: { cap, sector } });
  return readApiEnvelope(response);
};

export const getTrending = async () => {
  const response = await api.get(API_ENDPOINTS.MARKET.TRENDING);
  return readApiEnvelope(response);
};

export const getBySector = async (name) => {
  const response = await api.get(API_ENDPOINTS.MARKET.SECTOR, { params: { name } });
  return readApiEnvelope(response);
};

// Insight & AI APIs
export const trackDecision = async (decisionData) => {
  const response = await api.post(API_ENDPOINTS.DECISION.BASE, decisionData);
  return readApiEnvelope(response);
};

export const explainStock = async (stockData) => {
  const response = await api.post(API_ENDPOINTS.AI.EXPLAIN, stockData);
  return readApiEnvelope(response);
};

export const getDecisionStats = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.STATS);
  return readApiEnvelope(response);
};

export const getUserInsights = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.INSIGHTS);
  return readApiEnvelope(response);
};

export const getRecentDecisions = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.BASE);
  return readApiEnvelope(response);
};

export const getInsights = (symbol) => {
  // If we have a symbol-specific insight endpoint, use it. Otherwise fallback to user insights.
  return getUserInsights();
};

export const getArchetype = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.ARCHETYPE);
  return readApiEnvelope(response);
};

export const evaluateDecision = async (evaluationData) => {
  const response = await api.post(API_ENDPOINTS.DECISION.EVALUATE, evaluationData);
  return readApiEnvelope(response);
};

export const getOnboardingScenario = async (userType) => {
  const response = await api.post(API_ENDPOINTS.AI.ONBOARDING.SCENARIO, { userType });
  return readApiEnvelope(response);
};

export const getArenaScenarios = async (marketType) => {
  const response = await api.get(API_ENDPOINTS.AI.ONBOARDING.SCENARIOS, { params: { marketType } });
  return readApiEnvelope(response);
};

export const getOnboardingFeedback = async (choice, userType) => {
  const response = await api.post(API_ENDPOINTS.AI.ONBOARDING.FEEDBACK, { choice, userType });
  return readApiEnvelope(response);
};

export const getArenaSummary = async (decisions) => {
  const response = await api.post(API_ENDPOINTS.AI.ONBOARDING.SUMMARY, { decisions });
  return readApiEnvelope(response);
};

export const getTutorialInsight = async (topic, context) => {
  const response = await api.get(API_ENDPOINTS.AI.TUTORIAL, { params: { topic, context } });
  return readApiEnvelope(response);
};

export const getTutorialInsightResponse = getTutorialInsight;

// Watchlist APIs
export const getWatchlist = async () => {
  const response = await api.get(API_ENDPOINTS.WATCHLIST.BASE);
  return readApiEnvelope(response);
};

export const addToWatchlist = async (symbol) => {
  const response = await api.post(API_ENDPOINTS.WATCHLIST.TOGGLE(symbol));
  return readApiEnvelope(response);
};

export const removeFromWatchlist = async (symbol) => {
  const response = await api.delete(API_ENDPOINTS.WATCHLIST.TOGGLE(symbol));
  return readApiEnvelope(response);
};

export const checkWatchlist = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.WATCHLIST.CHECK(symbol));
  return readApiEnvelope(response);
};

export const getVaultDaily = async () => {
  const response = await api.get(API_ENDPOINTS.VAULT.DAILY);
  return readApiEnvelope(response);
};

export const getVaultCards = async () => {
  const response = await api.get(API_ENDPOINTS.VAULT.CARDS);
  return readApiEnvelope(response);
};
