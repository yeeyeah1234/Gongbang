// NET-01 · INT-004 · BRD-005 댓글·대댓글 타입 정의

export type CommentTargetKind = 'post' | 'board-post'

export interface CommentAuthor {
  memberId: number
  nickname: string | null
  profileImageUrl: string | null
}

export interface CommentItem {
  commentId: number
  author: CommentAuthor | null
  isPostAuthor: boolean
  content: string | null
  isSecret: boolean
  isDeleted: boolean
  createdAt: string
  updatedAt: string
  replies: CommentItem[]
}

export interface CommentCreatePayload {
  content: string
  isSecret: boolean
  parentId?: number | null
}

export interface CommentUpdatePayload {
  content: string
  isSecret: boolean
}
