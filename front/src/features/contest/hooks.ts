import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useIsLoggedIn as useAuthIsLoggedIn } from '../auth/store'
import { fetchCategories, fetchContest, fetchContests, updateBookmark } from './api'
import type { ContestSort } from './types'

// 인증 모듈(정진우)의 로그인 사용자 조회로 바꿨다 (11/16). 원래 자리는 그대로 두고 그 값을 돌려준다.
export function useIsLoggedIn() {
  return useAuthIsLoggedIn()
}

// MAIN-01 ③: 한 번에 20개씩
const CONTEST_PAGE_SIZE = 20

export const contestKeys = {
  all: ['contests'] as const,
  list: (filter: { sort: ContestSort; category?: string }) =>
    [...contestKeys.all, 'list', filter] as const,
}

// MAIN-01 ①: 메인은 진행 중인 공모전만 (status=active)
export function useContestList(filter: { sort: ContestSort; category?: string }) {
  return useInfiniteQuery({
    queryKey: contestKeys.list(filter),
    queryFn: ({ pageParam }) =>
      fetchContests({
        status: 'active',
        sort: filter.sort,
        category: filter.category,
        page: pageParam,
        size: CONTEST_PAGE_SIZE,
      }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.page + 1 : undefined),
  })
}

// MAIN-02. 상세 API는 부를 때마다 조회수가 오르므로, 한 번 연 화면에서는 다시 부르지 않는다.
// (키를 'contests' 밖에 둬서 북마크 후 목록을 새로 고칠 때 같이 다시 부르지 않게 한다)
export function useContestDetail(contestId: number) {
  return useQuery({
    queryKey: ['contest-detail', contestId],
    queryFn: () => fetchContest(contestId),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: false,
  })
}

export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: fetchCategories,
    staleTime: Infinity, // 분야 목록은 거의 바뀌지 않는다
  })
}

export function useToggleBookmark() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ contestId, isBookmarked }: { contestId: number; isBookmarked: boolean }) =>
      updateBookmark(contestId, !isBookmarked),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contestKeys.all }),
  })
}
