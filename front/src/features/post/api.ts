import { apiClient } from '../../api/client'
import type { ApiResponse, PageResponse } from '../../api/types'
import type { PostCreateRequest, PostCreateResponse, PostListItem } from './types'

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
