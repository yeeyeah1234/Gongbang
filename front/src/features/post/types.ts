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
