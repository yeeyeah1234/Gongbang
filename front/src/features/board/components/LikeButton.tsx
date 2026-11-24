import { useState } from 'react'
import { useIsLoggedIn } from '../../auth/store'
import { LoginRequiredModal } from '../../post/components/LoginRequiredModal'
import { useToggleBoardLike } from '../hooks'
import type { BoardDetail } from '../types'

const FAILED_MESSAGE = '좋아요를 반영하지 못했어요. 잠시 후 다시 시도해 주세요'

// BRD-007 좋아요. 비회원은 로그인 모달(①), 내 글은 비활성(③), 누르면 바로 바꾸고 실패하면 되돌린다(④)
export function LikeButton({
  post,
  onError,
}: {
  post: BoardDetail
  onError: (message: string) => void
}) {
  const isLoggedIn = useIsLoggedIn()
  const toggle = useToggleBoardLike(post.boardPostId)
  const [loginOpen, setLoginOpen] = useState(false)

  const click = () => {
    if (!isLoggedIn) {
      setLoginOpen(true)
      return
    }
    // 앞 요청이 끝나기 전에는 다시 보내지 않는다 (서버 값과 어긋나지 않게)
    if (toggle.isPending) return
    toggle.mutate(!post.isLiked, { onError: () => onError(FAILED_MESSAGE) })
  }

  return (
    <>
      <button
        type="button"
        aria-pressed={post.isLiked}
        disabled={post.isAuthor}
        title={post.isAuthor ? '내 글에는 좋아요를 누를 수 없어요' : undefined}
        onClick={click}
        className={[
          'inline-flex h-12 cursor-pointer items-center gap-2 rounded-full border px-5 text-body-lg font-bold transition-colors',
          'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
          'disabled:cursor-not-allowed disabled:border-line disabled:bg-surface-sub disabled:text-text-disabled',
          post.isLiked
            ? 'border-primary bg-primary-soft text-primary'
            : 'border-line-strong bg-surface text-text hover:bg-bg',
        ].join(' ')}
      >
        <svg aria-hidden="true" viewBox="0 0 20 20" className="size-5">
          <path
            d="M10 17s-6.5-3.9-6.5-8.6A3.6 3.6 0 0 1 10 6a3.6 3.6 0 0 1 6.5 2.4C16.5 13.1 10 17 10 17Z"
            fill={post.isLiked ? 'currentColor' : 'none'}
            stroke="currentColor"
            strokeWidth="1.6"
          />
        </svg>
        좋아요
        <span aria-live="polite">{post.likeCount.toLocaleString()}</span>
      </button>
      <LoginRequiredModal
        open={loginOpen}
        onClose={() => setLoginOpen(false)}
        redirectTo={`/board/${post.boardPostId}`}
      />
    </>
  )
}
