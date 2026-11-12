import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchCategories, fetchContests, updateBookmark } from './api'
import type { ContestSort } from './types'

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
