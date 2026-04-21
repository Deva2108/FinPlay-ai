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
