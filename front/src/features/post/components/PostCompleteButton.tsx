import { useState } from 'react'
import { getErrorMessage } from '../../../api/errors'
import { Button } from '../../../components/ui/Button'
import type { ButtonSize } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import { useCompletePost } from '../hooks'
import type { PostDetail } from '../types'

interface PostCompleteButtonProps {
  post: PostDetail
  // 서버가 막은 이유를 페이지 토스트로 띄운다 (PST-006 ⑧)
  onError: (message: string) => void
  // 휴대폰 아래 고정 줄에서는 md (STYLE_GUIDE 8-3). 기본은 패널 안의 lg
  size?: ButtonSize
}

// REC-006 ② 수동 모집 완료 / Figma 03-6·03-6b. 모집중인 글의 작성자 패널에만 보인다.
export function PostCompleteButton({ post, onError, size = 'lg' }: PostCompleteButtonProps) {
  const complete = useCompletePost()
  const [confirmOpen, setConfirmOpen] = useState(false)
  // 정원이 차면 서버가 자동으로 모집완료로 바꾸므로(①) 보통은 미충원 상태에서 누른다
  const notFull = post.currentCount < post.targetCount

  const run = () => {
    if (complete.isPending) return
    complete.mutate(post.postId, {
      onSuccess: () => setConfirmOpen(false),
      onError: (error) => {
        setConfirmOpen(false)
        onError(getErrorMessage(error))
      },
    })
  }

  return (
    <>
      <Button
        size={size}
        className="w-full"
        loading={!confirmOpen && complete.isPending}
        onClick={() => (notFull ? setConfirmOpen(true) : run())}
      >
        모집 완료
      </Button>
      {/* ② 정원이 안 찼으면 확인 모달. ④ 남은 대기 지원은 자동으로 거절된다 */}
      <Modal
        open={confirmOpen}
        size="sm"
        title="아직 목표 모집 인원이 채워지지 않았습니다."
        onClose={() => setConfirmOpen(false)}
        footer={
          <>
            <Button size="lg" onClick={() => setConfirmOpen(false)}>
              취소
            </Button>
            <Button variant="primary" size="lg" loading={complete.isPending} onClick={run}>
              모집 완료
            </Button>
          </>
        }
      >
        <p className="text-body text-text-sub">
          모집을 종료하시겠습니까? (현재 {post.currentCount} / {post.targetCount}명)
        </p>
        <p className="mt-1 text-body text-text-sub">종료하면 대기 중인 지원은 자동으로 거절돼요.</p>
      </Modal>
    </>
  )
}
