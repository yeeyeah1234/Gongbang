import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { useViewer } from '../contest/hooks'
import { fetchPopularContests, fetchSearch } from './api'
import type { SearchParams } from './types'

const SEARCH_PAGE_SIZE = 20
// SRCH-02 ④: 결과가 없을 때 추천하는 인기 공모전 수 (API 명세서 기본값)
const POPULAR_SIZE = 5

// 공모전 카드의 북마크 표시가 로그인 여부에 따라 달라서 키에 viewer를 넣는다 (MAIN-06 ③)
export function useSearch(params: SearchParams) {
  const { viewer, ready } = useViewer()
  return useInfiniteQuery({
    queryKey: ['search', params, viewer],
    enabled: ready,
    queryFn: ({ pageParam }) => fetchSearch({ ...params, page: pageParam, size: SEARCH_PAGE_SIZE }),
    initialPageParam: 0,
    // 공모전 결과 기준으로 다음 페이지를 부른다 (모집글이 생기면 둘 중 하나라도 남았는지로 바꾼다)
    getNextPageParam: (lastPage) =>
      lastPage.contests.hasNext || lastPage.posts.hasNext ? lastPage.contests.page + 1 : undefined,
    retry: false,
  })
}

export function usePopularContests(enabled: boolean) {
  const { viewer, ready } = useViewer()
  return useQuery({
    queryKey: ['contests', 'popular', POPULAR_SIZE, viewer],
    queryFn: () => fetchPopularContests(POPULAR_SIZE),
    enabled: enabled && ready,
  })
}
