import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api/users': {
        target: 'http://localhost:9001',
        changeOrigin: true
      },
      '/api/lost-found': {
        target: 'http://localhost:9002',
        changeOrigin: true
      },
      '/api/upload': {
        target: 'http://localhost:9002',
        changeOrigin: true
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws-market': {
        target: 'http://localhost:9004',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
