import { apiClient } from '../../api/client'
import type { ApiResponse, PageResponse } from '../../api/types'
import type { Category, ContestCardItem, ContestSort, ContestStatus } from './types'

export interface ContestListParams {
  status: ContestStatus
  sort: ContestSort
  category?: string // 없으면 「전체」
  keyword?: string
  page: number
  size: number
}

// MAIN-01·04·05
export async function fetchContests(params: ContestListParams) {
  const response = await apiClient.get<ApiResponse<PageResponse<ContestCardItem>>>(
    '/api/contests',
    { params },
  )
  return response.data.data
}

// MAIN-05
export async function fetchCategories() {
  const response = await apiClient.get<ApiResponse<Category[]>>('/api/categories')
  return response.data.data
}

// MY-03 북마크 추가·해제 (정진우 API, 카드에서 부름). 두 번 눌러도 같은 결과(멱등).
export async function updateBookmark(contestId: number, bookmarked: boolean) {
  const url = `/api/contests/${contestId}/bookmark`
  const response = bookmarked
    ? await apiClient.put<ApiResponse<{ isBookmarked: boolean }>>(url)
    : await apiClient.delete<ApiResponse<{ isBookmarked: boolean }>>(url)
  return response.data.data
}
