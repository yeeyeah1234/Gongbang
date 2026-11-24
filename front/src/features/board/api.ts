import { apiClient } from '../../api/client'
import type { ApiResponse, PageResponse } from '../../api/types'
import type {
  BoardCategory,
  BoardDetail,
  BoardImage,
  BoardLikeResponse,
  BoardListItem,
  BoardPostRequest,
  BoardSort,
} from './types'

const BASE = '/api/board-posts'

// BRD-001 게시글 목록. 비회원도 부를 수 있다 (②). page는 0부터.
export async function fetchBoardPosts(params: {
  category: BoardCategory | null
  sort: BoardSort
  page: number
  size: number
}) {
  const response = await apiClient.get<ApiResponse<PageResponse<BoardListItem>>>(BASE, {
    params: { ...params, category: params.category ?? undefined },
  })
  return response.data.data
}

// BRD-008 인기글 (최대 3개)
export async function fetchPopularBoardPosts(category: BoardCategory | null) {
  const response = await apiClient.get<ApiResponse<BoardListItem[]>>(`${BASE}/popular`, {
    params: { category: category ?? undefined },
  })
  return response.data.data
}

// BRD-004 상세. 로그인 회원이면 isLiked·isAuthor와 조회수(24시간에 한 번)가 달라진다.
export async function fetchBoardPost(boardPostId: number) {
  const response = await apiClient.get<ApiResponse<BoardDetail>>(`${BASE}/${boardPostId}`)
  return response.data.data
}

// BRD-002 작성 (로그인)
export async function createBoardPost(body: BoardPostRequest) {
  const response = await apiClient.post<ApiResponse<{ boardPostId: number }>>(BASE, body)
  return response.data.data
}

// BRD-003 수정 (작성자). 수정된 상세를 돌려준다.
export async function updateBoardPost({
  boardPostId,
  body,
}: {
  boardPostId: number
  body: BoardPostRequest
}) {
  const response = await apiClient.patch<ApiResponse<BoardDetail>>(`${BASE}/${boardPostId}`, body)
  return response.data.data
}

// BRD-003 ④ 삭제 (작성자, 소프트 삭제)
export async function deleteBoardPost(boardPostId: number) {
  await apiClient.delete(`${BASE}/${boardPostId}`)
}

// BRD-007 좋아요·취소
export async function likeBoardPost(boardPostId: number) {
  const response = await apiClient.put<ApiResponse<BoardLikeResponse>>(
    `${BASE}/${boardPostId}/like`,
  )
  return response.data.data
}

export async function unlikeBoardPost(boardPostId: number) {
  const response = await apiClient.delete<ApiResponse<BoardLikeResponse>>(
    `${BASE}/${boardPostId}/like`,
  )
  return response.data.data
}

type ImagesResponse = { images: BoardImage[] }

// BRD-006 결과물 사진. savePostImages가 쓰는 모양(postId)에 맞춘다.
export async function uploadBoardImage({ postId, file }: { postId: number; file: File }) {
  const form = new FormData()
  form.append('file', file)
  const response = await apiClient.post<ApiResponse<ImagesResponse>>(
    `${BASE}/${postId}/images`,
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  return response.data.data
}

export async function deleteBoardImage({ postId, imageId }: { postId: number; imageId: number }) {
  const response = await apiClient.delete<ApiResponse<ImagesResponse>>(
    `${BASE}/${postId}/images/${imageId}`,
  )
  return response.data.data
}

export async function reorderBoardImages({
  postId,
  imageIds,
}: {
  postId: number
  imageIds: number[]
}) {
  const response = await apiClient.patch<ApiResponse<ImagesResponse>>(
    `${BASE}/${postId}/images/order`,
    { imageIds },
  )
  return response.data.data
}
