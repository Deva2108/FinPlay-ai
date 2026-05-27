// Polyfills MUST be the very first import — they must execute before framer-motion,
// recharts, lucide-react, or any other third-party module that calls .at() is parsed.
// ES module imports are hoisted and evaluated in order, so position matters here.
import './polyfills';

import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles/globals.css';

declare global {
  interface Window {
    t: (key: string) => string;
  }
}

if (typeof window !== 'undefined') {
  window.t = (key: string) => key;
}

const rootElement = document.getElementById('root');
if (rootElement) {
  ReactDOM.createRoot(rootElement).render(
    <App />
  );
}
