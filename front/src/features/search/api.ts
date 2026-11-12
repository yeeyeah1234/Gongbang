import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { ContestCardItem } from '../contest/types'
import type { SearchParams, SearchResult } from './types'

// SRCH-02·05 통합 검색
export async function fetchSearch(params: SearchParams & { page: number; size: number }) {
  const response = await apiClient.get<ApiResponse<SearchResult>>('/api/search', { params })
  return response.data.data
}

// SRCH-04 인기 공모전(추천)
export async function fetchPopularContests(size: number) {
  const response = await apiClient.get<ApiResponse<ContestCardItem[]>>('/api/contests/popular', {
    params: { size },
  })
  return response.data.data
}
