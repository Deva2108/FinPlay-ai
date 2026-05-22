import React, { Component } from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';

/**
 * Granular Error Boundary to prevent a single widget failure from 
 * unmounting the entire application.
 */
class WidgetErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    console.error(`Widget Failure: ${this.props.name || 'Unknown'}`, error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className={`p-6 rounded-3xl bg-slate-900/40 border border-rose-500/20 flex flex-col items-center justify-center text-center space-y-3 ${this.props.className}`}>
          <div className="p-3 bg-rose-500/10 rounded-2xl text-rose-500">
            <AlertTriangle size={24} />
          </div>
          <div className="space-y-1">
            <p className="text-xs font-black text-white uppercase tracking-widest">
              {this.props.name || 'Component'} Unavailable
            </p>
            <p className="text-[10px] text-slate-500 font-medium leading-relaxed max-w-[200px]">
              Sync interrupted. Our engine is attempting to recover this module.
            </p>
          </div>
          <button 
            onClick={() => this.setState({ hasError: false })}
            className="flex items-center gap-2 px-4 py-2 bg-white/5 hover:bg-white/10 text-[10px] font-black text-slate-400 hover:text-white uppercase tracking-widest rounded-xl transition-all"
          >
            <RotateCcw size={12} /> Retry Sync
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

export default WidgetErrorBoundary;
