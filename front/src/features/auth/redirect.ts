// AUTH-01 ⑥, AUTH-12 ③: 로그인 후 돌아갈 화면은 /login?redirect=/원래/주소 로 넘긴다 (11/16 정진우 결정)
export const LOGIN_PATH = '/login'

export function loginPathWithRedirect(redirectTo: string) {
  if (!redirectTo || redirectTo.startsWith('/login')) return LOGIN_PATH
  return `${LOGIN_PATH}?redirect=${encodeURIComponent(redirectTo)}`
}

// 다른 사이트로 보내지 않게 / 로 시작하는 주소만 받는다
export function safeRedirect(value: string | null) {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return '/'
  return value
}
