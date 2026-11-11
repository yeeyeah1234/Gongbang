import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 개발 중에는 /api 요청을 로컬 백엔드(8080)로 넘긴다
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
