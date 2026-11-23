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

// 캐시 안의 공모전(카드·상세)에서 contestId가 같은 것의 isBookmarked만 바꾼다.
// 목록(무한 스크롤 페이지), 인기 목록, 검색 결과, 상세는 모양이 달라서 안쪽까지 찾아 바꾼다.
function withBookmark(data: unknown, contestId: number, isBookmarked: boolean): unknown {
  if (Array.isArray(data)) return data.map((item) => withBookmark(item, contestId, isBookmarked))
  if (data === null || typeof data !== 'object') return data
  const record = data as Record<string, unknown>
  if (record.contestId === contestId && typeof record.isBookmarked === 'boolean') {
    return { ...record, isBookmarked }
  }
  let changed = false
  const next: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(record)) {
    next[key] = withBookmark(value, contestId, isBookmarked)
    if (next[key] !== value) changed = true
  }
  return changed ? next : data
}

// 북마크를 보여 주는 캐시: 공모전 목록·인기('contests'), 검색 결과('search'), 상세('contest-detail')
const BOOKMARK_CACHE_KEYS = [contestKeys.all, ['search'], ['contest-detail']] as const

// MAIN-06 ③·MAIN-02 ④: 누르는 즉시 화면의 북마크 표시를 바꾸고(응답을 기다리지 않음), 실패하면 되돌린다.
export function useToggleBookmark() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ contestId, isBookmarked }: { contestId: number; isBookmarked: boolean }) =>
      updateBookmark(contestId, !isBookmarked),
    onMutate: async ({ contestId, isBookmarked }) => {
      await Promise.all(
        BOOKMARK_CACHE_KEYS.map((queryKey) => queryClient.cancelQueries({ queryKey })),
      )
      const snapshots = BOOKMARK_CACHE_KEYS.flatMap((queryKey) =>
        queryClient.getQueriesData({ queryKey }),
      )
      for (const queryKey of BOOKMARK_CACHE_KEYS) {
        queryClient.setQueriesData({ queryKey }, (old: unknown) =>
          withBookmark(old, contestId, !isBookmarked),
        )
      }
      return { snapshots }
    },
    onError: (_error, _variables, context) => {
      context?.snapshots.forEach(([queryKey, data]) => queryClient.setQueryData(queryKey, data))
    },
    onSuccess: () => {
      // 화면은 이미 바꿨으니 목록·검색은 다시 부르지 않고 오래된 값으로만 표시해 둔다(다음에 열 때 새로 받음).
      void queryClient.invalidateQueries({ queryKey: contestKeys.all, refetchType: 'none' })
      void queryClient.invalidateQueries({ queryKey: ['search'], refetchType: 'none' })
      // 마이페이지 북마크 목록(MY-03)에 바로 반영되게 한다
      void queryClient.invalidateQueries({ queryKey: ['members', 'me', 'bookmarks'] })
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
