import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { CreateInvitationRequest, CreateInvitationResponse, RecruitingPostItem } from './types'

export async function fetchMyRecruitingPosts(): Promise<RecruitingPostItem[]> {
  const { data } = await apiClient.get<ApiResponse<RecruitingPostItem[]>>(
    '/api/members/me/recruiting-posts',
  )
  return data.data ?? []
}

export async function createInvitation(
  payload: CreateInvitationRequest,
): Promise<CreateInvitationResponse> {
  const { data } = await apiClient.post<ApiResponse<CreateInvitationResponse>>(
    '/api/invitations',
    payload,
  )
  return data.data
}
