import { useState } from 'react'
import { useLoginMember } from '../../auth/store'
import { Button } from '../../../components/ui/Button'
import { ReportModal } from './ReportModal'
import type { ReportTargetType } from '../types'

export function PostModerationSection({
  postId,
  authorId,
  nickname,
  targetType = 'POST',
  buttonLabel = '모집글 신고',
}: {
  postId: number
  authorId: number | null
  nickname: string
  targetType?: 'POST' | 'BOARD_POST'
  buttonLabel?: string
}) {
  const me = useLoginMember()
  const [target, setTarget] = useState<{
    type: ReportTargetType
    id: number
    memberId: number
    nickname: string
  } | null>(null)
  const [message, setMessage] = useState('')
  return (
    <section className="mt-8 border-t border-line pt-6">
      <div className="flex justify-end gap-4">
        {me && authorId !== null && me.memberId !== authorId && (
          <Button
            size="sm"
            variant="text"
            onClick={() =>
              setTarget({ type: targetType, id: postId, memberId: authorId, nickname })
            }
          >
            {buttonLabel}
          </Button>
        )}
      </div>
      {message && (
        <p role="status" className="my-3 text-primary">
          {message}
        </p>
      )}
      {target && (
        <ReportModal
          open
          targetType={target.type}
          targetId={target.id}
          reportedMemberId={target.memberId}
          targetNickname={target.nickname}
          onClose={() => setTarget(null)}
          onSubmitted={setMessage}
        />
      )}
    </section>
  )
}
