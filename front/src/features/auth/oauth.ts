import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { TermsItem } from './types'

// AUTH-10 간편 로그인 (카카오·구글). 백엔드가 직접 리다이렉트하는 방식
export type OAuthProvider = 'kakao' | 'google'

export const PROVIDER_LABEL: Record<OAuthProvider, string> = { kakao: '카카오', google: '구글' }

export interface OAuthTokenResponse {
  accessToken: string | null
  isNewMember: boolean
  signupToken: string | null
  loginType: 'KAKAO' | 'GOOGLE' | null
  email: string | null
}

// 서버에 키가 설정돼 켜진 제공자
export async function fetchOAuthProviders() {
  const res = await apiClient.get<ApiResponse<{ providers: OAuthProvider[] }>>(
    '/api/auth/oauth/providers',
  )
  return res.data.data.providers
}

// ③ 1회용 키 → 로그인 토큰 (처음이면 signupToken)
export async function exchangeOAuthCode(code: string) {
  const res = await apiClient.post<ApiResponse<OAuthTokenResponse>>('/api/auth/oauth/token', {
    code,
  })
  return res.data.data
}

// ⑤ 추가 정보 입력 후 가입
export async function oauthSignup(body: {
  signupToken: string
  nickname: string
  terms: TermsItem[]
  interestCategoryCodes: string[]
  email?: string
  verificationToken?: string
}) {
  const res = await apiClient.post<ApiResponse<{ memberId: number; accessToken: string }>>(
    '/api/auth/oauth/signup',
    body,
  )
  return res.data.data
}

// ④ 돌아갈 화면은 이메일 로그인과 같다: 떠나기 전에 기억해 두고 /oauth/callback에서 꺼낸다
const REDIRECT_KEY = 'gongbang.oauth.redirect'

// ② 버튼을 누르면 페이지 전체가 백엔드 주소로 이동한다
export function startOAuthLogin(provider: OAuthProvider, redirectTo: string) {
  try {
    sessionStorage.setItem(REDIRECT_KEY, redirectTo)
  } catch {
    // 저장이 막혀 있으면 메인으로 돌아간다
  }
  window.location.href = `${import.meta.env.VITE_API_BASE_URL ?? ''}/oauth2/authorization/${provider}`
}

export function takeOAuthRedirect() {
  try {
    const value = sessionStorage.getItem(REDIRECT_KEY)
    sessionStorage.removeItem(REDIRECT_KEY)
    return value
  } catch {
    return null
  }
}
