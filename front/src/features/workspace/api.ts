import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { PinnedNotice, WorkspaceSummary } from './types'

// WS-02 헤더 정보. 팀원이 아니면 403 NOT_TEAM_MEMBER
export async function fetchWorkspaceSummary(teamId: number) {
  return (
    await apiClient.get<ApiResponse<WorkspaceSummary>>(`/api/teams/${teamId}/workspace-summary`)
  ).data.data
}

// NTC-05 고정 공지 배너. 없으면 null
export async function fetchPinnedNotice(teamId: number) {
  return (
    (await apiClient.get<ApiResponse<PinnedNotice | null>>(`/api/teams/${teamId}/notices/pinned`))
      .data.data ?? null
  )
}

// 공지 탭 배지: 내가 쓰지 않았고 아직 열어 보지 않은 공지 수
export async function fetchNoticeUnreadCount(teamId: number) {
  return (
    await apiClient.get<ApiResponse<{ count: number }>>(`/api/teams/${teamId}/notices/unread-count`)
  ).data.data.count
}
