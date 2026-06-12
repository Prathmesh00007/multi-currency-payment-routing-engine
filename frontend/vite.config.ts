import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  define: { 'global': 'window' },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'https://multi-currency-payment-routing-engine.onrender.com',
        changeOrigin: true,
      }
    }
  }
})
