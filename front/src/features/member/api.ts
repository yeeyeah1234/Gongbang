import { apiClient } from '../../api/client'
import type { ApiResponse, PageResponse } from '../../api/types'
import type { ContestCardItem } from '../contest/types'
import type {
  Award,
  AwardInput,
  BookmarkSort,
  Career,
  CareerInput,
  Certificate,
  CertificateInput,
  MyComment,
  MyPost,
  MyProfile,
  PublicProfile,
  UpdateProfileRequest,
  VerifyStatus,
} from './types'

// MY-01
export async function fetchMyProfile() {
  return (await apiClient.get<ApiResponse<MyProfile>>('/api/members/me')).data.data
}
export async function updateMyProfile(body: UpdateProfileRequest) {
  return (await apiClient.patch<ApiResponse<MyProfile>>('/api/members/me', body)).data.data
}

// MY-05
export async function fetchPublicProfile(memberId: number) {
  return (await apiClient.get<ApiResponse<PublicProfile>>(`/api/members/${memberId}/profile`)).data
    .data
}

// MY-07·08·09 이력 (같은 모양이라 종류만 바꿔 부른다)
export type HistoryKind = 'careers' | 'certificates' | 'awards'
type HistoryMap = {
  careers: { item: Career; input: CareerInput }
  certificates: { item: Certificate; input: CertificateInput }
  awards: { item: Award; input: AwardInput }
}

export async function fetchHistory<K extends HistoryKind>(kind: K) {
  return (await apiClient.get<ApiResponse<HistoryMap[K]['item'][]>>(`/api/members/me/${kind}`)).data
    .data
}
export async function addHistory<K extends HistoryKind>(kind: K, input: HistoryMap[K]['input']) {
  return (
    await apiClient.post<ApiResponse<HistoryMap[K]['item']>>(`/api/members/me/${kind}`, input)
  ).data.data
}
export async function updateHistory<K extends HistoryKind>(
  kind: K,
  id: number,
  input: HistoryMap[K]['input'],
) {
  return (
    await apiClient.patch<ApiResponse<HistoryMap[K]['item']>>(
      `/api/members/me/${kind}/${id}`,
      input,
    )
  ).data.data
}
export async function deleteHistory(kind: HistoryKind, id: number) {
  await apiClient.delete(`/api/members/me/${kind}/${id}`)
}
// MY-08 ⑤ 인증 요청 (실명은 이때만)
export async function requestVerify(
  kind: 'certificates' | 'awards',
  id: number,
  realName: string,
  proofFileId: number | null,
) {
  return (
    await apiClient.post<ApiResponse<{ verifyStatus: VerifyStatus }>>(
      `/api/members/me/${kind}/${id}/verify`,
      {
        realName,
        proofFileId,
      },
    )
  ).data.data
}

// MY-02 (게시판 담당 김수형·댓글 김건우 테이블이 develop에 들어오면 동작)
export async function fetchMyPosts(page: number) {
  return (
    await apiClient.get<ApiResponse<PageResponse<MyPost>>>('/api/members/me/posts', {
      params: { page, size: 10 },
    })
  ).data.data
}
export async function fetchMyComments(page: number) {
  return (
    await apiClient.get<ApiResponse<PageResponse<MyComment>>>('/api/members/me/comments', {
      params: { page, size: 10 },
    })
  ).data.data
}
export async function deleteMyPosts(postIds: number[]) {
  return (
    await apiClient.post<ApiResponse<{ deletedCount: number }>>('/api/members/me/posts/delete', {
      postIds,
    })
  ).data.data
}

// MY-03
export async function fetchBookmarks(sort: BookmarkSort, page: number) {
  return (
    await apiClient.get<ApiResponse<PageResponse<ContestCardItem>>>('/api/members/me/bookmarks', {
      params: { sort, page, size: 20 },
    })
  ).data.data
}

// MY-04
export async function changePassword(currentPassword: string, newPassword: string) {
  await apiClient.patch('/api/members/me/password', { currentPassword, newPassword })
}

// MY-11
export async function withdraw(password: string, confirmText: string) {
  return (
    await apiClient.delete<ApiResponse<{ restoreDeadline: string }>>('/api/members/me', {
      data: { password, confirmText },
    })
  ).data.data
}
