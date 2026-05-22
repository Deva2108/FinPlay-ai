import React, { Component, Suspense, lazy, ReactNode, useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';

const LoadingScreen = () => (
  <div className="h-screen w-full bg-[#020617] flex flex-col items-center justify-center gap-4">
    <div className="w-12 h-12 border-4 border-blue-500/20 border-t-blue-500 rounded-full animate-spin" />
    <p className="text-[10px] font-black text-blue-500 uppercase tracking-[0.3em] animate-pulse">Syncing Arena...</p>
  </div>
);

// Lazy-loaded page components for code-splitting
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Insights = lazy(() => import('./pages/Insights'));
const FinPlayArena = lazy(() => import('./components/FinPlayArena'));
const Portfolio = lazy(() => import('./pages/Portfolio'));
const LiveMarket = lazy(() => import('./pages/LiveMarket'));
const StockDetails = lazy(() => import('./pages/StockDetails'));
const Login = lazy(() => import('./pages/Login'));
const Register = lazy(() => import('./pages/Register'));
const Onboarding = lazy(() => import('./pages/Onboarding'));
const Decisions = lazy(() => import('./pages/Decisions'));
const Vault = lazy(() => import('./pages/Vault'));
const SimpleDashboard = lazy(() => import('./pages/SimpleDashboard'));

import Layout from './components/Layout';
import { StockPanelProvider } from './context/StockPanelContext';
import { TradingProvider } from './context/TradingContext';
import { MarketProvider } from './context/MarketContext';

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

interface RouteBoundaryState { hasError: boolean }
class RouteErrorBoundary extends Component<{ children: ReactNode; name?: string }, RouteBoundaryState> {
  constructor(props: { children: ReactNode; name?: string }) {
    super(props);
    this.state = { hasError: false };
  }
  static getDerivedStateFromError(): RouteBoundaryState { return { hasError: true }; }
  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error(`Route Failure (${this.props.name || 'unknown'}):`, error, info);
  }
  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-[60vh] flex items-center justify-center p-6">
          <div className="bg-slate-900/60 border border-rose-500/20 rounded-3xl p-8 max-w-md text-center space-y-4">
            <div className="w-14 h-14 mx-auto rounded-2xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center">
              <AlertTriangle size={28} className="text-rose-500" />
            </div>
            <h3 className="text-lg font-black text-white uppercase tracking-tight">{this.props.name || 'Page'} Unavailable</h3>
            <p className="text-xs text-slate-400 leading-relaxed">Sync interrupted on this section. Other features remain online.</p>
            <button
              onClick={() => this.setState({ hasError: false })}
              className="px-5 py-2 bg-white text-slate-950 rounded-xl text-[10px] font-black uppercase tracking-widest"
            >
              Retry
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

function PrivateRoute({ children }: { children: ReactNode }) {
  const token = localStorage.getItem('token');
  return token ? <>{children}</> : <Navigate to="/login" replace />;
}

export default function App() {
  const isFirstTime = !localStorage.getItem('finplay_arena_done');

  return (
    <ErrorBoundary>
      <MarketProvider>
        <TradingProvider>
          <StockPanelProvider>
              <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
                <Suspense fallback={<LoadingScreen />}>
                  <Routes>
                    {/* Entry Logic */}
                    <Route path="/" element={
                      <PrivateRoute>
                        {isFirstTime
                          ? <Navigate to="/onboarding" replace />
                          : <Layout><Dashboard /></Layout>
                        }
                      </PrivateRoute>
                    } />

                    {/* Onboarding (requires login — users register then land here) */}
                    <Route path="/onboarding" element={
                      <PrivateRoute><Onboarding /></PrivateRoute>
                    } />

                    {/* Protected Market Experience */}
                    <Route path="/arena" element={
                      <PrivateRoute>
                        <Layout>
                          <FinPlayArena
                            onDecisionMade={() => {}}
                            onShowInsight={() => {}}
                            context=""
                          />
                        </Layout>
                      </PrivateRoute>
                    } />
                    <Route path="/market" element={<PrivateRoute><Layout><RouteErrorBoundary name="Market"><LiveMarket /></RouteErrorBoundary></Layout></PrivateRoute>} />
                    <Route path="/portfolio" element={<PrivateRoute><Layout><RouteErrorBoundary name="Portfolio"><Portfolio /></RouteErrorBoundary></Layout></PrivateRoute>} />
                    <Route path="/vault" element={<PrivateRoute><Layout><RouteErrorBoundary name="Vault"><Vault /></RouteErrorBoundary></Layout></PrivateRoute>} />
                    <Route path="/history" element={<PrivateRoute><Layout><RouteErrorBoundary name="History"><Decisions /></RouteErrorBoundary></Layout></PrivateRoute>} />
                    <Route path="/insights" element={<PrivateRoute><Layout><RouteErrorBoundary name="Insights"><Insights /></RouteErrorBoundary></Layout></PrivateRoute>} />
                    <Route path="/stock/:symbol" element={<PrivateRoute><Layout><RouteErrorBoundary name="Stock"><StockDetails /></RouteErrorBoundary></Layout></PrivateRoute>} />
                    <Route path="/dashboard" element={<PrivateRoute><Layout><RouteErrorBoundary name="Dashboard"><Dashboard /></RouteErrorBoundary></Layout></PrivateRoute>} />

                    {/* Auth (public) */}
                    <Route path="/login" element={<Login />} />
                    <Route path="/register" element={<Register />} />

                    {/* Fallback */}
                    <Route path="*" element={<Navigate to="/" replace />} />
                  </Routes>
                </Suspense>
              </BrowserRouter>
            </StockPanelProvider>
          </TradingProvider>
      </MarketProvider>
    </ErrorBoundary>
  );
}