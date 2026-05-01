import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:18089',
        changeOrigin: true,
      },
      '/v3': {
        target: 'http://127.0.0.1:18089',
        changeOrigin: true,
      },
      '/swagger-ui': {
        target: 'http://127.0.0.1:18089',
        changeOrigin: true,
      },
    },
  },
})
