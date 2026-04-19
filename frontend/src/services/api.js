import axios from 'axios';

// Backend Base URL
const BACKEND_URL = 'http://localhost:8080';

export const api = axios.create({
  baseURL: BACKEND_URL,
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
        console.log("JWT EXPIRED OR INVALID -> AUTO LOGOUT");
        localStorage.removeItem('token');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

// Auth APIs
export const registerUser = async (data) => {
  try {
    const response = await api.post('/user/register', data);
    return response.data.result || response.data;
  } catch (error) {
    throw error.response?.data?.message || error;
  }
};

export const loginUser = async (data) => {
  try {
    // Cleanup old token
    localStorage.removeItem('token');
    
    const response = await api.post('/user/login', data);
    const result = response.data.result || response.data;
    const token = typeof result === 'string' ? result : result?.token;
    if (token) {
      localStorage.setItem('token', token);
    }
    return response;
  } catch (error) {
    throw error.response?.data?.message || error;
  }
};

export const logoutUser = () => {
  localStorage.removeItem('token');
};

// Trading APIs
export const getUserPortfolios = async () => {
  try {
    const response = await api.get('/portfolios');
    return response.data.result || response.data;
  } catch (error) {
    throw error.response?.data?.message || error;
  }
};

export const createPortfolio = async (name) => {
  try {
    const response = await api.post('/portfolios', { name });
    return response.data.result || response.data;
  } catch (error) {
    throw error.response?.data?.message || error;
  }
};

export const executeTrade = async (tradeData) => {
  try {
    const portfolios = await getUserPortfolios();
    const portfolioList = Array.isArray(portfolios) ? portfolios : (portfolios?.result || []);
    
    if (portfolioList.length === 0) {
      throw new Error('No portfolio found for user.');
    }
    
    const portfolioId = portfolioList[0].portfolioId;
    const payload = {
      ...tradeData,
      portfolioId
    };
    
    const response = await api.post('/api/holdings', payload);
    return response.data.result || response.data;
  } catch (error) {
    throw error.response?.data?.message || error;
  }
};

export const getHoldings = async (portfolioId) => {
  try {
    const response = await api.get('/api/holdings', { params: { portfolioId } });
    return response.data.result || response.data;
  } catch (error) {
    throw error.response?.data?.message || error;
  }
};

// Live Market Data fetchers (STRICT ERROR PROPAGATION)
export const getLiveQuotes = async (symbols) => {
  if (!symbols || symbols.length === 0) return [];
  try {
    const response = await api.get('/api/market/quotes', {
      params: { symbols: symbols.join(',') }
    });
    return response.data.result || response.data;
  } catch (error) {
    console.error('getLiveQuotes failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const getQuotes = (symbols) => getLiveQuotes(symbols);

export const getNews = async (query = 'stock market') => {
  const finalQuery = typeof query === 'number' ? 'stock market' : query;
  try {
    const response = await api.get('/api/market/news', {
      params: { query: finalQuery }
    });
    return response.data.result || response.data;
  } catch (error) {
    console.error('getNews failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const getStockDetails = async (symbol) => {
  try {
    const response = await api.get('/api/market/details', {
      params: { symbol }
    });
    return response.data.result || response.data;
  } catch (error) {
    console.error('getStockDetails failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const getChartData = async (symbol) => {
  try {
    const response = await api.get('/api/market/chart', {
      params: { symbol }
    });
    return response.data.result || response.data;
  } catch (error) {
    console.error('getChartData failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const getIndices = async () => {
  try {
    const response = await api.get('/api/market/indices');
    return response.data.result || response.data;
  } catch (error) {
    console.error('getIndices failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const searchStocks = async (q, signal) => {
  try {
    const response = await api.get('/api/market/search', { 
      params: { q },
      signal 
    });
    return response.data.result || response.data;
  } catch (error) {
    if (axios.isCancel(error)) {
      throw { name: 'AbortError' };
    }
    console.error('searchStocks failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const getGainers = async (cap, sector) => {
  try {
    const response = await api.get('/api/market/gainers', { params: { cap, sector } });
    return response.data.result || response.data;
  } catch (error) {
    console.error('getGainers failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const getLosers = async (cap, sector) => {
  try {
    const response = await api.get('/api/market/losers', { params: { cap, sector } });
    return response.data.result || response.data;
  } catch (error) {
    console.error('getLosers failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const getTrending = async () => {
  try {
    const response = await api.get('/api/market/trending');
    return response.data.result || response.data;
  } catch (error) {
    console.error('getTrending failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const getBySector = async (name) => {
  try {
    const response = await api.get('/api/market/sector', { params: { name } });
    return response.data.result || response.data;
  } catch (error) {
    console.error('getBySector failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const trackDecision = async (decisionData) => {
  try {
    const response = await api.post('/api/decision', decisionData);
    return response.data;
  } catch (error) {
    console.error('trackDecision failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const explainStock = async (stockData) => {
  try {
    const response = await api.post('/api/explain', stockData);
    return response.data;
  } catch (error) {
    console.error('explainStock failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const getDecisionStats = async () => {
  try {
    const response = await api.get('/api/decision/stats');
    return response.data;
  } catch (error) {
    console.error('getDecisionStats failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const getUserInsights = async () => {
  try {
    const response = await api.get('/api/decision/insights');
    return response.data;
  } catch (error) {
    console.error('getUserInsights failed:', error);
    throw error.response?.data?.message || error;
  }
};

export const evaluateDecision = async (evaluationData) => {
  try {
    const response = await api.post('/api/decision/evaluate', evaluationData);
    return response.data;
  } catch (error) {
    console.error('evaluateDecision failed:', error);
    throw error.response?.data?.message || error;
  }
};
