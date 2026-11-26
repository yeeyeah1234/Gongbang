import { useMutation, useQuery } from '@tanstack/react-query'
import { createInvitation, fetchMyRecruitingPosts } from './api'
import type { CreateInvitationRequest } from './types'

export const invitationKeys = {
  myRecruitingPosts: ['invitations', 'my-recruiting-posts'] as const,
}

export function useMyRecruitingPosts(enabled: boolean) {
  return useQuery({
    queryKey: invitationKeys.myRecruitingPosts,
    queryFn: fetchMyRecruitingPosts,
    enabled,
    retry: false,
  })
}

export function useCreateInvitationMutation() {
  return useMutation({
    mutationFn: (payload: CreateInvitationRequest) => createInvitation(payload),
  })
}
