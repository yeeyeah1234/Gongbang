// API 명세서 AUTH-01 로그인 응답 member
export interface LoginMemberInfo {
  memberId: number
  nickname: string
  profileImageUrl: string | null
  role: 'USER' | 'ADMIN'
}

export type VerificationPurpose = 'SIGNUP' | 'LOGIN' | 'RESET' | 'RESTORE'

export type TermsType = 'SERVICE' | 'PRIVACY' | 'MARKETING'

export interface TermsItem {
  type: TermsType
  version: string
  agreed: boolean
}

export interface SendCodeResponse {
  expiresAt: string
  resendAvailableAt: string
  // 메일 서버가 없는 로컬 개발에서만 온다
  devCode?: string | null
}

export interface LoginResponse {
  accessToken: string
  member: LoginMemberInfo
}

export interface SignupRequest {
  email: string
  verificationToken: string
  password: string
  nickname: string
  terms: TermsItem[]
  interestCategoryCodes: string[]
  desiredRoles?: string[]
  school?: string
  major?: string
  grade?: string
  bio?: string
}

export interface FindEmailResponse {
  maskedEmail: string
  loginType: 'EMAIL' | 'KAKAO'
  joinedAt: string
}
