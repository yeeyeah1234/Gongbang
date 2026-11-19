import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getApiError } from '../../api/errors'
import { useAuthStore, useIsLoggedIn as useAuthIsLoggedIn } from '../auth/store'
import { fetchCategories, fetchContest, fetchContests, updateBookmark } from './api'
import type { ContestSort } from './types'

// 인증 모듈(정진우)의 로그인 사용자 조회로 바꿨다 (11/16). 원래 자리는 그대로 두고 그 값을 돌려준다.
export function useIsLoggedIn() {
  return useAuthIsLoggedIn()
}

// MAIN-06 ③: 북마크 표시(isBookmarked)는 로그인 여부에 따라 달라서, 쿼리 키에 넣어 로그인·로그아웃 때 다시 부른다.
// 새로 고침 직후 로그인 복원(AuthBootstrap)이 끝나기 전에 비회원으로 먼저 부르지 않도록 ready까지 기다린다.
export type Viewer = 'member' | 'guest'

export function useViewer() {
  const isLoggedIn = useAuthIsLoggedIn()
  const ready = useAuthStore((state) => state.initialized)
  return { viewer: (isLoggedIn ? 'member' : 'guest') as Viewer, ready }
}

// MAIN-01 ③: 한 번에 20개씩
const CONTEST_PAGE_SIZE = 20

export const contestKeys = {
  all: ['contests'] as const,
  list: (filter: { sort: ContestSort; category?: string }, viewer: Viewer) =>
    [...contestKeys.all, 'list', filter, viewer] as const,
  detail: (contestId: number, viewer: Viewer) => ['contest-detail', contestId, viewer] as const,
}

// MAIN-01 ①: 메인은 진행 중인 공모전만 (status=active)
export function useContestList(filter: { sort: ContestSort; category?: string }) {
  const { viewer, ready } = useViewer()
  return useInfiniteQuery({
    queryKey: contestKeys.list(filter, viewer),
    enabled: ready,
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
  const { viewer, ready } = useViewer()
  return useQuery({
    queryKey: contestKeys.detail(contestId, viewer),
    enabled: ready,
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
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: contestKeys.all })
      // 검색 결과 카드도 같은 북마크 값을 보여 준다 (SRCH-02)
      void queryClient.invalidateQueries({ queryKey: ['search'] })
    },
  })
}

// MAIN-06 ④·MAIN-02 ④: 북마크 요청이 실패했을 때 띄울 문구. 서버 문구가 있으면 그걸 쓰고,
// 로그인이 풀린 경우(401)는 공통 axios가 로그인 화면으로 보내므로(AUTH-12 ③) 띄우지 않는다(null).
export function bookmarkErrorMessage(error: unknown, wasBookmarked: boolean): string | null {
  const { code, message } = getApiError(error)
  if (code === 'UNAUTHORIZED') return null
  if (message) return message
  return wasBookmarked
    ? '북마크를 해제하지 못했어요. 잠시 후 다시 시도해 주세요'
    : '북마크하지 못했어요. 잠시 후 다시 시도해 주세요'
}
