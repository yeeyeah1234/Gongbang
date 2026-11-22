import {
  useInfiniteQuery,
  useQuery,
  useQueryClient,
  type InfiniteData,
} from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'
import type { PageResponse } from '../../../api/types'
import { workspaceKeys } from '../hooks'
import { fetchNotice, fetchNotices } from './api'
import type { NoticeListItemData } from './types'

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

export function useNoticeList(teamId: number) {
  return useInfiniteQuery({
    queryKey: noticeKeys.list(teamId),
    queryFn: ({ pageParam }) => fetchNotices(teamId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
    retry: false,
  })
}

export function useNoticeDetail(teamId: number, noticeId: number | null) {
  return useQuery({
    queryKey: noticeKeys.detail(teamId, noticeId),
    queryFn: () => fetchNotice(noticeId!),
    enabled: noticeId !== null,
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
      client.setQueryData<ListCache>(
        noticeKeys.list(teamId),
        (data) =>
          data && mapItems(data, (items) => items.filter((item) => item.noticeId !== noticeId)),
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
