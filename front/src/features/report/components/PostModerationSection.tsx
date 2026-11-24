import { useState } from 'react'
import { useLoginMember } from '../../auth/store'
import { Button } from '../../../components/ui/Button'
import { ReportModal } from './ReportModal'
import type { ReportTargetType } from '../types'

export function PostModerationSection({
  postId,
  authorId,
  nickname,
}: {
  postId: number
  authorId: number
  nickname: string
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
        {me && me.memberId !== authorId && (
          <Button
            size="sm"
            variant="text"
            onClick={() => setTarget({ type: 'POST', id: postId, memberId: authorId, nickname })}
          >
            모집글 신고
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
