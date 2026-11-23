import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { MyTeam, PinnedNotice, WorkspaceSummary } from './types'

// WS-02 헤더 정보 (GET /api/teams/{teamId}). 팀원이 아니면 403 NOT_TEAM_MEMBER
export async function fetchWorkspaceSummary(teamId: number) {
  return (await apiClient.get<ApiResponse<WorkspaceSummary>>(`/api/teams/${teamId}`)).data.data
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

// WS-08 워크스페이스 팀 목록 (GET /api/teams). 진행 중이 먼저, 종료가 뒤 (각각 최근 활동순)
export async function fetchMyTeams() {
  return (await apiClient.get<ApiResponse<MyTeam[]>>('/api/teams')).data.data
}

// WS-08 ⑥⑦ 팀 이름 변경 (PATCH /api/teams/{teamId}). 팀장이 아니면 403, 1~20자가 아니면 400 TEAM_NAME_INVALID
export async function renameTeam(teamId: number, name: string) {
  return (
    await apiClient.patch<ApiResponse<{ teamId: number; name: string }>>(`/api/teams/${teamId}`, {
      name,
    })
  ).data.data
}
