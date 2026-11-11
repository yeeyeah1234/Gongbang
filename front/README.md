# 공방 프론트엔드

React + TypeScript + Vite. 라우팅은 React Router, 서버 데이터는 TanStack Query, 전역 상태는 Zustand, HTTP는 axios, 스타일은 Tailwind CSS를 씁니다.

## 명령어

```bash
npm install          # 의존성 설치
npm run dev          # 개발 서버 (/api 요청은 localhost:8080 백엔드로 전달)
npm run build        # 타입 검사 + 빌드
npm run lint         # ESLint
npm run format       # Prettier로 포맷 (커밋 전에 실행)
npm run format:check # 포맷 검사만
```

## 폴더

- `src/api/client.ts` — 공통 axios 인스턴스 (401 처리는 여기에만)
- `src/lib/queryClient.ts` — TanStack Query 클라이언트
- `src/router.tsx` — 라우트 정의
- `src/styles/theme.css` — Tailwind 디자인 토큰 (STYLE_GUIDE 6장)
- `src/pages/` — 화면
