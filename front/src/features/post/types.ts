// API 명세서 PST-001 GET /api/posts

// DB 명세서 recruit_post.status
export type PostStatus = 'RECRUITING' | 'COMPLETED'

export interface PostContestInfo {
  contestId: number | null // 외부 공모전이면 null
  title: string
  categoryName: string | null
  isExternal: boolean // 직접 입력한 외부 공모전이면 화면에 「외부」 표시 (PST-001 ②)
  deadline: string // YYYY-MM-DD
}

export interface PostAuthor {
  memberId: number
  nickname: string | null // 탈퇴한 회원은 서버가 「탈퇴한 사용자」로 준다
}

export interface PostListItem {
  postId: number
  title: string
  contest: PostContestInfo | null
  author: PostAuthor
  createdAt: string // 한국 시간 YYYY-MM-DDTHH:mm:ss
  viewCount: number
  currentCount: number // 작성자 포함 확정 팀원 수 (PST-001 ③)
  targetCount: number
  status: PostStatus
}

// API 명세서 PST-002 POST /api/posts. contestId와 external 중 하나만 보낸다.
export interface PostExternalContestInput {
  title: string
  categoryCode: string
  deadline: string // YYYY-MM-DD
  url?: string
}

export interface PostCreateRequest {
  contestId?: number
  external?: PostExternalContestInput
  title: string
  targetCount: number
  recruitStartDate: string // YYYY-MM-DD
  recruitEndDate: string // YYYY-MM-DD
  relatedUrl?: string
  content: string
}

export interface PostCreateResponse {
  postId: number
}

// API 명세서 PST-005 GET /api/posts/{postId}. myStatus로 버튼을 나눈다 (PST-006).
export type PostMyStatus = 'NONE' | 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'AUTHOR' | 'LEFT'

export interface PostDetailContest {
  contestId: number | null // 외부 공모전이면 null
  title: string
  categoryName: string | null
  isExternal: boolean
  deadline: string // YYYY-MM-DD
  dDay: number // 마감 당일 0, 지나면 음수
  url: string | null // 외부 공모전 링크 (서비스 공모전이면 null)
}

export interface PostDetail {
  postId: number
  title: string
  content: string
  contest: PostDetailContest
  imageUrl: string | null
  author: PostAuthor & { profileImageUrl: string | null }
  createdAt: string // 한국 시간 YYYY-MM-DDTHH:mm:ss
  viewCount: number
  currentCount: number
  targetCount: number
  status: PostStatus
  recruitStartDate: string
  recruitEndDate: string
  relatedUrl: string | null
  myStatus: PostMyStatus
  myApplicationId: number | null
}

// API 명세서 REC-001
export interface ApplicationCreateResponse {
  applicationId: number
}
