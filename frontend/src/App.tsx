import React, { Component, Suspense, ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';

const LoadingScreen = () => (
  <div className="h-screen w-full bg-[#020617] flex flex-col items-center justify-center gap-4">
    <div className="w-12 h-12 border-4 border-blue-500/20 border-t-blue-500 rounded-full animate-spin" />
    <p className="text-[10px] font-black text-blue-500 uppercase tracking-[0.3em] animate-pulse">Syncing Arena...</p>
  </div>
);

import Dashboard from './pages/Dashboard';
import FinPlayArena from './components/FinPlayArena';
import Portfolio from './pages/Portfolio';
import LiveMarket from './pages/LiveMarket';
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

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error("REAL_ERROR:", error);
    console.error("STACK_TRACE:", errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-[#020617] flex items-center justify-center p-6">
          <div className="relative group max-w-md w-full">
            <div className="absolute inset-0 bg-rose-500/10 blur-3xl rounded-full opacity-50 group-hover:opacity-100 transition-opacity" />
            <div className="relative bg-slate-900/80 border border-rose-500/20 backdrop-blur-xl p-10 rounded-[2.5rem] text-center space-y-6 shadow-2xl">
              <div className="w-20 h-20 bg-rose-500/10 rounded-3xl flex items-center justify-center mx-auto border border-rose-500/20">
                <AlertTriangle size={40} className="text-rose-500 animate-pulse" />
              </div>
              <div className="space-y-2">
                <h2 className="text-2xl font-black text-white uppercase tracking-tight">Sync Interrupted</h2>
                <p className="text-slate-400 text-sm leading-relaxed">The system encountered a logic gap. Don't worry, your assets are safe.</p>
              </div>
              <button 
                onClick={() => window.location.href = '/'}
                className="w-full py-4 bg-white text-slate-950 font-black rounded-2xl uppercase tracking-widest text-[10px] hover:bg-slate-200 transition-all shadow-xl shadow-white/5"
              >
                Reboot Connection
              </button>
            </div>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

export default function App() {
  const isFirstTime = !localStorage.getItem('finplay_arena_done');

  return (
    <ErrorBoundary>
      <MarketProvider>
        <BehaviorProvider>
          <TradingProvider>
            <StockPanelProvider>
              <BrowserRouter>
                <Suspense fallback={<LoadingScreen />}>
                  <Routes>
                    {/* Entry Logic */}
                    <Route path="/" element={isFirstTime ? <Navigate to="/onboarding" replace /> : <Layout><Dashboard /></Layout>} />
                    
                    {/* Start-to-Market Flow */}
                    <Route path="/onboarding" element={<Onboarding />} />
                    
                    {/* Market Experience (Arena) with Layout */}
                    <Route 
                      path="/arena" 
                      element={
                        <Layout>
                          <FinPlayArena 
                            onDecisionMade={() => {}} 
                            onShowInsight={() => {}} 
                            context={{}} 
                          />
                        </Layout>
                      } 
                    />
                    <Route path="/market" element={<Layout><LiveMarket /></Layout>} />
                    <Route path="/portfolio" element={<Layout><Portfolio /></Layout>} />
                    <Route path="/stock/:symbol" element={<Layout><StockDetails /></Layout>} />
                    
                    {/* Dashboard Redirect */}
                    <Route path="/dashboard" element={<Layout><Dashboard /></Layout>} />
                    
                    {/* Auth & System */}
                    <Route path="/login" element={<Login />} />
                    <Route path="/register" element={<Register />} />
                    
                    {/* Fallback */}
                    <Route path="*" element={<Navigate to="/" replace />} />
                  </Routes>
                </Suspense>
              </BrowserRouter>
            </StockPanelProvider>
          </TradingProvider>
        </BehaviorProvider>
      </MarketProvider>
    </ErrorBoundary>
  );
}
