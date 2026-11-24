import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createComment, deleteComment, fetchComments, updateComment } from './api'
import type { CommentCreatePayload, CommentTargetKind, CommentUpdatePayload } from './types'

export const commentKeys = {
  list: (kind: CommentTargetKind, targetId: number) => ['comments', kind, targetId] as const,
}

export function useComments(kind: CommentTargetKind, targetId: number) {
  return useQuery({
    queryKey: commentKeys.list(kind, targetId),
    queryFn: () => fetchComments(kind, targetId),
    enabled: Number.isInteger(targetId) && targetId > 0,
  })
}

export function useCreateCommentMutation(kind: CommentTargetKind, targetId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CommentCreatePayload) => createComment(kind, targetId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: commentKeys.list(kind, targetId) })
    },
  })
}

export function useUpdateCommentMutation(kind: CommentTargetKind, targetId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ commentId, payload }: { commentId: number; payload: CommentUpdatePayload }) =>
      updateComment(commentId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: commentKeys.list(kind, targetId) })
    },
  })
}

export function useDeleteCommentMutation(kind: CommentTargetKind, targetId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (commentId: number) => deleteComment(commentId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: commentKeys.list(kind, targetId) })
    },
  })
}
