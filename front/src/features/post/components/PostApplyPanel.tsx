import { useState } from 'react'
import { useLocation } from 'react-router'
import { getErrorMessage } from '../../../api/errors'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import type { ButtonSize } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import { useIsLoggedIn } from '../../auth/store'
import { useApplyToPost, useCancelApplication } from '../hooks'
import { todayString } from '../postForm'
import type { PostDetail } from '../types'
import { useIsMdUp } from '../useIsMdUp'
import { ApplicantManager } from './ApplicantManager'
import { LoginRequiredModal } from './LoginRequiredModal'
import { PostCompleteButton } from './PostCompleteButton'

interface PostApplyPanelProps {
  post: PostDetail
  // 서버가 막은 이유를 페이지 토스트로 띄운다 (REC-001 ③, REC-002 ④⑤⑥)
  onError: (message: string) => void
}

// 2026-11-03 → 11.03 (올해가 아니면 2027.01.03처럼 연도를 붙인다)
function shortDate(date: string) {
  const monthDay = `${date.slice(5, 7)}.${date.slice(8, 10)}`
  return date.slice(0, 4) === todayString().slice(0, 4)
    ? monthDay
    : `${date.slice(0, 4)}.${monthDay}`
}

// PST-006 보는 사람별 버튼 / Figma 03-1~03-7. 오른쪽 고정 영역의 모집 인원 카드.
export function PostApplyPanel({ post, onError }: PostApplyPanelProps) {
  // STYLE_GUIDE 8-3: 768px 미만에서는 누를 버튼을 화면 아래 고정 줄로 옮긴다 (상태 문구만 있으면 그대로 둔다)
  const isMdUp = useIsMdUp()
  const inBar = !isMdUp && hasActionButtons(post)

  return (
    <section
      aria-labelledby="post-panel-count"
      className="rounded-card border border-line bg-surface p-6 shadow-pop"
    >
      <h2 className="text-small text-text-sub">모집 인원</h2>
      <p id="post-panel-count" className="mt-1 text-title font-bold text-text">
        <span className="sr-only">확정 팀원 </span>
        {post.currentCount} / {post.targetCount}명
      </p>
      {/* 숫자를 위에서 읽어 주므로 막대는 보조 표시 (인라인 style 대신 progress 요소) */}
      <progress
        aria-hidden="true"
        value={Math.min(post.currentCount, post.targetCount)}
        max={post.targetCount}
        className="mt-4 block h-2 w-full appearance-none overflow-hidden rounded-full bg-surface-sub [&::-moz-progress-bar]:bg-primary [&::-webkit-progress-bar]:bg-surface-sub [&::-webkit-progress-value]:rounded-full [&::-webkit-progress-value]:bg-primary"
      />
      <div className="mt-4 flex items-center gap-3">
        <StatusBadge status={post.status} recruitEndDate={post.recruitEndDate} />
        <span className="text-small text-text-sub">
          모집 기간 {shortDate(post.recruitStartDate)} ~ {shortDate(post.recruitEndDate)}
        </span>
      </div>
      {inBar ? (
        // 버튼이 여는 모달(z-50)이 이 줄 안에서 그려지므로, 상단바(z-30)보다 위에 오도록 z-40
        <div className="fixed inset-x-0 bottom-0 z-40 border-t border-divider bg-surface px-4 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))]">
          <PanelAction post={post} onError={onError} inBar />
        </div>
      ) : (
        <div className="mt-6">
          <PanelAction post={post} onError={onError} />
        </div>
      )}
    </section>
  )
}

// PST-001 ④ 모집중(파랑) / 모집마감(회색, 모집중인데 종료일이 지남) / 모집완료(회색). 저장된 상태값은 바꾸지 않는다
export function StatusBadge({
  status,
  recruitEndDate,
}: {
  status: PostDetail['status']
  recruitEndDate: string
}) {
  if (status !== 'RECRUITING') return <Badge variant="surface-sub">모집완료</Badge>
  if (todayString() > recruitEndDate) return <Badge variant="surface-sub">모집마감</Badge>
  return <Badge variant="primary-soft">모집중</Badge>
}

// 버튼 대신 상태 문구만 보여 줄 때
// compact: 휴대폰 아래 고정 줄에서 md 버튼(44px)과 높이를 맞춘다
function Notice({
  children,
  checked = false,
  compact = false,
}: {
  children: string
  checked?: boolean
  compact?: boolean
}) {
  return (
    <p
      className={[
        'flex items-center justify-center gap-2 rounded-full bg-surface-sub font-bold',
        compact ? 'h-11 text-body' : 'h-14 text-body-lg',
        checked ? 'text-primary' : 'text-text-sub',
      ].join(' ')}
    >
      {checked && <CheckMark />}
      {children}
    </p>
  )
}

function CheckMark() {
  return (
    <svg aria-hidden="true" viewBox="0 0 16 16" className="size-4" fill="none">
      <path d="M3 8.5 6.5 12 13 4.5" stroke="currentColor" strokeWidth="2" />
    </svg>
  )
}

// ⑩ 모집중인데 종료일이 지났으면 새 지원은 막는다 (종료일 당일까지 지원 가능, REC-001)
function isPeriodEnded(post: PostDetail) {
  return post.status === 'RECRUITING' && todayString() > post.recruitEndDate
}

// 상태 문구가 아니라 누를 버튼이 있는지: 작성자, 지원 대기, 지원할 수 있는 회원·비회원
function hasActionButtons(post: PostDetail) {
  if (post.myStatus === 'AUTHOR' || post.myStatus === 'PENDING') return true
  return post.myStatus === 'NONE' && post.status !== 'COMPLETED' && !isPeriodEnded(post)
}

// inBar: 휴대폰 아래 고정 줄에서는 버튼을 한 줄에 나란히 둔다
function PanelAction({ post, onError, inBar = false }: PostApplyPanelProps & { inBar?: boolean }) {
  const periodEnded = isPeriodEnded(post)
  // 고정 줄에서는 두 버튼이 375px 한 줄에 들어가도록 md(44px)
  const buttonSize: ButtonSize = inBar ? 'md' : 'lg'

  switch (post.myStatus) {
    case 'AUTHOR':
      // ⑥ 작성자: 「지원자 관리 (대기 N)」, 「모집 완료」.
      // ⑦⑩ 모집완료·기간 종료 뒤에도 지원자 관리는 그대로 쓰고, 모집 완료는 모집중일 때만 (REC-006 ③)
      return (
        <div className={inBar ? 'flex gap-2 *:flex-1' : 'flex flex-col gap-3'}>
          <ApplicantManager post={post} onError={onError} size={buttonSize} />
          {post.status === 'RECRUITING' && (
            <PostCompleteButton post={post} onError={onError} size={buttonSize} />
          )}
        </div>
      )
    case 'LEFT':
      return <Notice>팀에서 나간 모집글입니다</Notice> // ⑨
    case 'ACCEPTED':
      return <Notice checked>팀 참여 완료</Notice> // ⑤
    case 'REJECTED':
      return <Notice>지원이 거절되었어요</Notice> // ④
    case 'PENDING':
      return <PendingActions post={post} onError={onError} inBar={inBar} /> // ③
    case 'NONE':
      if (post.status === 'COMPLETED') return <Notice>모집이 완료된 게시글입니다</Notice> // ⑦
      if (periodEnded) return <Notice>모집 기간이 종료된 게시글입니다</Notice> // ⑩
      return <ApplyButton post={post} onError={onError} size={buttonSize} /> // ①②
  }
}

// ①② 지원하기: 비회원은 로그인 모달, 회원은 확인 모달 (REC-001 ①)
function ApplyButton({ post, onError, size }: PostApplyPanelProps & { size: ButtonSize }) {
  const location = useLocation()
  const isLoggedIn = useIsLoggedIn()
  const apply = useApplyToPost()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [loginOpen, setLoginOpen] = useState(false)

  const confirm = () => {
    // REC-001 ④ 요청 중에는 다시 보내지 않는다
    if (apply.isPending) return
    apply.mutate(post.postId, {
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
        variant="primary"
        size={size}
        className="w-full"
        onClick={() => (isLoggedIn ? setConfirmOpen(true) : setLoginOpen(true))}
      >
        지원하기
      </Button>
      <Modal
        open={confirmOpen}
        title={`‘${post.title}’ 모집에 지원할까요?`}
        onClose={() => setConfirmOpen(false)}
        footer={
          <>
            <Button size="lg" onClick={() => setConfirmOpen(false)}>
              취소
            </Button>
            <Button variant="primary" size="lg" loading={apply.isPending} onClick={confirm}>
              지원하기
            </Button>
          </>
        }
      />
      <LoginRequiredModal
        open={loginOpen}
        onClose={() => setLoginOpen(false)}
        redirectTo={location.pathname}
      />
    </>
  )
}

// ③ 지원 대기: 「지원 완료」 표시와 「지원 취소」 (REC-002)
function PendingActions({
  post,
  onError,
  inBar = false,
}: PostApplyPanelProps & { inBar?: boolean }) {
  const cancel = useCancelApplication(post.postId)
  const [confirmOpen, setConfirmOpen] = useState(false)

  const confirm = () => {
    if (cancel.isPending || post.myApplicationId === null) return
    cancel.mutate(post.myApplicationId, {
      onSuccess: () => setConfirmOpen(false),
      // REC-002 ④⑥ 이미 처리됐거나 없는 지원이면 안내 후 새로 불러온다 (훅이 다시 불러온다)
      onError: (error) => {
        setConfirmOpen(false)
        onError(getErrorMessage(error))
      },
    })
  }

  return (
    <div className={inBar ? 'flex gap-2 *:flex-1' : 'flex flex-col gap-3'}>
      <Notice checked compact={inBar}>
        지원 완료
      </Notice>
      <Button size={inBar ? 'md' : 'lg'} className="w-full" onClick={() => setConfirmOpen(true)}>
        지원 취소
      </Button>
      <Modal
        open={confirmOpen}
        title="지원을 취소할까요?"
        onClose={() => setConfirmOpen(false)}
        footer={
          <>
            <Button size="lg" onClick={() => setConfirmOpen(false)}>
              닫기
            </Button>
            <Button variant="primary" size="lg" loading={cancel.isPending} onClick={confirm}>
              지원 취소
            </Button>
          </>
        }
      />
    </div>
  )
}
