import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
} from '@tanstack/react-query'
import type { PageResponse } from '../../api/types'
import { useIsLoggedIn } from '../auth/store'
import {
  deleteNotification,
  deleteReadNotifications,
  fetchNotifications,
  fetchUnreadCount,
  markAllRead,
  markRead,
} from './api'
import type { NotificationCategory, NotificationItem } from './types'

export const notificationKeys = {
  all: ['notifications'] as const,
  unread: ['notifications', 'unread-count'] as const,
  recent: ['notifications', 'recent'] as const,
  list: (filter: { unreadOnly: boolean; category?: NotificationCategory }) =>
    ['notifications', 'list', filter] as const,
}

// NOTI-03 ③ 새 알림이 오면 숫자가 바로 올라간다.
// TODO(확인 필요): 실시간 방식(채팅 WebSocket 공유 vs 30초 확인)은 김건우와 협의 중 (AGENTS 2-2) — 그 전까지 30초마다 확인
export function useUnreadCount() {
  const loggedIn = useIsLoggedIn()
  return useQuery({
    queryKey: notificationKeys.unread,
    queryFn: fetchUnreadCount,
    enabled: loggedIn,
    refetchInterval: 30_000,
  })
}

// NOTI-02 ① 최근 5개
export function useRecentNotifications(enabled: boolean) {
  return useQuery({
    queryKey: notificationKeys.recent,
    queryFn: () => fetchNotifications({ page: 0, size: 5 }),
    retry: false,
    enabled,
  })
}

// NOTI-01 ⑤ 20개씩 더 불러오기
export function useNotificationList(filter: {
  unreadOnly: boolean
  category?: NotificationCategory
}) {
  return useInfiniteQuery({
    queryKey: notificationKeys.list(filter),
    queryFn: ({ pageParam }) => fetchNotifications({ page: pageParam, size: 20, ...filter }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  })
}

function useInvalidateAll() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: notificationKeys.all })
}

export function useMarkRead() {
  const syncRead = useSyncRead()
  return useMutation({ mutationFn: markRead, onSuccess: (_, id) => syncRead(id) })
}

export function useMarkAllRead() {
  const syncRead = useSyncRead()
  return useMutation({ mutationFn: markAllRead, onSuccess: () => syncRead() })
}

// 서버 저장 성공 뒤 캐시를 먼저 갱신한다. 진행 중인 이전 조회가 읽음 표시를 되돌리지 않게 취소한다.
function useSyncRead() {
  const client = useQueryClient()
  return async (id?: number) => {
    await client.cancelQueries({ queryKey: notificationKeys.all })
    let wasUnread = false
    const updatePage = (page: PageResponse<NotificationItem>, unreadOnly = false) => {
      const items = page.items.map((item) => {
        if (id !== undefined && item.notificationId !== id) return item
        if (!item.isRead) wasUnread = true
        return { ...item, isRead: true }
      })
      return { ...page, items: unreadOnly ? items.filter((item) => !item.isRead) : items }
    }
    client.setQueryData<PageResponse<NotificationItem>>(notificationKeys.recent, (page) =>
      page ? updatePage(page) : page,
    )
    for (const [key] of client.getQueriesData({ queryKey: ['notifications', 'list'] })) {
      const filter = key[2] as { unreadOnly: boolean }
      client.setQueryData<InfiniteData<PageResponse<NotificationItem>>>(key, (data) =>
        data
          ? { ...data, pages: data.pages.map((page) => updatePage(page, filter.unreadOnly)) }
          : data,
      )
    }
    client.setQueryData<number>(notificationKeys.unread, (count) =>
      id === undefined ? 0 : count === undefined ? count : Math.max(0, count - (wasUnread ? 1 : 0)),
    )
    // 그동안 도착한 새 알림과 페이지 정보는 서버 값으로 다시 맞춘다.
    await client.invalidateQueries({ queryKey: notificationKeys.all })
  }
}

export function useDeleteNotification() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: deleteNotification, onSuccess: invalidate })
}

export function useDeleteReadNotifications() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: deleteReadNotifications, onSuccess: invalidate })
}
