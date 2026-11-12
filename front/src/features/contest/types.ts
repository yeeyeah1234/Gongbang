// API 명세서 MAIN-01·04·05 GET /api/contests
export type ContestStatus = 'active' | 'all'
export type ContestSort = 'latest' | 'deadline' | 'views'

// 공모전 카드 (MAIN-06). 검색 결과·북마크 목록도 같은 모양을 쓴다.
export interface ContestCardItem {
  contestId: number
  title: string
  host: string
  categoryCode: string
  categoryName: string
  thumbnailUrl: string | null
  deadline: string // YYYY-MM-DD
  dDay: number // 마감 당일 0
  isClosed: boolean
  isBookmarked: boolean
}

// API 명세서 MAIN-02 GET /api/contests/{contestId}
export interface ContestDetail extends ContestCardItem {
  startDate: string | null // YYYY-MM-DD
  description: string | null // 크롤링한 원본 HTML. 화면에 넣기 전에 반드시 sanitizeDescription을 거친다
  sourceUrl: string
  viewCount: number
}

// API 명세서 MAIN-05 GET /api/categories
export interface Category {
  categoryCode: string
  name: string
  sortOrder: number
}
