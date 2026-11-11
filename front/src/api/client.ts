import axios from 'axios'

// 모든 API 요청은 이 인스턴스 하나로 보낸다 (컴포넌트에서 fetch·axios 직접 호출 금지)
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  withCredentials: true, // refresh 토큰 httpOnly 쿠키를 함께 보낸다
})

// 401이면 로그인 화면으로 보내는 처리는 여기에만 둔다
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      // TODO(확인 필요): refresh 토큰 재발급 흐름과 로그인 화면 경로는 인증 담당(정진우)과 맞춘다
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)
