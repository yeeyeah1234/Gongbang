import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { getErrorMessage } from '../../api/errors'
import { useIsWide } from '../../hooks/useIsWide'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { useLoginMember } from '../../features/auth/store'
import { ProofReview } from '../../features/admin/ProofReview'
import {
  fetchVerifications,
  reviewVerification,
  type ReviewStatus,
  type Verification,
} from '../../features/admin/api'

const labels: Record<ReviewStatus, string> = {
  PENDING: '심사 대기',
  VERIFIED: '승인 완료',
  REJECTED: '반려',
}
const date = (value: string | null) => (value ? value.replace('T', ' ').slice(0, 16) : '—')

export default function AdminVerificationPage() {
  const member = useLoginMember()
  useEffect(() => {
    document.title = '인증 심사 | 공방'
  }, [])
  if (member?.role !== 'ADMIN')
    return (
      <main className="mx-auto max-w-3xl p-8">
        <h1 className="text-heading font-bold">관리자만 접근할 수 있어요</h1>
        <Link to="/" className="mt-4 inline-block text-primary">
          홈으로 돌아가기
        </Link>
      </main>
    )
  return <VerificationWorkspace />
}

export function VerificationWorkspace() {
  const isWide = useIsWide()
  const [detailOpen, setDetailOpen] = useState(false)
  const [status, setStatus] = useState<ReviewStatus>('PENDING')
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [message, setMessage] = useState('')
  const queryClient = useQueryClient()
  const history = useQuery({
    queryKey: ['admin-verifications', 'history'],
    queryFn: async () => {
      const results = await Promise.all([
        fetchVerifications('VERIFIED', 0),
        fetchVerifications('REJECTED', 0),
      ])
      return results
        .flatMap((r) => r.content)
        .sort((a, b) => (b.reviewedAt ?? '').localeCompare(a.reviewedAt ?? ''))
        .slice(0, 5)
    },
  })
  const query = useQuery({
    queryKey: ['admin-verifications', status, page],
    queryFn: () => fetchVerifications(status, page),
    staleTime: 0,
    gcTime: 0,
    retry: false,
  })
  const selected =
    query.data?.content.find((item) => item.id === selectedId) ??
    (isWide ? query.data?.content[0] : undefined)
  const mutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      reviewVerification(id, 'REJECTED', reason),
    onSuccess: async () => {
      setRejecting(false)
      setReason('')
      setMessage('반려 처리했습니다. 회원에게 결과 알림을 전달합니다.')
      await queryClient.invalidateQueries({ queryKey: ['admin-verifications'] })
      await queryClient.invalidateQueries({ queryKey: ['members'] })
    },
  })
  const select = (item: Verification) => {
    setDetailOpen(true)
    setSelectedId(item.id)
    setReason('')
    mutation.reset()
  }
  return (
    <main className="text-text">
      <div>
        <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="mb-2 text-small font-medium text-primary">
              관리자 페이지 / 자격증·수상 인증 심사
            </p>
            <h1 className="text-title font-bold">자격증·수상 인증 심사</h1>
            <p className="mt-2 text-small text-text-sub">
              자격증과 수상 경력의 인증 요청을 확인하고 처리합니다.
            </p>
          </div>
          <Button onClick={() => void query.refetch()} loading={query.isFetching}>
            새로고침
          </Button>
        </div>
        <div className="mb-4 flex flex-wrap justify-between gap-2 border-b-4 border-text bg-surface-sub px-4 py-3 text-small">
          <p className="font-bold">
            {labels[status]} ·{' '}
            {query.isPending
              ? '조회 중'
              : query.isError
                ? '조회 실패'
                : `${query.data.totalElements}건`}
          </p>
          <p className="text-caption text-text-sub">
            승인·반려 시 결과 알림 발송 · 심사용 실명 파기
          </p>
        </div>
        {message && (
          <p
            role="status"
            className="mb-4 rounded-card border border-line bg-surface p-4 text-success"
          >
            {message}
          </p>
        )}
        {query.isPending ? (
          <div role="status" className="rounded-card border border-line bg-surface p-8">
            인증 요청을 불러오는 중입니다…
          </div>
        ) : query.isError ? (
          <div role="alert" className="rounded-card border border-danger-line bg-surface p-8">
            <p className="mb-4 text-danger">
              {getErrorMessage(query.error, '인증 요청을 불러오지 못했어요.')}
            </p>
            <Button onClick={() => void query.refetch()}>다시 시도</Button>
          </div>
        ) : (
          <>
            <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
              {(isWide || !detailOpen) && (
                <section
                  aria-label="인증 요청 목록"
                  className="overflow-hidden border border-line-strong bg-surface"
                >
                  <div className="flex items-center justify-between border-b border-divider p-4">
                    <h2 className="text-subheading font-bold">{labels[status]} 요청</h2>
                    <span className="text-small text-text-sub">
                      총 {query.data.totalElements}건
                    </span>
                  </div>
                  <div
                    className="flex flex-wrap gap-2 border-b border-divider p-4"
                    aria-label="심사 상태 필터"
                  >
                    {(Object.keys(labels) as ReviewStatus[]).map((value) => (
                      <Button
                        size="sm"
                        key={value}
                        variant={status === value ? 'primary' : 'outline'}
                        aria-pressed={status === value}
                        onClick={() => {
                          setStatus(value)
                          setPage(0)
                          setSelectedId(null)
                          setMessage('')
                        }}
                      >
                        {labels[value]}
                      </Button>
                    ))}
                  </div>
                  {query.data.content.length === 0 ? (
                    <div className="px-6 py-16 text-center">
                      <h3 className="text-body font-bold">{labels[status]} 요청이 없습니다</h3>
                      <p className="mt-2 text-small text-text-sub">
                        다른 상태를 선택하거나 새로고침해 주세요.
                      </p>
                    </div>
                  ) : (
                    <ul className="divide-y divide-divider">
                      {query.data.content.map((item) => (
                        <li key={item.id}>
                          <button
                            onClick={() => select(item)}
                            aria-pressed={selected?.id === item.id}
                            className={`w-full cursor-pointer border-l-2 p-4 text-left transition-colors hover:bg-bg focus-visible:outline-2 focus-visible:outline-primary ${selected?.id === item.id ? 'border-text bg-surface-sub' : 'border-transparent'}`}
                          >
                            <div className="mb-2 flex items-center justify-between gap-4">
                              <span className="text-small font-medium text-primary">
                                {item.targetType === 'CERTIFICATE' ? '자격증' : '수상 경력'}
                              </span>
                              <span className="text-small text-text-sub">#{item.id}</span>
                            </div>
                            <h3 className="text-body font-bold break-words">{item.title}</h3>
                            <p className="mt-2 text-small text-text-sub">
                              {item.nickname} · {date(item.requestedAt)}
                            </p>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                  <div className="flex items-center justify-between border-t border-divider p-4">
                    <Button
                      size="sm"
                      disabled={page === 0}
                      onClick={() => {
                        setPage(page - 1)
                        setSelectedId(null)
                      }}
                    >
                      이전
                    </Button>
                    <span className="text-small text-text-sub">
                      {page + 1} / {Math.max(1, query.data.totalPages)}
                    </span>
                    <Button
                      size="sm"
                      disabled={page + 1 >= query.data.totalPages}
                      onClick={() => {
                        setPage(page + 1)
                        setSelectedId(null)
                      }}
                    >
                      다음
                    </Button>
                  </div>
                  <div className="border-t border-divider p-4">
                    <h2 className="mb-4 text-subheading font-bold">최근 심사 처리 이력</h2>
                    {history.isPending && <p className="text-small">불러오는 중…</p>}
                    {history.isError && (
                      <p className="text-small text-danger">처리 이력을 불러오지 못했습니다.</p>
                    )}
                    {history.data?.map((item) => (
                      <button
                        key={item.id}
                        className="block w-full border-b border-divider py-3 text-left text-small hover:text-primary"
                        onClick={() => {
                          setStatus(item.status)
                          setDetailOpen(true)
                          setSelectedId(item.id)
                          setPage(0)
                        }}
                      >
                        {item.title} · {labels[item.status]}
                        <span className="mt-1 block text-caption text-text-sub">
                          {date(item.reviewedAt)} · 심사자 #{item.reviewerId}
                        </span>
                      </button>
                    ))}
                  </div>
                </section>
              )}
              {(isWide || detailOpen) && (
                <section
                  aria-label="인증 요청 상세"
                  className="self-start border border-line-strong bg-surface p-4"
                >
                  {!isWide && (
                    <Button className="mb-4" onClick={() => setDetailOpen(false)}>
                      ← 목록으로
                    </Button>
                  )}
                  {selected ? (
                    <>
                      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
                        <h2 className="text-subheading font-bold">
                          심사 상세 정보 · 증빙 파일 확인
                        </h2>
                        <span className="rounded-full bg-surface-sub px-3 py-1 text-small">
                          {labels[selected.status]}
                        </span>
                      </div>
                      <h3 className="text-subheading font-bold break-words">{selected.title}</h3>
                      <p className="mt-2 text-body break-words text-text-sub">
                        {selected.description}
                      </p>
                      <dl className="my-6 grid grid-cols-2 gap-4 border-y border-divider py-6 text-small">
                        <dt className="text-text-sub">요청 회원</dt>
                        <dd>
                          {selected.nickname} (#{selected.memberId})
                        </dd>
                        <dt className="text-text-sub">접수 시각</dt>
                        <dd>{date(selected.requestedAt)}</dd>
                        <dt className="text-text-sub">증빙 파일</dt>
                        <dd>파일 #{selected.proofFileId}</dd>
                        {selected.reviewedAt && (
                          <>
                            <dt className="text-text-sub">처리 시각</dt>
                            <dd>{date(selected.reviewedAt)}</dd>
                            <dt className="text-text-sub">심사자</dt>
                            <dd>회원 #{selected.reviewerId}</dd>
                          </>
                        )}
                      </dl>
                      {selected.status === 'PENDING' ? (
                        <>
                          <ProofReview key={selected.id} item={selected} />
                          <div className="mt-8 flex justify-end gap-3">
                            <Button
                              variant="danger"
                              onClick={() => {
                                mutation.reset()
                                setReason('')
                                setRejecting(true)
                              }}
                            >
                              반려
                            </Button>
                          </div>
                        </>
                      ) : selected.rejectReason ? (
                        <div className="rounded-input bg-bg p-4">
                          <h3 className="text-body font-bold">반려 사유</h3>
                          <p className="mt-2 text-body break-words whitespace-pre-wrap">
                            {selected.rejectReason}
                          </p>
                        </div>
                      ) : (
                        <p className="text-body text-success">승인된 인증 요청입니다.</p>
                      )}
                    </>
                  ) : (
                    <p className="py-16 text-center text-body text-text-sub">
                      목록에서 인증 요청을 선택해 주세요.
                    </p>
                  )}
                </section>
              )}
            </div>
          </>
        )}
        <Modal
          open={rejecting && !!selected}
          title="인증 요청을 반려할까요?"
          description="회원이 보완할 수 있도록 구체적인 사유를 알려 주세요."
          onClose={() => {
            if (!mutation.isPending) setRejecting(false)
          }}
          footer={
            <>
              <Button disabled={mutation.isPending} onClick={() => setRejecting(false)}>
                취소
              </Button>
              <Button
                variant="danger"
                loading={mutation.isPending}
                disabled={!reason.trim()}
                onClick={() => {
                  if (selected) mutation.mutate({ id: selected.id, reason: reason.trim() })
                }}
              >
                반려 처리
              </Button>
            </>
          }
        >
          <label htmlFor="reject-reason" className="mb-2 block text-body font-bold">
            반려 사유 (필수)
          </label>
          <textarea
            id="reject-reason"
            autoFocus
            maxLength={200}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={mutation.isPending}
            className="h-40 w-full resize-none rounded-input border border-line-input p-4 text-body focus:outline-primary"
          />
          <p className="mt-2 text-right text-small text-text-sub">{reason.length} / 200</p>
          {mutation.isError && (
            <p role="alert" className="mt-3 text-small text-danger">
              {getErrorMessage(mutation.error)}
            </p>
          )}
        </Modal>
      </div>
    </main>
  )
}
