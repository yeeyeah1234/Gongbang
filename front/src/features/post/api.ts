import { apiClient } from '../../api/client'
import type { ApiResponse, PageResponse } from '../../api/types'
import type {
  ApplicationCreateResponse,
  PostCreateRequest,
  PostCreateResponse,
  PostDetail,
  PostListItem,
  PostUpdateRequest,
} from './types'

// PST-001 모집글 목록. 비회원도 부를 수 있다 (⑤). page는 0부터.
export async function fetchPosts(params: { page: number; size: number }) {
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
