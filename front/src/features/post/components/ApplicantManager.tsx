import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getErrorMessage } from '../../../api/errors'
import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import { useAcceptApplication, useApplicants, useRejectApplication } from '../hooks'
import type { Applicant, ApplicantStatus, PostDetail } from '../types'
import { MemberPhoto } from './MemberPhoto'

interface ApplicantManagerProps {
  post: PostDetail
  // 서버가 막은 이유를 페이지 토스트로 띄운다 (REC-004 ③, PST-006 ⑧)
  onError: (message: string) => void
}

// INT-003 ①: 새 지원 알림은 「상세 주소?applicants=open」으로 와서 지원자 관리 모달을 바로 연다 (RecruitNotifier)
const OPEN_PARAM = 'applicants'
const OPEN_VALUE = 'open'

const TABS: { status: ApplicantStatus; label: string }[] = [
  { status: 'PENDING', label: '대기' },
  { status: 'ACCEPTED', label: '수락' },
  { status: 'REJECTED', label: '거절' },
]

// 2026-11-02T13:40:00 → 11.02 13:40 (REC-003 ③)
function applyDateTime(dateTime: string) {
  return `${dateTime.slice(5, 7)}.${dateTime.slice(8, 10)} ${dateTime.slice(11, 16)}`
}

function displayName(applicant: Applicant) {
  return applicant.member.nickname ?? '탈퇴한 사용자'
}

// REC-003 지원자 관리 / Figma 03-6·04-1. 작성자 패널의 「지원자 관리 (대기 N)」 버튼과 모달.
export function ApplicantManager({ post, onError }: ApplicantManagerProps) {
  // 모달이 열려 있는지는 주소 표시로 정한다. 알림을 눌러 같은 상세로 다시 와도 열린다.
  const [searchParams, setSearchParams] = useSearchParams()
  const open = searchParams.get(OPEN_PARAM) === OPEN_VALUE
  const setOpen = (next: boolean) =>
    setSearchParams(
      (prev) => {
        const params = new URLSearchParams(prev)
        if (next) params.set(OPEN_PARAM, OPEN_VALUE)
        else params.delete(OPEN_PARAM)
        return params
      },
      // 열고 닫을 때마다 방문 기록을 쌓지 않는다
      { replace: true },
    )
  // 대기 인원은 버튼에도 보이므로 모달을 열기 전부터 불러온다 (PST-006 ⑥)
  const pending = useApplicants(post.postId, 'PENDING')
  const pendingCount = pending.data?.counts.pending

  return (
    <>
      <Button variant="primary" size="lg" className="w-full" onClick={() => setOpen(true)}>
        지원자 관리{pendingCount !== undefined && ` (대기 ${pendingCount})`}
      </Button>
      {open && <ApplicantModal post={post} onClose={() => setOpen(false)} onError={onError} />}
    </>
  )
}

type Confirm = { type: 'accept' | 'reject'; applicant: Applicant }

function ApplicantModal({
  post,
  onClose,
  onError,
}: ApplicantManagerProps & { onClose: () => void }) {
  // ② 처음엔 대기 탭
  const [tab, setTab] = useState<ApplicantStatus>('PENDING')
  const [confirm, setConfirm] = useState<Confirm | null>(null)
  const list = useApplicants(post.postId, tab)
  const accept = useAcceptApplication()
  const reject = useRejectApplication()
  // REC-004 ③ 정원이 차면 수락 버튼을 바로 막는다 (서버도 409 POST_FULL로 막는다)
  const full = post.currentCount >= post.targetCount
  const counts = list.data?.counts

  const run = () => {
    if (!confirm || accept.isPending || reject.isPending) return
    const mutation = confirm.type === 'accept' ? accept : reject
    mutation.mutate(confirm.applicant.applicationId, {
      onSuccess: () => setConfirm(null),
      onError: (error) => {
        setConfirm(null)
        onError(getErrorMessage(error))
      },
    })
  }

  return (
    <>
      {/* 확인 모달이 떠 있으면 Esc·바깥 클릭은 확인 모달만 닫는다 */}
      <Modal
        open
        size="lg"
        title="지원자 관리"
        description={`${post.title} · 모집 인원 ${post.currentCount} / ${post.targetCount}명`}
        onClose={() => {
          if (!confirm) onClose()
        }}
      >
        <div
          role="tablist"
          aria-label="지원 상태"
          className="flex gap-8 border-b border-line-strong"
        >
          {TABS.map(({ status, label }) => {
            const selected = tab === status
            const count = counts?.[status.toLowerCase() as keyof typeof counts]
            return (
              <button
                key={status}
                type="button"
                role="tab"
                id={`applicant-tab-${status}`}
                aria-selected={selected}
                aria-controls="applicant-tabpanel"
                onClick={() => setTab(status)}
                className={[
                  '-mb-px cursor-pointer border-b-2 pb-2 text-body-lg',
                  selected
                    ? 'border-text font-bold text-text'
                    : 'border-transparent text-text-sub hover:text-text',
                ].join(' ')}
              >
                {label}
                {count !== undefined && ` ${count}`}
              </button>
            )
          })}
        </div>

        <div
          id="applicant-tabpanel"
          role="tabpanel"
          aria-labelledby={`applicant-tab-${tab}`}
          className="h-[400px] overflow-y-auto"
        >
          {list.isPending ? (
            <ul aria-busy="true">
              {[0, 1, 2].map((i) => (
                <li key={i} className="h-[92px] border-b border-line py-4">
                  <div className="h-full animate-pulse rounded-lg bg-surface-sub" />
                </li>
              ))}
            </ul>
          ) : list.isError ? (
            <div className="flex h-full flex-col items-center justify-center gap-4">
              <p className="text-body text-text-sub">{getErrorMessage(list.error)}</p>
              <Button onClick={() => list.refetch()}>다시 시도</Button>
            </div>
          ) : list.data.items.length === 0 ? (
            // ⑥ 지원자가 없을 때 (Figma 04-6)
            <p className="flex h-full items-center justify-center text-body text-text-sub">
              {tab === 'PENDING'
                ? '아직 지원자가 없어요'
                : `${TABS.find((t) => t.status === tab)?.label}한 지원자가 없어요`}
            </p>
          ) : (
            <ul>
              {list.data.items.map((applicant) => (
                <ApplicantRow
                  key={applicant.applicationId}
                  applicant={applicant}
                  status={tab}
                  acceptDisabled={full}
                  onAccept={() => setConfirm({ type: 'accept', applicant })}
                  onReject={() => setConfirm({ type: 'reject', applicant })}
                />
              ))}
            </ul>
          )}
        </div>
      </Modal>

      {/* REC-004 ① 수락 확인 (Figma 04-2), REC-005 ① 거절 확인 (Figma 04-3) */}
      <Modal
        open={confirm !== null}
        size="sm"
        title={
          confirm?.type === 'accept'
            ? `‘${confirm ? displayName(confirm.applicant) : ''}’님을 팀원으로 받을까요?`
            : `‘${confirm ? displayName(confirm.applicant) : ''}’님의 지원을 거절할까요?`
        }
        description={
          confirm?.type === 'accept'
            ? `수락하면 모집 인원이 ${post.currentCount + 1} / ${post.targetCount}명이 돼요.`
            : '거절한 지원자는 이 글에 다시 지원할 수 없어요.'
        }
        onClose={() => setConfirm(null)}
        footer={
          <>
            <Button size="lg" onClick={() => setConfirm(null)}>
              취소
            </Button>
            <Button
              variant={confirm?.type === 'accept' ? 'primary' : 'danger'}
              size="lg"
              loading={accept.isPending || reject.isPending}
              onClick={run}
            >
              {confirm?.type === 'accept' ? '수락' : '거절'}
            </Button>
          </>
        }
      />
    </>
  )
}

// ③ 프로필 사진, 닉네임, 전공, 한 줄 소개, 지원 일시, 「프로필 보기」 「거절」 「수락」
function ApplicantRow({
  applicant,
  status,
  acceptDisabled,
  onAccept,
  onReject,
}: {
  applicant: Applicant
  status: ApplicantStatus
  acceptDisabled: boolean
  onAccept: () => void
  onReject: () => void
}) {
  const { member } = applicant
  const name = displayName(applicant)
  const intro = [member.major, member.bio].filter(Boolean).join(' · ')

  return (
    <li className="flex items-center gap-4 border-b border-line py-4">
      <MemberPhoto url={member.profileImageUrl} nickname={name} className="size-12 text-body" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-body font-bold text-text">{name}</p>
        {intro && <p className="truncate text-small text-text-sub">{intro}</p>}
        <p className="text-caption text-text-sub">
          지원 <time dateTime={applicant.createdAt}>{applyDateTime(applicant.createdAt)}</time>
        </p>
      </div>
      <div className="flex shrink-0 items-center gap-2">
        {/* ⑤ 공개 프로필(MY-05). 탈퇴 회원은 공개 프로필이 숨겨지므로 링크를 두지 않는다 */}
        {member.nickname !== null && (
          <Link
            to={`/members/${member.memberId}`}
            className="inline-flex h-9 items-center rounded-full border border-line-strong bg-surface px-4 text-small font-medium whitespace-nowrap text-text hover:bg-bg"
          >
            프로필 보기
          </Link>
        )}
        {status === 'PENDING' ? (
          <>
            <Button size="sm" onClick={onReject}>
              거절
            </Button>
            <Button variant="primary" size="sm" disabled={acceptDisabled} onClick={onAccept}>
              수락
            </Button>
          </>
        ) : (
          // Figma 04-5는 처리 시각도 보여 주지만 REC-003 응답에 없어 상태만 표시한다
          <span className="text-small text-text-sub">
            {status === 'ACCEPTED' ? '수락됨' : '거절됨'}
          </span>
        )}
      </div>
    </li>
  )
}
