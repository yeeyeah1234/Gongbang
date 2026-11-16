// API 명세서 MY-01 GET /api/members/me
export interface MyProfile {
  memberId: number
  email: string
  loginType: 'EMAIL' | 'KAKAO'
  role: 'USER' | 'ADMIN'
  nickname: string
  profileImageUrl: string | null
  bio: string | null
  school: string | null
  major: string | null
  grade: string | null
  interestCategoryCodes: string[]
  desiredRoles: string[]
  skillTags: string[]
  portfolioUrl: string | null
  createdAt: string
}

export interface UpdateProfileRequest {
  nickname?: string
  removeProfileImage?: boolean
  bio?: string
  school?: string
  major?: string
  grade?: string
  interestCategoryCodes?: string[]
  desiredRoles?: string[]
  skillTags?: string[]
  portfolioUrl?: string
}

export type VerifyStatus = 'NONE' | 'PENDING' | 'VERIFIED' | 'REJECTED'

// MY-07
export interface Career {
  careerId: number
  organization: string
  role: string
  startDate: string
  endDate: string | null
  description: string | null
  // 관련 링크 (선택)
  linkUrl: string | null
}
export type CareerInput = Omit<Career, 'careerId'>

// MY-08
export interface Certificate {
  certificateId: number
  name: string
  issuer: string
  acquiredDate: string
  certNumber: string | null
  proofFileId: number | null
  verifyStatus: VerifyStatus
}
export type CertificateInput = Omit<Certificate, 'certificateId' | 'verifyStatus'>

// MY-09
export interface Award {
  awardId: number
  contestName: string
  awardName: string
  awardedDate: string
  proofFileId: number | null
  verifyStatus: VerifyStatus
}
export type AwardInput = Omit<Award, 'awardId' | 'verifyStatus'>

// MY-05 공개 프로필 (이메일·가입 방식·실명·증빙 파일 없음)
export interface PublicProfile {
  memberId: number
  nickname: string
  profileImageUrl: string | null
  bio: string | null
  school: string | null
  major: string | null
  interestCategoryCodes: string[]
  desiredRoles: string[]
  skillTags: string[]
  portfolioUrl: string | null
  careers: {
    organization: string
    role: string
    startDate: string
    endDate: string | null
    linkUrl: string | null
  }[]
  certificates: { name: string; issuer: string; acquiredDate: string; verified: boolean }[]
  awards: { contestName: string; awardName: string; awardedDate: string; verified: boolean }[]
}

// MY-02
export interface MyPost {
  postId: number
  title: string
  createdAt: string
  commentCount: number
  status: string
}
export interface MyComment {
  commentId: number
  postId: number
  postTitle: string
  content: string
  createdAt: string
}

export type BookmarkSort = 'deadline' | 'bookmarked' | 'latest'
