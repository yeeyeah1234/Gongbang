export interface RecruitingPostItem {
  postId: number
  title: string
  currentCount: number
  targetCount: number
}

export interface CreateInvitationRequest {
  postId: number
  inviteeId: number
}

export interface CreateInvitationResponse {
  invitationId: number
}

export interface AcceptInvitationResponse {
  teamId: number
}
