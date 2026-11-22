import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
} from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'
import type { PageResponse } from '../../../api/types'
import { fetchPinnedNotice } from '../api'
import { useWorkspace } from '../context'
import { workspaceKeys } from '../hooks'
import {
  createNotice,
  deleteNotice,
  fetchNotice,
  fetchNotices,
  pinNotice,
  unpinNotice,
  updateNotice,
} from './api'
import type {
  NoticeCreateRequest,
  NoticeDetailData,
  NoticeListItemData,
  NoticeUpdateRequest,
} from './types'

// workspaceKeys.notice 아래에 두어 refreshNoticeIndicators()가 목록·상세까지 함께 갱신한다
export const noticeKeys = {
  list: (teamId: number) => [...workspaceKeys.notice(teamId), 'list'] as const,
  details: (teamId: number) => [...workspaceKeys.notice(teamId), 'detail'] as const,
  detail: (teamId: number, noticeId: number | null) =>
    [...noticeKeys.details(teamId), noticeId] as const,
}

type ListCache = InfiniteData<PageResponse<NoticeListItemData>>

function mapItems(data: ListCache, map: (items: NoticeListItemData[]) => NoticeListItemData[]) {
  return { ...data, pages: data.pages.map((page) => ({ ...page, items: map(page.items) })) }
}

// page 방식이라 그사이 새 글이 끼면 다음 쪽에 같은 공지가 또 온다
export function uniqueNotices(data: ListCache | undefined) {
  return [
    ...new Map(
      data?.pages.flatMap((page) => page.items).map((item) => [item.noticeId, item]),
    ).values(),
  ]
}

function withoutNotice(data: ListCache | undefined, noticeId: number | null) {
  return data && mapItems(data, (items) => items.filter((item) => item.noticeId !== noticeId))
}

export function useNoticeList(teamId: number) {
  return useInfiniteQuery({
    queryKey: noticeKeys.list(teamId),
    queryFn: ({ pageParam }) => fetchNotices(teamId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
    retry: false,
  })
}

// enabled false: 지우는 중인 공지를 다시 불러와 404가 나지 않게
export function useNoticeDetail(teamId: number, noticeId: number | null, enabled = true) {
  return useQuery({
    queryKey: noticeKeys.detail(teamId, noticeId),
    queryFn: () => fetchNotice(noticeId!),
    enabled: enabled && noticeId !== null,
    retry: false,
  })
}

// 상세 조회 성공 = 읽음. 여기서 refreshNoticeIndicators()를 부르면 상세도 다시 조회되어 끝없이 반복되므로
// 목록 캐시의 점과 탭 배지만 따로 맞춘다.
export function useSyncNoticeRead(
  teamId: number,
  readId: number | undefined,
  readAt: number,
  list: ListCache | undefined,
) {
  const client = useQueryClient()

  // 목록이 상세보다 늦게 와도 점이 남지 않게 목록이 바뀔 때마다 본다
  useEffect(() => {
    if (readId === undefined || !list) return
    const unread = list.pages.some((page) =>
      page.items.some((item) => item.noticeId === readId && item.isUnread),
    )
    if (!unread) return
    const queryKey = noticeKeys.list(teamId)
    const next = mapItems(list, (items) =>
      items.map((item) => (item.noticeId === readId ? { ...item, isUnread: false } : item)),
    )
    const query = client.getQueryCache().find<ListCache>({ queryKey })
    // setQueryData는 오류까지 지워 다음 쪽 「다시 시도」가 사라지고 자동으로 또 부른다
    if (query?.state.status === 'error') query.setState({ data: next })
    else client.setQueryData<ListCache>(queryKey, next)
  }, [client, teamId, readId, list])

  // 읽음 기록보다 먼저 나간 배지 요청은 버린다. 첫 조회 중에는 invalidate만으로 취소되지 않는다
  useEffect(() => {
    if (readId === undefined) return
    const queryKey = workspaceKeys.noticeUnread(teamId)
    void client.cancelQueries({ queryKey }).then(() => client.invalidateQueries({ queryKey }))
  }, [client, teamId, readId, readAt])
}

// 없어진 공지를 목록에서 빼고 목록·배너·배지를 다시 불러온다 (상세는 다시 조회하면 또 404라 제외)
export function useForgetNotice(teamId: number) {
  const client = useQueryClient()
  return useCallback(
    (noticeId: number | null) => {
      client.setQueryData<ListCache>(noticeKeys.list(teamId), (data) =>
        withoutNotice(data, noticeId),
      )
      const detailSegment = noticeKeys.details(teamId).length - 1
      void client.invalidateQueries({
        queryKey: workspaceKeys.notice(teamId),
        predicate: (query) => query.queryKey[detailSegment] !== 'detail',
      })
    },
    [client, teamId],
  )
}

// NTC-04 고정 직전에 새로 받는다. fetchQuery만 부르면 먼저 나간 요청의 낡은 응답을 같이 받는다
export function useFetchLatestPinned(teamId: number) {
  const client = useQueryClient()
  return useCallback(async () => {
    const queryKey = workspaceKeys.pinnedNotice(teamId)
    await client.cancelQueries({ queryKey })
    return client.fetchQuery({
      queryKey,
      queryFn: () => fetchPinnedNotice(teamId),
      staleTime: 0,
      networkMode: 'always',
    })
  }, [client, teamId])
}

// 쓰기 공통
// - onSuccess는 탭을 떠나도 실행된다. 이동·모달 닫기는 화면이 mutate(…, { onSuccess })로 한다
// - networkMode always: 오프라인이면 멈춰 기다리지 않고 실패해야 요청 중 잠긴 모달이 풀린다
export function useCreateNotice() {
  const { teamId, refreshNoticeIndicators } = useWorkspace()
  return useMutation({
    mutationFn: (body: NoticeCreateRequest) => createNotice(teamId, body),
    networkMode: 'always',
    onSuccess: () => {
      void refreshNoticeIndicators()
    },
  })
}

export function useUpdateNotice() {
  const client = useQueryClient()
  const { teamId, refreshNoticeIndicators } = useWorkspace()
  return useMutation({
    mutationFn: ({ noticeId, body }: { noticeId: number; body: NoticeUpdateRequest }) =>
      updateNotice(noticeId, body),
    networkMode: 'always',
    onSuccess: (_, { noticeId, body }) => {
      // 다시 불러오기 전에 수정을 또 열어도 옛 내용으로 덮어쓰지 않게
      client.setQueryData<NoticeDetailData>(
        noticeKeys.detail(teamId, noticeId),
        (data) => data && { ...data, ...body },
      )
      void refreshNoticeIndicators()
    },
  })
}

export function useDeleteNotice() {
  const client = useQueryClient()
  const { teamId, refreshNoticeIndicators } = useWorkspace()
  return useMutation({
    mutationFn: deleteNotice,
    networkMode: 'always',
    onSuccess: (_, noticeId) => {
      // 먼저 빼야 목록 갱신이 실패해도 /notice가 지운 공지로 옮기지 않는다
      client.setQueryData<ListCache>(noticeKeys.list(teamId), (data) =>
        withoutNotice(data, noticeId),
      )
      void refreshNoticeIndicators()
    },
  })
}

export function useSetNoticePinned() {
  const { refreshNoticeIndicators } = useWorkspace()
  return useMutation({
    mutationFn: ({ noticeId, pinned }: { noticeId: number; pinned: boolean }) =>
      pinned ? pinNotice(noticeId) : unpinNotice(noticeId),
    networkMode: 'always',
    onSuccess: () => {
      void refreshNoticeIndicators()
    },
  })
}
