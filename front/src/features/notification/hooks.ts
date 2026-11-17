import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useIsLoggedIn } from '../auth/store'
import {
  deleteNotification,
  deleteReadNotifications,
  fetchNotifications,
  fetchUnreadCount,
  markAllRead,
  markRead,
} from './api'
import type { NotificationCategory } from './types'

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
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: markRead, onSuccess: invalidate })
}

export function useMarkAllRead() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: markAllRead, onSuccess: invalidate })
}

export function useDeleteNotification() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: deleteNotification, onSuccess: invalidate })
}

export function useDeleteReadNotifications() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: deleteReadNotifications, onSuccess: invalidate })
}
