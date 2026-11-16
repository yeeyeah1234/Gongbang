import { apiClient } from '../../api/client'
import type { ApiResponse, PageResponse } from '../../api/types'
import type {
  NotificationCategory,
  NotificationItem,
  NotificationSetting,
  ServiceNotice,
} from './types'

export async function fetchUnreadCount() {
  return (await apiClient.get<ApiResponse<{ count: number }>>('/api/notifications/unread-count'))
    .data.data.count
}

export async function fetchNotifications(params: {
  page: number
  size: number
  unreadOnly?: boolean
  category?: NotificationCategory
}) {
  return (
    await apiClient.get<ApiResponse<PageResponse<NotificationItem>>>('/api/notifications', {
      params,
    })
  ).data.data
}

export async function markRead(id: number) {
  await apiClient.patch(`/api/notifications/${id}/read`)
}

export async function markAllRead() {
  await apiClient.patch('/api/notifications/read-all')
}

export async function deleteNotification(id: number) {
  await apiClient.delete(`/api/notifications/${id}`)
}

export async function deleteReadNotifications() {
  await apiClient.delete('/api/notifications/read')
}

// MY-10
export async function fetchNotificationSettings() {
  return (
    await apiClient.get<ApiResponse<{ settings: NotificationSetting[] }>>(
      '/api/notifications/settings',
    )
  ).data.data.settings
}

export async function saveNotificationSettings(settings: NotificationSetting[]) {
  return (
    await apiClient.put<ApiResponse<{ settings: NotificationSetting[] }>>(
      '/api/notifications/settings',
      { settings },
    )
  ).data.data.settings
}

// NOTI-07
export async function fetchServiceNotice(id: number) {
  return (await apiClient.get<ApiResponse<ServiceNotice>>(`/api/service-notices/${id}`)).data.data
}

// TODO(확인 필요): 팀 초대 수락·거절은 김수형 INT-005 API 경로가 정해지면 바꾼다 (지금 가정 경로)
export async function respondInvitation(invitationId: number, accept: boolean) {
  await apiClient.post(`/api/invitations/${invitationId}/${accept ? 'accept' : 'reject'}`)
}
