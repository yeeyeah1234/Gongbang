import { useState } from 'react'
import { getErrorMessage } from '../../../api/errors'
import { CornerDownRightIcon, LockIcon, MessageSquareIcon } from '../../../components/icons/Icons'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import { useIsLoggedIn, useLoginMember } from '../../auth/store'
import { LoginRequiredModal } from '../../post/components/LoginRequiredModal'
import { ProfilePhoto } from '../../../components/ui/ProfilePhoto'
import { MemberActionPopover } from '../../report/components/MemberActionPopover'
import {
  useComments,
  useCreateCommentMutation,
  useDeleteCommentMutation,
  useUpdateCommentMutation,
} from '../hooks'
import type { CommentItem, CommentTargetKind } from '../types'

const MAX_COMMENT_LENGTH = 500

interface ReplyDraft {
  parentId: number
  parentNickname: string
  content: string
  isSecret: boolean
}

interface EditDraft {
  commentId: number
  content: string
  isSecret: boolean
}

interface CommentSectionProps {
  targetKind: CommentTargetKind
  targetId: number
  redirectTo: string
  onNotify: (message: string) => void
}

function formatCommentDateTime(iso: string): string {
  if (!iso || iso.length < 16) return iso
  return `${iso.slice(0, 10).replaceAll('-', '.')} ${iso.slice(11, 16)}`
}

function isEdited(item: CommentItem): boolean {
  return Boolean(item.createdAt && item.updatedAt && item.updatedAt > item.createdAt)
}

function countActiveComments(items: CommentItem[]): number {
  let total = 0
  for (const root of items) {
    if (!root.isDeleted) {
      total += 1
    }
    for (const reply of root.replies) {
      if (!reply.isDeleted) {
        total += 1
      }
    }
  }
  return total
}

/**
 * NET-01 · INT-004 · BRD-005 모집글 및 커뮤니티 게시글 공용 댓글·대댓글(1단계) 영역.
 * 댓글·답글 작성자의 프로필 사진 또는 닉네임 클릭 시 프로필 보기·신고하기 드롭다운(NET-02)을 제공한다.
 */
export function CommentSection({
  targetKind,
  targetId,
  redirectTo,
  onNotify,
}: CommentSectionProps) {
  const isLoggedIn = useIsLoggedIn()
  const loginMember = useLoginMember()

  const commentsQuery = useComments(targetKind, targetId)
  const createMutation = useCreateCommentMutation(targetKind, targetId)
  const updateMutation = useUpdateCommentMutation(targetKind, targetId)
  const deleteMutation = useDeleteCommentMutation(targetKind, targetId)

  const [newContent, setNewContent] = useState('')
  const [newSecret, setNewSecret] = useState(false)
  const [replyDraft, setReplyDraft] = useState<ReplyDraft | null>(null)
  const [editDraft, setEditDraft] = useState<EditDraft | null>(null)
  const [deletingCommentId, setDeletingCommentId] = useState<number | null>(null)
  const [loginModalOpen, setLoginModalOpen] = useState(false)

  const comments = commentsQuery.data ?? []
  const totalCount = countActiveComments(comments)

  const handleSubmitRoot = () => {
    if (!isLoggedIn) {
      setLoginModalOpen(true)
      return
    }
    const trimmed = newContent.trim()
    if (!trimmed || trimmed.length > MAX_COMMENT_LENGTH || createMutation.isPending) return

    createMutation.mutate(
      { content: trimmed, isSecret: newSecret, parentId: null },
      {
        onSuccess: () => {
          setNewContent('')
          setNewSecret(false)
          onNotify('댓글을 등록했어요')
        },
        onError: (err) => {
          onNotify(getErrorMessage(err, '댓글을 등록하지 못했어요'))
        },
      },
    )
  }

  const handleOpenReply = (root: CommentItem) => {
    if (!isLoggedIn) {
      setLoginModalOpen(true)
      return
    }
    if (replyDraft?.parentId === root.commentId) {
      setReplyDraft(null)
      return
    }
    setEditDraft(null)
    setReplyDraft({
      parentId: root.commentId,
      parentNickname: root.author?.nickname ?? '탈퇴한 사용자',
      content: '',
      isSecret: root.isSecret,
    })
  }

  const handleSubmitReply = () => {
    if (!replyDraft || createMutation.isPending) return
    const trimmed = replyDraft.content.trim()
    if (!trimmed || trimmed.length > MAX_COMMENT_LENGTH) return

    createMutation.mutate(
      {
        content: trimmed,
        isSecret: replyDraft.isSecret,
        parentId: replyDraft.parentId,
      },
      {
        onSuccess: () => {
          setReplyDraft(null)
          onNotify('답글을 등록했어요')
        },
        onError: (err) => {
          onNotify(getErrorMessage(err, '답글을 등록하지 못했어요'))
        },
      },
    )
  }

  const handleStartEdit = (item: CommentItem) => {
    setReplyDraft(null)
    setEditDraft({
      commentId: item.commentId,
      content: item.content ?? '',
      isSecret: item.isSecret,
    })
  }

  const handleSubmitEdit = () => {
    if (!editDraft || updateMutation.isPending) return
    const trimmed = editDraft.content.trim()
    if (!trimmed || trimmed.length > MAX_COMMENT_LENGTH) return

    updateMutation.mutate(
      {
        commentId: editDraft.commentId,
        payload: { content: trimmed, isSecret: editDraft.isSecret },
      },
      {
        onSuccess: () => {
          setEditDraft(null)
          onNotify('댓글을 수정했어요')
        },
        onError: (err) => {
          onNotify(getErrorMessage(err, '댓글을 수정하지 못했어요'))
        },
      },
    )
  }

  const handleConfirmDelete = () => {
    if (deletingCommentId === null || deleteMutation.isPending) return
    deleteMutation.mutate(deletingCommentId, {
      onSuccess: () => {
        setDeletingCommentId(null)
        onNotify('댓글을 삭제했어요')
      },
      onError: (err) => {
        setDeletingCommentId(null)
        onNotify(getErrorMessage(err, '댓글을 삭제하지 못했어요'))
      },
    })
  }

  const renderCommentCard = (item: CommentItem, isReply: boolean) => {
    if (item.isDeleted) {
      return (
        <div className="rounded-card border border-line bg-surface-sub px-5 py-4 text-body text-text-sub">
          삭제된 댓글입니다
        </div>
      )
    }

    const nickname = item.author?.nickname ?? '탈퇴한 사용자'
    const isMine =
      loginMember !== null && item.author !== null && loginMember.memberId === item.author.memberId
    const isEditing = editDraft?.commentId === item.commentId

    return (
      <div
        className={
          isReply
            ? 'min-w-0 rounded-card border border-line bg-bg p-4 sm:px-5 sm:py-4'
            : 'min-w-0 rounded-card border border-line bg-surface p-4 sm:px-5 sm:py-4'
        }
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex min-w-0 flex-wrap items-center gap-x-2.5 gap-y-1">
            {isReply && <CornerDownRightIcon className="h-4 w-4 shrink-0 text-text-disabled" />}

            <MemberActionPopover
              memberId={item.author?.memberId ?? null}
              nickname={nickname}
              redirectTo={redirectTo}
              reportTargetType="COMMENT"
              reportTargetId={item.commentId}
              onReportSuccess={onNotify}
            >
              <ProfilePhoto
                url={item.author?.profileImageUrl ?? null}
                name={nickname}
                size="sm"
                decorative
              />
              <span className="max-w-[160px] truncate text-body sm:max-w-[240px]">{nickname}</span>
            </MemberActionPopover>

            {item.isPostAuthor && <Badge variant="primary-soft">작성자</Badge>}

            {item.isSecret && (
              <span className="inline-flex items-center gap-1 rounded-full bg-surface-sub px-2.5 py-0.5 text-caption font-medium text-text-sub">
                <LockIcon className="h-3.5 w-3.5" />
                <span>비공개</span>
              </span>
            )}

            <time dateTime={item.createdAt} className="text-small text-text-sub">
              {formatCommentDateTime(item.createdAt)}
              {isEdited(item) ? ' (수정됨)' : ''}
            </time>
          </div>

          {!isEditing && (
            <div className="flex shrink-0 items-center gap-2 text-small sm:gap-3">
              {!isReply && (
                <button
                  type="button"
                  onClick={() => handleOpenReply(item)}
                  className="inline-flex cursor-pointer items-center px-1.5 font-medium text-text-sub transition-colors hover:text-primary max-sm:min-h-10 sm:px-0"
                >
                  {replyDraft?.parentId === item.commentId ? '답글 취소' : '답글'}
                </button>
              )}
              {isMine && (
                <>
                  <button
                    type="button"
                    onClick={() => handleStartEdit(item)}
                    className="inline-flex cursor-pointer items-center px-1.5 font-medium text-text-sub transition-colors hover:text-text max-sm:min-h-10 sm:px-0"
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    onClick={() => setDeletingCommentId(item.commentId)}
                    className="inline-flex cursor-pointer items-center px-1.5 font-medium text-danger transition-opacity hover:opacity-80 max-sm:min-h-10 sm:px-0"
                  >
                    삭제
                  </button>
                </>
              )}
            </div>
          )}
        </div>

        {isEditing && editDraft ? (
          <div className="mt-3 space-y-3">
            <textarea
              value={editDraft.content}
              onChange={(event) => setEditDraft({ ...editDraft, content: event.target.value })}
              rows={3}
              maxLength={MAX_COMMENT_LENGTH}
              aria-label="댓글 수정 내용"
              className="w-full resize-none rounded-input border border-line-input bg-surface px-4 py-3 text-body text-text placeholder:text-text-disabled focus:border-2 focus:border-primary focus:outline-none"
            />
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-4">
                <label className="inline-flex cursor-pointer items-center gap-2 text-small text-text-sub select-none max-sm:min-h-10">
                  <input
                    type="checkbox"
                    checked={editDraft.isSecret}
                    onChange={(event) =>
                      setEditDraft({ ...editDraft, isSecret: event.target.checked })
                    }
                    className="size-4 accent-primary"
                  />
                  <LockIcon className="h-4 w-4" />
                  <span>비공개 댓글</span>
                </label>
                <span className="text-small text-text-sub">
                  {editDraft.content.length} / {MAX_COMMENT_LENGTH}
                </span>
              </div>

              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  className="max-sm:min-h-10"
                  disabled={updateMutation.isPending}
                  onClick={() => setEditDraft(null)}
                >
                  취소
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  className="max-sm:min-h-10"
                  disabled={
                    editDraft.content.trim().length === 0 ||
                    editDraft.content.length > MAX_COMMENT_LENGTH ||
                    updateMutation.isPending
                  }
                  onClick={handleSubmitEdit}
                >
                  {updateMutation.isPending ? '저장 중…' : '저장'}
                </Button>
              </div>
            </div>
          </div>
        ) : item.isSecret && item.content === null ? (
          <p className="mt-3 flex items-center gap-1.5 text-body text-text-sub">
            <LockIcon className="h-4 w-4 shrink-0" />
            <span>비밀 댓글입니다</span>
          </p>
        ) : (
          <p className="mt-3 text-body [overflow-wrap:anywhere] whitespace-pre-wrap text-text">
            {item.content}
          </p>
        )}
      </div>
    )
  }

  return (
    <section aria-label="댓글 영역" className="mt-12 border-t border-divider pt-10">
      <div className="flex items-center gap-2">
        <h2 className="text-subheading font-bold text-text">댓글</h2>
        <span className="text-subheading font-bold text-primary">{totalCount}</span>
      </div>

      {/* 상단 원댓글 입력창 */}
      <div className="mt-4 rounded-card border border-line bg-surface p-4">
        {isLoggedIn ? (
          <>
            <textarea
              value={newContent}
              onChange={(event) => setNewContent(event.target.value)}
              rows={3}
              maxLength={MAX_COMMENT_LENGTH}
              placeholder="댓글을 입력해 주세요 (최대 500자)"
              aria-label="댓글 입력"
              className="w-full resize-none rounded-input border border-line-input bg-surface px-4 py-3 text-body text-text placeholder:text-text-disabled focus:border-2 focus:border-primary focus:outline-none"
            />
            <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-4">
                <label className="inline-flex cursor-pointer items-center gap-2 text-small text-text-sub select-none max-sm:min-h-10">
                  <input
                    type="checkbox"
                    checked={newSecret}
                    onChange={(event) => setNewSecret(event.target.checked)}
                    className="size-4 accent-primary"
                  />
                  <LockIcon className="h-4 w-4" />
                  <span>비공개 댓글</span>
                </label>
                <span className="text-small text-text-sub">
                  {newContent.length} / {MAX_COMMENT_LENGTH}
                </span>
              </div>

              <Button
                variant="primary"
                size="md"
                disabled={
                  newContent.trim().length === 0 ||
                  newContent.length > MAX_COMMENT_LENGTH ||
                  createMutation.isPending
                }
                onClick={handleSubmitRoot}
              >
                {createMutation.isPending && replyDraft === null ? '등록 중…' : '등록'}
              </Button>
            </div>
          </>
        ) : (
          <div className="flex flex-col items-start justify-between gap-3 sm:flex-row sm:items-center">
            <button
              type="button"
              onClick={() => setLoginModalOpen(true)}
              className="w-full cursor-pointer text-left text-body text-text-sub hover:text-text max-sm:min-h-10"
            >
              로그인 후 댓글과 답글을 남길 수 있어요
            </button>
            <Button
              variant="primary"
              size="sm"
              className="max-sm:min-h-10"
              onClick={() => setLoginModalOpen(true)}
            >
              로그인
            </Button>
          </div>
        )}
      </div>

      {/* 댓글·대댓글 목록 */}
      <div className="mt-6 space-y-4">
        {commentsQuery.isPending ? (
          <div className="space-y-3" aria-busy="true">
            <div className="h-24 animate-pulse rounded-card bg-surface-sub" />
            <div className="h-24 animate-pulse rounded-card bg-surface-sub" />
          </div>
        ) : commentsQuery.isError ? (
          <div className="flex flex-col items-center gap-3 rounded-card border border-line bg-surface py-10 text-center">
            <p className="text-body text-text-sub">댓글을 불러오지 못했어요</p>
            <Button
              variant="outline"
              size="sm"
              className="max-sm:min-h-10"
              onClick={() => {
                void commentsQuery.refetch()
              }}
            >
              다시 시도
            </Button>
          </div>
        ) : comments.length === 0 ? (
          <div className="flex flex-col items-center gap-3 rounded-card border border-line bg-surface px-4 py-12 text-center">
            <MessageSquareIcon className="h-10 w-10 text-text-disabled" />
            <p className="text-body text-text-sub">
              아직 작성된 댓글이 없어요. 첫 댓글을 남겨보세요.
            </p>
          </div>
        ) : (
          comments.map((root) => (
            <div key={root.commentId} className="min-w-0 space-y-3">
              {renderCommentCard(root, false)}

              {/* 대댓글(1-Depth) 목록 */}
              {root.replies.length > 0 && (
                <div className="space-y-3 pl-4 sm:pl-10">
                  {root.replies.map((reply) => (
                    <div key={reply.commentId}>{renderCommentCard(reply, true)}</div>
                  ))}
                </div>
              )}

              {/* 인라인 답글 입력창 */}
              {replyDraft?.parentId === root.commentId && (
                <div className="pl-4 sm:pl-10">
                  <div className="rounded-card border border-line bg-bg p-4">
                    <div className="mb-2 flex items-center gap-1.5 text-small font-medium text-text-sub">
                      <CornerDownRightIcon className="h-4 w-4 shrink-0 text-text-disabled" />
                      <span className="truncate">
                        {replyDraft.parentNickname}님에게 답글 남기기
                      </span>
                    </div>
                    <textarea
                      value={replyDraft.content}
                      onChange={(event) =>
                        setReplyDraft({ ...replyDraft, content: event.target.value })
                      }
                      rows={3}
                      maxLength={MAX_COMMENT_LENGTH}
                      placeholder="답글을 입력해 주세요 (최대 500자)"
                      aria-label="답글 입력"
                      className="w-full resize-none rounded-input border border-line-input bg-surface px-4 py-3 text-body text-text placeholder:text-text-disabled focus:border-2 focus:border-primary focus:outline-none"
                    />
                    <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                      <div className="flex flex-wrap items-center gap-4">
                        <label className="inline-flex cursor-pointer items-center gap-2 text-small text-text-sub select-none max-sm:min-h-10">
                          <input
                            type="checkbox"
                            checked={replyDraft.isSecret}
                            onChange={(event) =>
                              setReplyDraft({ ...replyDraft, isSecret: event.target.checked })
                            }
                            className="size-4 accent-primary"
                          />
                          <LockIcon className="h-4 w-4" />
                          <span>비공개 댓글</span>
                        </label>
                        <span className="text-small text-text-sub">
                          {replyDraft.content.length} / {MAX_COMMENT_LENGTH}
                        </span>
                      </div>

                      <div className="flex items-center gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          className="max-sm:min-h-10"
                          disabled={createMutation.isPending}
                          onClick={() => setReplyDraft(null)}
                        >
                          취소
                        </Button>
                        <Button
                          variant="primary"
                          size="sm"
                          className="max-sm:min-h-10"
                          disabled={
                            replyDraft.content.trim().length === 0 ||
                            replyDraft.content.length > MAX_COMMENT_LENGTH ||
                            createMutation.isPending
                          }
                          onClick={handleSubmitReply}
                        >
                          {createMutation.isPending ? '등록 중…' : '답글 등록'}
                        </Button>
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>

      {/* 댓글 삭제 확인 모달 */}
      <Modal
        open={deletingCommentId !== null}
        size="sm"
        title="댓글을 삭제할까요?"
        description="삭제하면 되돌릴 수 없어요."
        onClose={() => {
          if (!deleteMutation.isPending) setDeletingCommentId(null)
        }}
        footer={
          <>
            <Button
              variant="outline"
              size="md"
              disabled={deleteMutation.isPending}
              onClick={() => setDeletingCommentId(null)}
            >
              취소
            </Button>
            <Button
              variant="danger"
              size="md"
              disabled={deleteMutation.isPending}
              onClick={handleConfirmDelete}
            >
              {deleteMutation.isPending ? '삭제 중…' : '삭제'}
            </Button>
          </>
        }
      />

      <LoginRequiredModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        redirectTo={redirectTo}
      />
    </section>
  )
}
