import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type {
  CommentCreatePayload,
  CommentItem,
  CommentTargetKind,
  CommentUpdatePayload,
} from './types'

function commentsPath(kind: CommentTargetKind, targetId: number): string {
  return kind === 'post'
    ? `/api/posts/${targetId}/comments`
    : `/api/board-posts/${targetId}/comments`
}

// NET-01 · BRD-005 댓글·대댓글 목록 조회 (비회원 가능)
export async function fetchComments(
  kind: CommentTargetKind,
  targetId: number,
): Promise<CommentItem[]> {
  return (await apiClient.get<ApiResponse<CommentItem[]>>(commentsPath(kind, targetId))).data.data
}

// NET-01 · BRD-005 댓글·답글 작성
export async function createComment(
  kind: CommentTargetKind,
  targetId: number,
  payload: CommentCreatePayload,
): Promise<{ commentId: number }> {
  return (
    await apiClient.post<ApiResponse<{ commentId: number }>>(commentsPath(kind, targetId), payload)
  ).data.data
}

// NET-01 · BRD-005 댓글·답글 수정
export async function updateComment(
  commentId: number,
  payload: CommentUpdatePayload,
): Promise<CommentItem> {
  return (await apiClient.patch<ApiResponse<CommentItem>>(`/api/comments/${commentId}`, payload))
    .data.data
}

// NET-01 · BRD-005 댓글·답글 삭제
export async function deleteComment(commentId: number): Promise<void> {
  await apiClient.delete<ApiResponse<null>>(`/api/comments/${commentId}`)
}
