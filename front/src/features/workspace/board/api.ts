import { apiClient } from '../../../api/client'
import type { ApiResponse } from '../../../api/types'
import type { WhiteboardListItem, WhiteboardRenameRequest, WhiteboardTitleResponse } from './types'

// WB-01 보드 목록 (최근 수정순)
export async function fetchWhiteboards(teamId: number) {
  return (
    await apiClient.get<ApiResponse<WhiteboardListItem[]>>(`/api/teams/${teamId}/whiteboards`)
  ).data.data
}

// WB-01 ② 새 보드. 30개 초과 시 409 WHITEBOARD_LIMIT
export async function createWhiteboard(teamId: number) {
  return (
    await apiClient.post<ApiResponse<WhiteboardTitleResponse>>(`/api/teams/${teamId}/whiteboards`)
  ).data.data
}

// WB-01 ① 보드 이름 바꾸기
export async function renameWhiteboard(whiteboardId: number, body: WhiteboardRenameRequest) {
  return (
    await apiClient.patch<ApiResponse<WhiteboardTitleResponse>>(
      `/api/whiteboards/${whiteboardId}`,
      body,
    )
  ).data.data
}

// WB-01 ① 보드 삭제 (만든 사람·팀장만, 아니면 403 NOT_WHITEBOARD_AUTHOR)
export async function deleteWhiteboard(whiteboardId: number) {
  await apiClient.delete(`/api/whiteboards/${whiteboardId}`)
}
