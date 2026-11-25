import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'

export function formatReportReason(reason?: string) {
  if (!reason) return ''
  const separator = reason.indexOf(':')
  const code = (separator < 0 ? reason : reason.slice(0, separator)).trim()
  const labels: Record<string, string> = {
    ABUSE: '욕설 / 비방 / 모욕',
    HATE: '차별 / 혐오 표현',
    BAD_NICKNAME: '부적절한 닉네임',
    SPAM: '광고 / 도배',
    INAPPROPRIATE: '부적절한 내용',
  }
  return (labels[code] ?? code) + (separator < 0 ? '' : reason.slice(separator))
}
export type Kind = 'notices' | 'members' | 'reports' | 'posts' | 'contests'
export const endpoints: Record<Kind, string> = {
  notices: 'service-notices',
  members: 'member-management',
  reports: 'reports',
  posts: 'posts',
  contests: 'contests',
}
export interface AdminItem {
  id: number
  title?: string
  content?: string
  policyChange?: boolean
  emailQueuedAt?: string | null
  nickname?: string | null
  email?: string | null
  status?: string
  role?: string
  loginType?: string
  loginFailCount?: number
  lockedUntil?: string | null
  suspendedUntil?: string | null
  warningCount?: number
  nicknameChangeRequired?: boolean
  sanctionReason?: string | null
  lastLoginAt?: string | null
  createdAt?: string | null
  deletedAt?: string | null
  reason?: string
  targetType?: string
  targetId?: number
  reporterId?: number
  reviewerId?: number
  reviewedAt?: string | null
  reviewNote?: string | null
  action?: string
  authorId?: number
  reportCount?: number
  endDate?: string
  categoryId?: number
  categoryName?: string
  sourceUrl?: string
}
export interface AdminPage {
  content: AdminItem[]
  totalElements: number
  totalPages: number
  number: number
}
export interface Category {
  id: number
  code: string
  name: string
  sortOrder: number
}
export interface Command {
  targetLabel?: string
  path: string
  method: 'post' | 'patch' | 'delete'
  body?: unknown
  label: string
}
export async function readAdmin<T>(
  path: string,
  params?: Record<string, string | number | boolean>,
) {
  return (await apiClient.get<ApiResponse<T>>(`/api/admin/${path}`, { params })).data.data
}
export async function commandAdmin(command: Command) {
  return apiClient.request({
    url: `/api/admin/${command.path}`,
    method: command.method,
    data: command.body,
  })
}
