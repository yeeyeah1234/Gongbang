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
      // AUTH-10 간편 로그인 시작·콜백은 백엔드(Spring Security)가 받는다
      '/oauth2': 'http://localhost:8080',
      '/login/oauth2': 'http://localhost:8080',
      // MY-01 올린 프로필 사진
      '/uploads': 'http://localhost:8080',
    },
  },
})
