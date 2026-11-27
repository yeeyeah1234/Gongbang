import axios, { type InternalAxiosRequestConfig } from 'axios'
import { loginPathWithRedirect } from '../features/auth/redirect'
import { useAuthStore } from '../features/auth/store'

// 모든 API 요청은 이 인스턴스 하나로 보낸다 (컴포넌트에서 fetch·axios 직접 호출 금지)
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  withCredentials: true, // refresh 토큰 httpOnly 쿠키를 함께 보낸다
})

// 모든 API가 Authorization: Bearer {accessToken} 헤더를 쓴다 (AUTH-12)
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 로그인·재발급처럼 401 자체가 답인 요청은 재발급을 시도하지 않는다
const NO_REFRESH_PATHS = [
  '/api/auth/login',
  '/api/auth/token/refresh',
  '/api/auth/logout',
  '/api/auth/signup',
  '/api/auth/restore',
]

let refreshing: Promise<string | null> | null = null

// POST /api/auth/token/refresh (refresh 쿠키) → 새 access 토큰. 동시에 여러 요청이 401이어도 한 번만 부른다
export function refreshAccessToken() {
  if (!refreshing) {
    refreshing = axios
      .post<{ data: { accessToken: string } }>(
        `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/auth/token/refresh`,
        null,
        { withCredentials: true },
      )
      .then((res) => {
        const token = res.data.data.accessToken
        useAuthStore.getState().setLogin(token)
        return token
      })
      .catch(() => null)
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

// 401이면 로그인 화면으로 보내는 처리는 여기에만 둔다
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (axios.isAxiosError(error)) {
      const code = error.response?.data?.error?.code
      if (code === 'ACCOUNT_SUSPENDED') {
        useAuthStore.getState().clear()
        if (window.location.pathname !== '/login')
          window.location.replace('/login?reason=suspended')
        return Promise.reject(error)
      }
      if (code === 'NICKNAME_CHANGE_REQUIRED') {
        if (window.location.pathname !== '/mypage/nickname')
          window.location.replace('/mypage/nickname')
        return Promise.reject(error)
      }
    }
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const config = error.config as
        (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
      const url = config?.url ?? ''
      // access 토큰이 만료됐으면 refresh 쿠키로 한 번 다시 받아 같은 요청을 다시 보낸다
      if (config && !config._retried && !NO_REFRESH_PATHS.some((p) => url.startsWith(p))) {
        config._retried = true
        const token = await refreshAccessToken()
        if (token) {
          config.headers.Authorization = `Bearer ${token}`
          return apiClient(config)
        }
      }
      if (!NO_REFRESH_PATHS.some((p) => url.startsWith(p))) {
        // AUTH-12 ③: 로그인이 풀리면 지금 주소를 기억하고 로그인 화면으로 보낸다
        useAuthStore.getState().clear()
        const here = window.location.pathname + window.location.search
        window.location.href = loginPathWithRedirect(here)
      }
    }
    return Promise.reject(error)
  },
)
