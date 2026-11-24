import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'

export type ReviewStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'
export interface Verification {
  id: number
  memberId: number
  nickname: string
  targetType: 'CERTIFICATE' | 'AWARD'
  targetId: number
  title: string
  description: string
  proofFileId: number
  proofAvailable: boolean
  status: ReviewStatus
  rejectReason: string | null
  reviewerId: number | null
  requestedAt: string | null
  reviewedAt: string | null
}
interface VerificationPage {
  content: Verification[]
  totalElements: number
  totalPages: number
  number: number
}
export async function fetchVerifications(status: ReviewStatus, page: number) {
  return (
    await apiClient.get<ApiResponse<VerificationPage>>('/api/admin/verifications', {
      params: { status, page },
    })
  ).data.data
}
export async function reviewVerification(
  id: number,
  status: Exclude<ReviewStatus, 'PENDING'>,
  rejectReason?: string,
) {
  await apiClient.patch(`/api/admin/verifications/${id}`, { status, rejectReason })
}
