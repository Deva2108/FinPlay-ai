import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import checker from 'vite-plugin-checker'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    checker({ typescript: true }),
  ],
  server: {
    hmr: { overlay: false }
  },
  build: {
    // Raise the "large chunk" warning threshold — recharts alone exceeds 500 kB
    // minified; splitting it into its own chunk and warning at 800 kB avoids
    // false-positive noise while still catching genuinely oversized chunks.
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        manualChunks: {
          // Heavy visualisation lib — only loaded on chart-bearing pages
          recharts: ['recharts'],
          // Animation runtime — deferred until first animated component mounts
          motion: ['framer-motion'],
          // Icon tree-shaking is per-import, but bundling together avoids
          // Rollup emitting hundreds of tiny 1-2 kB icon chunks
          icons: ['lucide-react'],
          // React core — shared by every route, isolated for long-term caching
          'react-vendor': ['react', 'react-dom'],
        }
      }
    }
  }
})
