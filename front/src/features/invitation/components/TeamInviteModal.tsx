import { useState } from 'react'
import { Link } from 'react-router'
import { getErrorMessage } from '../../../api/errors'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import { useCreateInvitationMutation, useMyRecruitingPosts } from '../hooks'

interface TeamInviteModalProps {
  open: boolean
  inviteeId: number | null
  targetNickname: string
  onClose: () => void
  onInvited?: (message: string) => void
}

/** NET-02 ④ 닉네임 클릭 팝오버에서 내가 모집중인 글로 팀 초대를 보내는 모달. */
export function TeamInviteModal({
  open,
  inviteeId,
  targetNickname,
  onClose,
  onInvited,
}: TeamInviteModalProps) {
  const [selectedPostId, setSelectedPostId] = useState<number | null>(null)
  const [error, setError] = useState<string | undefined>()

  const postsQuery = useMyRecruitingPosts(open && inviteeId !== null)
  const inviteMutation = useCreateInvitationMutation()

  const posts = postsQuery.data ?? []

  const resetAndClose = () => {
    if (inviteMutation.isPending) return
    setSelectedPostId(null)
    setError(undefined)
    inviteMutation.reset()
    onClose()
  }

  const handleSubmit = () => {
    if (inviteeId === null || selectedPostId === null || inviteMutation.isPending) {
      setError('초대할 모집글을 선택해 주세요')
      return
    }
    setError(undefined)
    inviteMutation.mutate(
      { postId: selectedPostId, inviteeId },
      {
        onSuccess: () => {
          setSelectedPostId(null)
          setError(undefined)
          inviteMutation.reset()
          onClose()
          onInvited?.(`${targetNickname}님에게 팀 초대를 보냈어요`)
        },
        onError: (err) => {
          setError(getErrorMessage(err, '팀 초대를 보내지 못했어요. 잠시 후 다시 시도해 주세요'))
        },
      },
    )
  }

  return (
    <Modal
      open={open && inviteeId !== null}
      size="md"
      title={`${targetNickname}님 팀 초대하기`}
      description="초대할 내 모집글을 선택해 주세요."
      onClose={resetAndClose}
      footer={
        <>
          <Button
            variant="outline"
            size="md"
            disabled={inviteMutation.isPending}
            onClick={resetAndClose}
          >
            취소
          </Button>
          {posts.length > 0 && (
            <Button
              variant="primary"
              size="md"
              disabled={selectedPostId === null || inviteMutation.isPending}
              onClick={handleSubmit}
            >
              {inviteMutation.isPending ? '초대 중…' : '초대 보내기'}
            </Button>
          )}
        </>
      }
    >
      {postsQuery.isPending ? (
        <div className="space-y-2.5" aria-busy="true">
          <div className="h-14 animate-pulse rounded-input bg-surface-sub" />
          <div className="h-14 animate-pulse rounded-input bg-surface-sub" />
        </div>
      ) : postsQuery.isError ? (
        <div className="flex flex-col items-center gap-3 rounded-card border border-line bg-bg px-4 py-8 text-center">
          <p className="text-body text-text-sub">모집글 목록을 불러오지 못했어요</p>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              void postsQuery.refetch()
            }}
          >
            다시 시도
          </Button>
        </div>
      ) : posts.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-card border border-line bg-bg px-4 py-8 text-center">
          <p className="text-body text-text-sub">초대할 수 있는 모집중인 글이 없어요</p>
          <Link
            to="/posts/new"
            onClick={resetAndClose}
            className="inline-flex h-10 items-center rounded-full bg-primary px-4 text-small font-bold text-surface transition-colors hover:bg-primary-hover"
          >
            모집글 작성하기
          </Link>
        </div>
      ) : (
        <div role="radiogroup" aria-label="초대할 모집글 목록" className="space-y-2.5">
          {posts.map((post) => {
            const isFull = post.currentCount >= post.targetCount
            const isSelected = selectedPostId === post.postId
            return (
              <label
                key={post.postId}
                className={[
                  'flex items-center justify-between gap-3 rounded-input border px-4 py-3.5 transition-colors',
                  isFull
                    ? 'cursor-not-allowed border-line bg-surface-sub opacity-60'
                    : isSelected
                      ? 'cursor-pointer border-primary bg-primary-soft/40'
                      : 'cursor-pointer border-line bg-surface hover:bg-bg',
                ].join(' ')}
              >
                <div className="flex min-w-0 items-center gap-3">
                  <input
                    type="radio"
                    name="invite-post"
                    value={post.postId}
                    checked={isSelected}
                    disabled={isFull || inviteMutation.isPending}
                    onChange={() => {
                      setSelectedPostId(post.postId)
                      if (error) setError(undefined)
                    }}
                    className="size-4 shrink-0 accent-primary"
                  />
                  <span className="truncate text-body font-medium text-text">{post.title}</span>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {isFull && <Badge variant="surface-sub">정원 마감</Badge>}
                  <span className="text-small font-medium text-text-sub">
                    {post.currentCount}/{post.targetCount}명
                  </span>
                </div>
              </label>
            )
          })}
        </div>
      )}

      {error && (
        <p role="alert" className="mt-3 text-small text-danger">
          {error}
        </p>
      )}
    </Modal>
  )
}
