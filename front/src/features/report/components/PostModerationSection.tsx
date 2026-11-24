import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'
import type { ApiResponse } from '../../../api/types'
import { useLoginMember } from '../../auth/store'
import { Button } from '../../../components/ui/Button'
import { ReportModal } from './ReportModal'
import type { ReportTargetType } from '../types'

interface CommentItem { id: number; authorId: number; content: string; deletedAt: string | null }
export function PostModerationSection({ postId, authorId, nickname }: { postId: number; authorId: number; nickname: string }) {
  const me = useLoginMember()
  const [target, setTarget] = useState<{ type: ReportTargetType; id: number; memberId: number; nickname: string } | null>(null)
  const [message, setMessage] = useState('')
  const comments = useQuery({ queryKey: ['post-comments', postId], queryFn: async () => (await apiClient.get<ApiResponse<CommentItem[]>>(`/api/posts/${postId}/comments`)).data.data, refetchOnWindowFocus: true })
  return <section className="mt-8 border-t border-line pt-6">
    <div className="flex justify-between gap-4"><h2 className="text-subheading font-bold">댓글</h2>{me && me.memberId !== authorId && <Button size="sm" variant="text" onClick={() => setTarget({ type: 'POST', id: postId, memberId: authorId, nickname })}>모집글 신고</Button>}</div>
    {message && <p role="status" className="my-3 text-primary">{message}</p>}
    {comments.isPending && <p className="mt-4 text-small">댓글을 불러오는 중입니다.</p>}
    {comments.isError && <Button className="mt-4" onClick={() => comments.refetch()}>댓글 다시 불러오기</Button>}
    {comments.data?.length === 0 && <p className="mt-4 text-small text-text-sub">등록된 댓글이 없습니다.</p>}
    {comments.data?.map(c => <div key={c.id} className="border-b border-divider py-4"><p className="whitespace-pre-wrap break-words text-body">{c.content}</p>{me && !c.deletedAt && me.memberId !== c.authorId && <Button size="sm" variant="text" onClick={() => setTarget({ type: 'COMMENT', id: c.id, memberId: c.authorId, nickname: '댓글 작성자' })}>댓글 신고</Button>}</div>)}
    {target && <ReportModal open targetType={target.type} targetId={target.id} reportedMemberId={target.memberId} targetNickname={target.nickname} onClose={() => setTarget(null)} onSubmitted={setMessage} />}
  </section>
}
