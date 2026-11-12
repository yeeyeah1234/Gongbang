// 모든 API 공통 응답 형식 (AGENTS.md 5장)
export interface ApiResponse<T> {
  success: boolean
  data: T
  error: { code: string; message: string } | null
}

// 목록 응답 (용어 사전 「목록 응답」). page는 0부터, 무한 스크롤은 hasNext만 써도 된다.
export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalCount: number
  hasNext: boolean
}
