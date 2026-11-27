import { apiClient } from '../../api/client'
import type { ApiResponse, PageResponse } from '../../api/types'
import type {
  ApplicantList,
  ApplicantStatus,
  ApplicationAcceptResponse,
  ApplicationCreateResponse,
  PostCreateRequest,
  PostCreateResponse,
  PostCompleteResponse,
  PostDetail,
  PostImagesResponse,
  PostListItem,
  PostUpdateRequest,
} from './types'

// PST-001 모집글 목록. 비회원도 부를 수 있다 (⑤). page는 0부터.
// ⑩ contestId: 그 공모전 글만, recruiting: 지금 지원할 수 있는 글(모집중이고 종료일 전)만
export async function fetchPosts(params: {
  page: number
  size: number
  contestId?: number
  recruiting?: boolean
}) {
  const response = await apiClient.get<ApiResponse<PageResponse<PostListItem>>>('/api/posts', {
    params,
  })
  return response.data.data
}

// PST-002 모집글 작성 (로그인)
export async function createPost(body: PostCreateRequest) {
  const response = await apiClient.post<ApiResponse<PostCreateResponse>>('/api/posts', body)
  return response.data.data
}

// PST-005 모집글 상세. 비회원도 부를 수 있다. 로그인 회원이면 myStatus와 조회수(24시간에 한 번)가 달라진다.
export async function fetchPost(postId: number) {
  const response = await apiClient.get<ApiResponse<PostDetail>>(`/api/posts/${postId}`)
  return response.data.data
}

// PST-003 모집글 수정 (작성자). 수정된 상세를 돌려준다.
export async function updatePost({ postId, body }: { postId: number; body: PostUpdateRequest }) {
  const response = await apiClient.patch<ApiResponse<PostDetail>>(`/api/posts/${postId}`, body)
  return response.data.data
}

// PST-004 모집글 삭제 (작성자, 소프트 삭제)
export async function deletePost(postId: number) {
  await apiClient.delete(`/api/posts/${postId}`)
}

// REC-001 지원하기 (로그인)
export async function applyToPost(postId: number) {
  const response = await apiClient.post<ApiResponse<ApplicationCreateResponse>>(
    `/api/posts/${postId}/applications`,
  )
  return response.data.data
}

// REC-002 지원 취소 (지원자 본인)
export async function cancelApplication(applicationId: number) {
  await apiClient.delete(`/api/applications/${applicationId}`)
}

// REC-003 지원자 목록 (작성자). 먼저 지원한 순.
export async function fetchApplicants(params: { postId: number; status: ApplicantStatus }) {
  const response = await apiClient.get<ApiResponse<ApplicantList>>(
    `/api/posts/${params.postId}/applications`,
    { params: { status: params.status } },
  )
  return response.data.data
}

// REC-004 지원자 수락 (작성자)
export async function acceptApplication(applicationId: number) {
  const response = await apiClient.patch<ApiResponse<ApplicationAcceptResponse>>(
    `/api/applications/${applicationId}/accept`,
  )
  return response.data.data
}

// REC-005 지원자 거절 (작성자)
export async function rejectApplication(applicationId: number) {
  await apiClient.patch(`/api/applications/${applicationId}/reject`)
}

// REC-006 모집 완료 (작성자). 남은 대기 지원은 서버가 자동으로 거절한다.
export async function completePost(postId: number) {
  const response = await apiClient.patch<ApiResponse<PostCompleteResponse>>(
    `/api/posts/${postId}/complete`,
  )
  return response.data.data
}

// PST-007 공모전 사진 한 장 올리기 (작성자, 외부 공모전만). 맨 뒤에 붙고 남은 사진 전체를 돌려준다.
export async function uploadPostImage({ postId, file }: { postId: number; file: File }) {
  const form = new FormData()
  form.append('file', file)
  const response = await apiClient.post<ApiResponse<PostImagesResponse>>(
    `/api/posts/${postId}/images`,
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  return response.data.data
}

// PST-007 ④ 공모전 사진 삭제 (소프트 삭제)
export async function deletePostImage({ postId, imageId }: { postId: number; imageId: number }) {
  const response = await apiClient.delete<ApiResponse<PostImagesResponse>>(
    `/api/posts/${postId}/images/${imageId}`,
  )
  return response.data.data
}

// PST-007 ③ 공모전 사진 순서 변경: 남은 사진 ID 전부를 새 순서대로
export async function reorderPostImages({
  postId,
  imageIds,
}: {
  postId: number
  imageIds: number[]
}) {
  const response = await apiClient.patch<ApiResponse<PostImagesResponse>>(
    `/api/posts/${postId}/images/order`,
    { imageIds },
  )
  return response.data.data
}
