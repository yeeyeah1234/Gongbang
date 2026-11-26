import type { PageResponse } from '../../api/types'
import type { BoardListItem } from '../board/types'
import type { ContestCardItem } from '../contest/types'
import type { PostListItem } from '../post/types'

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
  posts: PageResponse<PostListItem> // 팀원 모집글 (PST-001 목록 항목)
  boardPosts: PageResponse<BoardListItem> // 커뮤니티 게시글 (BRD-001 목록 항목)
}
