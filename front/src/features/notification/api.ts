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

// INT-005 명세: 수락 accept / 거절 decline
export async function respondInvitation(invitationId: number, accept: boolean) {
  await apiClient.post(`/api/invitations/${invitationId}/${accept ? 'accept' : 'decline'}`)
}

export async function respondAdminInvitation(invitationId: number, accept: boolean) {
  await apiClient.post(`/api/admin-invitations/${invitationId}/${accept ? 'accept' : 'decline'}`)
}
