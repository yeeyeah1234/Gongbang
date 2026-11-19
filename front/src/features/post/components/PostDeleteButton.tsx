import { useState } from 'react'
import { useNavigate } from 'react-router'
import { getErrorMessage } from '../../../api/errors'
import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import { useDeletePost } from '../hooks'
import type { PostDetail } from '../types'

interface PostDeleteButtonProps {
  post: PostDetail
  // 서버가 막은 이유를 페이지 토스트로 띄운다 (PST-006 ⑧)
  onError: (message: string) => void
}

// PST-004 모집글 삭제 / Figma 03-6a. 작성자에게만 상세 제목 줄 [수정] 옆에 보인다 (①).
export function PostDeleteButton({ post, onError }: PostDeleteButtonProps) {
  const navigate = useNavigate()
  const deletePost = useDeletePost()
  const [open, setOpen] = useState(false)
  // ③ 모집 인원은 작성자를 포함하므로 작성자를 뺀 확정 팀원 수
  const teammateCount = post.currentCount - 1

  const confirm = () => {
    if (deletePost.isPending) return
    deletePost.mutate(post.postId, {
      // ④ 삭제하면 목록으로
      onSuccess: () => navigate('/posts', { replace: true }),
      onError: (error) => {
        setOpen(false)
        onError(getErrorMessage(error))
      },
    })
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex h-9 cursor-pointer items-center rounded-full border border-line-strong bg-surface px-4 text-small font-medium text-text hover:bg-bg"
      >
        삭제
      </button>
      {/* ②③ 확인 모달 */}
      <Modal
        open={open}
        title={`‘${post.title}’ 모집글을 삭제할까요?`}
        onClose={() => setOpen(false)}
        footer={
          <>
            <Button size="lg" onClick={() => setOpen(false)}>
              취소
            </Button>
            <Button variant="danger" size="lg" loading={deletePost.isPending} onClick={confirm}>
              삭제
            </Button>
          </>
        }
      >
        <p className="text-body text-text-sub">삭제하면 되돌릴 수 없어요</p>
        {teammateCount > 0 && (
          <p className="mt-1 text-body font-bold text-danger">
            확정된 팀원이 {teammateCount}명 있어요
          </p>
        )}
      </Modal>
    </>
  )
}
