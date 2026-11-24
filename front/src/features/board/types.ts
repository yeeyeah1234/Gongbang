// API 명세서 BRD-001~008 /api/board-posts

// DB 명세서 board_post.category
export type BoardCategory = 'CONTEST' | 'TIP' | 'SHOWCASE'

// BRD-001 ③ 말머리 이름
export const BOARD_CATEGORY_LABELS: Record<BoardCategory, string> = {
  CONTEST: '공모전 이야기',
  TIP: '노하우',
  SHOWCASE: '결과물 자랑',
}

export const BOARD_CATEGORIES: BoardCategory[] = ['CONTEST', 'TIP', 'SHOWCASE']

// BRD-001 ⑨ 정렬
export type BoardSort = 'latest' | 'popular'

export interface BoardAuthor {
  memberId: number
  nickname: string | null // 탈퇴한 회원은 서버가 「탈퇴한 사용자」로 준다
}

export interface BoardListItem {
  boardPostId: number
  category: BoardCategory
  title: string
  contest: { contestId: number; title: string } | null
  author: BoardAuthor
  createdAt: string // 한국 시간 YYYY-MM-DDTHH:mm:ss
  viewCount: number
  likeCount: number
}

export interface BoardImage {
  imageId: number
  url: string
}

export interface BoardDetail {
  boardPostId: number
  category: BoardCategory
  title: string
  content: string
  contest: {
    contestId: number
    title: string
    categoryName: string
    deadline: string // YYYY-MM-DD
    dDay: number
  } | null
  linkUrl: string | null
  images: BoardImage[]
  author: BoardAuthor & { profileImageUrl: string | null }
  createdAt: string
  updatedAt: string
  viewCount: number
  likeCount: number
  isLiked: boolean
  isAuthor: boolean
}

// BRD-002·003 작성·수정 (수정도 화면의 모든 값을 보낸다)
export interface BoardPostRequest {
  category: BoardCategory
  contestId: number | null
  title: string
  content: string
  linkUrl: string | null
}

export interface BoardLikeResponse {
  likeCount: number
  isLiked: boolean
}
