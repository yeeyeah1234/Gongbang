import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { ChatMessagePageApiResponse, ChatRoomApiItem, TeamMemberApiItem } from './types'

// NET-06 팀원 목록 조회
export async function fetchTeamMembers(teamId: number): Promise<TeamMemberApiItem[]> {
  return (await apiClient.get<ApiResponse<TeamMemberApiItem[]>>(`/api/teams/${teamId}/members`))
    .data.data
}

// NET-06 팀원 강제 퇴장
export async function kickTeamMember(
  teamId: number,
  memberId: number,
): Promise<{ currentCount: number }> {
  return (
    await apiClient.delete<ApiResponse<{ currentCount: number }>>(
      `/api/teams/${teamId}/members/${memberId}`,
    )
  ).data.data
}

// NET-06 팀장 위임
export async function delegateTeamLeader(teamId: number, memberId: number): Promise<void> {
  await apiClient.patch<ApiResponse<null>>(`/api/teams/${teamId}/leader`, { memberId })
}

// NET-04 내 채팅방 목록 조회
export async function fetchChatRooms(teamId: number): Promise<ChatRoomApiItem[]> {
  return (await apiClient.get<ApiResponse<ChatRoomApiItem[]>>(`/api/teams/${teamId}/chat-rooms`))
    .data.data
}

// NET-04 1:1 채팅방 열기 (있으면 기존 ID 반환, 없으면 생성)
export async function openOrCreateDirectChatRoom(
  teamId: number,
  memberId: number,
): Promise<{ roomId: number }> {
  return (
    await apiClient.post<ApiResponse<{ roomId: number }>>(
      `/api/teams/${teamId}/chat-rooms/direct`,
      { memberId },
    )
  ).data.data
}

// NET-05 채팅방 메시지 커서 페이징 조회
export async function fetchChatMessages(
  roomId: number,
  before?: number,
  size = 30,
): Promise<ChatMessagePageApiResponse> {
  return (
    await apiClient.get<ApiResponse<ChatMessagePageApiResponse>>(
      `/api/chat-rooms/${roomId}/messages`,
      {
        params: {
          ...(before !== undefined ? { before } : {}),
          size,
        },
      },
    )
  ).data.data
}

// NET-05 채팅방 읽음 처리
export async function markChatRoomAsRead(roomId: number, lastReadMessageId: number): Promise<void> {
  await apiClient.patch<ApiResponse<null>>(`/api/chat-rooms/${roomId}/read`, {
    lastReadMessageId,
  })
}
