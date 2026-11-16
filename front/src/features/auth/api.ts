import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type {
  FindEmailResponse,
  LoginResponse,
  SendCodeResponse,
  SignupRequest,
  VerificationPurpose,
} from './types'

// AUTH-05
// newSignup: 탈퇴 신청한 이메일로 [새로 가입하기]를 고른 경우 (AUTH-05 ⑦)
export async function sendCode(email: string, purpose: VerificationPurpose, newSignup = false) {
  const res = await apiClient.post<ApiResponse<SendCodeResponse>>('/api/auth/email/send-code', {
    email,
    purpose,
    ...(newSignup ? { newSignup: true } : {}),
  })
  return res.data.data
}

export async function verifyCode(email: string, purpose: VerificationPurpose, code: string) {
  const res = await apiClient.post<ApiResponse<{ verificationToken: string }>>(
    '/api/auth/email/verify',
    { email, purpose, code },
  )
  return res.data.data.verificationToken
}

// AUTH-06
export async function checkNickname(nickname: string) {
  const res = await apiClient.get<ApiResponse<{ available: boolean }>>(
    '/api/members/check-nickname',
    { params: { nickname } },
  )
  return res.data.data.available
}

// AUTH-04·07
export async function signup(request: SignupRequest) {
  const res = await apiClient.post<ApiResponse<{ memberId: number; accessToken: string }>>(
    '/api/auth/signup',
    request,
  )
  return res.data.data
}

// AUTH-01
export async function login(params: {
  email: string
  password: string
  rememberMe: boolean
  extraVerificationToken?: string
}) {
  const res = await apiClient.post<ApiResponse<LoginResponse>>('/api/auth/login', params)
  return res.data.data
}

// AUTH-11
export async function logout() {
  await apiClient.post('/api/auth/logout')
}

// AUTH-02
export async function findEmail(nickname: string) {
  const res = await apiClient.get<ApiResponse<FindEmailResponse>>('/api/auth/find-email', {
    params: { nickname },
  })
  return res.data.data
}

export async function resetPassword(email: string, verificationToken: string, newPassword: string) {
  await apiClient.post('/api/auth/password/reset', { email, verificationToken, newPassword })
}

// MY-11 ⑥
export async function restoreAccount(email: string, verificationToken: string) {
  const res = await apiClient.post<ApiResponse<{ accessToken: string }>>('/api/auth/restore', {
    email,
    verificationToken,
  })
  return res.data.data.accessToken
}
