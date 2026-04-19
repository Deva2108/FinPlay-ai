import React, { Component, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import Portfolio from './pages/Portfolio';
import LiveMarket from './pages/LiveMarket';
import Insights from './pages/Insights';
import StockDetails from './pages/StockDetails';
import Login from './pages/Login';
import Register from './pages/Register';
import Onboarding from './pages/Onboarding';
import SimpleDashboard from './pages/SimpleDashboard';
import Layout from './components/Layout';
import { StockPanelProvider } from './context/StockPanelContext';
import { TradingProvider } from './context/TradingContext';
import { MarketProvider } from './context/MarketContext';
import { BehaviorProvider } from './context/BehaviorContext';

class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error("App Crashed:", error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-[#020617] flex items-center justify-center p-10">
          <div className="glass-card p-8 border-rose-500/20 text-center max-w-md">
            <h2 className="text-2xl font-black text-rose-500 mb-4">SYSTEM ERROR</h2>
            <p className="text-slate-400 text-sm mb-6">{this.state.error?.message || "An unexpected error occurred."}</p>
            <button 
              onClick={() => window.location.reload()}
              className="px-6 py-2 bg-rose-600 text-white font-black rounded-xl uppercase tracking-widest text-xs"
            >
              Reboot System
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

export default function App() {
  useEffect(() => {
    console.log("App Loaded: Initializing Flow Audit");
  }, []);

  // Testing Stable Flow
  return (
    <ErrorBoundary>
      <MarketProvider>
        <BehaviorProvider>
          <TradingProvider>
            <StockPanelProvider>
              <BrowserRouter>
                <Routes>
                  {/* Start-to-Market Flow */}
                  <Route path="/" element={<Onboarding />} />
                  <Route path="/onboarding" element={<Onboarding />} />
                  <Route path="/dashboard" element={<SimpleDashboard />} />
                  
                  {/* Market Experience (Arena) with Layout */}
                  <Route path="/arena" element={<Layout><Dashboard /></Layout>} />
                  <Route path="/market" element={<Layout><LiveMarket /></Layout>} />
                  <Route path="/portfolio" element={<Layout><Portfolio /></Layout>} />
                  <Route path="/insights" element={<Layout><Insights /></Layout>} />
                  <Route path="/stock/:symbol" element={<Layout><StockDetails /></Layout>} />
                  
                  {/* Auth & System */}
                  <Route path="/login" element={<Login />} />
                  <Route path="/register" element={<Register />} />
                  
                  {/* Fallback */}
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </BrowserRouter>
            </StockPanelProvider>
          </TradingProvider>
        </BehaviorProvider>
      </MarketProvider>
    </ErrorBoundary>
  );
}
