import type { PageResponse } from '../../api/types'
import type { ContestCardItem } from '../contest/types'

// API 명세서 SRCH-02·05 GET /api/search
export type SearchType = 'all' | 'contest' | 'post'
export type SearchSort = 'accuracy' | 'latest' | 'deadline'

export interface SearchParams {
  keyword: string
  type: SearchType
  sort: SearchSort
  includeClosed: boolean
}

export interface SearchResult {
  contests: PageResponse<ContestCardItem>
  // TODO(확인 필요): 모집글 목록 항목(PST-001, 김수형) 타입이 정해지면 바꾼다. 지금 서버는 항상 빈 목록을 준다.
  posts: PageResponse<unknown>
}
