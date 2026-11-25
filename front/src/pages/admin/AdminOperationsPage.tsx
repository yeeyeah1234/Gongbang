import { MobileActionBar } from '../../components/layout/MobileActionBar'
import { useEffect, useState, type ReactNode, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation } from 'react-router'
import { isAxiosError } from 'axios'
import { useIsWide } from '../../hooks/useIsWide'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import {
  readAdmin,
  commandAdmin,
  endpoints,
  formatReportReason,
  type AdminItem,
  type AdminPage,
  type Kind,
  type Command,
  type Category,
} from '../../features/admin/operations'

const titles: Record<Kind, string> = {
  notices: '운영 공지 관리',
  members: '회원 관리',
  reports: '신고 처리',
  posts: '모집글·댓글 관리',
  contests: '공모전 관리',
}
const labels: Record<string, string> = {
  RECEIVED: '접수',
  RESOLVED: '처리 완료',
  DISMISSED: '기각',
  ACTIVE: '활성',
  WITHDRAWN: '탈퇴',
  DELETED: '삭제',
  RECRUITING: '모집 중',
  COMPLETED: '모집 완료',
  CLOSED: '모집 종료',
  ADMIN: '관리자',
  USER: '일반 회원',
  PENDING: '대기',
  RUNNING: '실행 중',
  SUCCEEDED: '완료',
  FAILED: '실패',
  COMMENT: '댓글',
  MEMBER: '회원',
  MESSAGE: '메시지',
  POST: '모집글',
  BOARD_POST: '커뮤니티 게시글',
}
const field =
  'min-w-0 w-full rounded-input border border-line-input bg-surface px-4 py-3 text-body focus:border-primary focus:outline-none'
const panel = 'border border-line-strong bg-surface p-4'
const descriptions: Record<Kind, string> = {
  notices: '서비스 전체 공지를 작성·수정하고 회원에게 소식을 전달합니다.',
  members: '회원 상태와 로그인 잠금을 확인하고 필요한 운영 조치를 적용합니다.',
  reports: '접수된 신고와 대상 내용을 확인한 뒤 처리 결과와 사유를 기록합니다.',
  posts: '신고된 모집글과 댓글을 확인하고 공개 여부를 관리합니다.',
  contests: '공모전 정보와 카테고리를 관리하고, 마감일 변경의 영향을 확인합니다.',
}
const listTitles: Record<Kind, string> = {
  notices: '운영 공지 목록',
  members: '회원 검색 · 목록',
  reports: '신고 접수 목록',
  posts: '모집글 · 댓글 목록',
  contests: '공모전 목록',
}
function errorText(error: unknown) {
  return isAxiosError(error)
    ? (error.response?.data?.error?.message ?? '처리하지 못했습니다. 다시 시도해 주세요.')
    : '처리하지 못했습니다. 다시 시도해 주세요.'
}
function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-small text-text-sub">{label}</dt>
      <dd className="mt-1 bg-surface-sub px-3 py-2 text-small break-words">{children ?? '—'}</dd>
    </div>
  )
}
function Stamp({ value }: { value?: string | null }) {
  return <>{value ? value.replace('T', ' ').slice(0, 16) : '—'}</>
}
function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block space-y-2">
      <span className="text-body font-bold">{label}</span>
      {children}
    </label>
  )
}
type Act = (command: Command) => void

export default function AdminOperationsPage({ kind }: { kind: Kind }) {
  const location = useLocation()
  return <Operations key={kind} kind={kind} preview={location.pathname.startsWith('/dev/')} />
}
function Operations({ kind, preview }: { kind: Kind; preview: boolean }) {
  const isWide = useIsWide()
  const [detailOpen, setDetailOpen] = useState(false)
  const [contestView, setContestView] = useState<'list' | 'categories' | 'edit'>('list')
  const client = useQueryClient()
  const [page, setPage] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [type, setType] = useState('')
  const [reportedFirst, setReportedFirst] = useState(true)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [confirmation, setConfirmation] = useState<Command | null>(null)
  const [feedback, setFeedback] = useState('')
  useEffect(() => {
    document.title = `${titles[kind]} | 공방`
  }, [kind])
  const query = useQuery({
    queryKey: ['admin', kind, page, search, status, type, reportedFirst],
    queryFn: () =>
      readAdmin<AdminPage>(endpoints[kind], { page, keyword: search, status, type, reportedFirst }),
    enabled: kind !== 'contests' || contestView !== 'categories',
  })
  const mutation = useMutation({
    mutationFn: commandAdmin,
    onSuccess: async () => {
      setConfirmation(null)
      setCreating(false)
      setFeedback('처리 결과가 저장되었습니다.')
      await client.invalidateQueries({ queryKey: ['admin'] })
    },
  })
  const selected =
    query.data?.content.find((item) => item.id === selectedId) ??
    (isWide && kind !== 'contests' ? query.data?.content[0] : undefined)
  function act(command: Command) {
    mutation.reset()
    setFeedback('')
    setConfirmation({
      ...command,
      targetLabel:
        command.targetLabel ??
        (creating
          ? '새 운영 공지'
          : (selected?.title ?? selected?.email ?? (selected ? `신고 #${selected.id}` : ''))),
    })
  }
  const filters =
    kind === 'reports'
      ? ['RECEIVED', 'RESOLVED', 'DISMISSED']
      : kind === 'members'
        ? ['ACTIVE', 'WITHDRAWN', 'DELETED']
        : []
  return (
    <main className="text-text">
      <header className="mb-4 flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-small text-primary">관리자 페이지 / {titles[kind]}</p>
          <h1 className="mt-2 text-title font-bold">{titles[kind]}</h1>
          <p className="mt-2 text-small text-text-sub">{descriptions[kind]}</p>
          {preview && <p className="mt-2 text-caption text-warning">미리보기 · 샘플 데이터</p>}
        </div>
        <div className="flex gap-3">
          <Button onClick={() => client.invalidateQueries({ queryKey: ['admin'] })}>
            새로고침
          </Button>
          {kind === 'notices' && (
            <Button
              onClick={() => {
                setDetailOpen(true)
                setCreating(true)
                setSelectedId(null)
              }}
            >
              새 공지 작성
            </Button>
          )}
        </div>
      </header>
      {kind === 'contests' && (
        <nav aria-label="공모전 관리 화면" className="mb-4 grid grid-cols-1 gap-2 md:grid-cols-3">
          {(
            [
              ['list', '공모전 목록'],
              ['categories', '카테고리 관리'],
              ['edit', '공모전 수정 · 영향 팀 확인'],
            ] as const
          ).map(([view, label]) => (
            <Button
              key={view}
              variant={contestView === view ? 'secondary' : 'outline'}
              aria-pressed={contestView === view}
              onClick={() => {
                setContestView(view)
                setFeedback('')
              }}
            >
              {label}
            </Button>
          ))}
        </nav>
      )}
      <div
        className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b-4 border-text bg-surface-sub px-4 py-3 text-small"
        aria-live="polite"
      >
        <p className="font-bold">
          {kind === 'contests' && contestView !== 'list' ? (
            contestView === 'categories' ? (
              '카테고리 관리'
            ) : (
              '공모전 수정 · 영향 팀 확인'
            )
          ) : (
            <>
              {listTitles[kind]} ·{' '}
              {query.isPending
                ? '조회 중'
                : query.isError
                  ? '조회 실패'
                  : `${query.data?.totalElements ?? 0}건`}
            </>
          )}
          {status ? ` · ${labels[status]}` : ''}
        </p>
        <p className="text-caption text-text-sub">
          {kind === 'reports'
            ? '최신 접수순 · 처리 결과와 사유 기록'
            : kind === 'posts'
              ? '강제 숨김 시 모집 종료 · 지원자 알림'
              : kind === 'notices'
                ? '공지 등록 시 전체 회원 사이트 알림'
                : kind === 'members'
                  ? '로그인 잠금과 계정 제재는 별도 관리'
                  : '마감일 변경 시 연결 팀 일정 재계산'}
        </p>
      </div>
      {feedback && (
        <p role="status" className="mb-4 rounded-input bg-primary-soft p-4 text-primary">
          {feedback}
        </p>
      )}
      <div
        className={`grid items-start gap-4 ${kind === 'contests' ? '' : 'xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]'}`}
      >
        {(kind === 'contests' ? contestView === 'list' : isWide || !detailOpen) && (
          <div className="min-w-0 space-y-4">
            <section className={panel} aria-label="목록">
              <h2 className="mb-4 text-body font-bold">{listTitles[kind]}</h2>
              {kind !== 'notices' && kind !== 'reports' && (
                <form
                  className="mb-4 flex gap-2"
                  onSubmit={(e) => {
                    e.preventDefault()
                    setSearch(keyword)
                    setPage(0)
                    setSelectedId(null)
                  }}
                >
                  <input
                    aria-label="검색어"
                    className={field}
                    maxLength={100}
                    value={keyword}
                    onChange={(e) => setKeyword(e.target.value)}
                    placeholder={kind === 'members' ? '이메일 또는 닉네임' : '제목 검색'}
                  />
                  <Button type="submit">검색</Button>
                </form>
              )}
              {filters.length > 0 && (
                <div className="mb-4 flex flex-wrap gap-2">
                  {['', ...filters].map((value) => (
                    <Button
                      key={value}
                      size="sm"
                      variant={status === value ? 'secondary' : 'outline'}
                      aria-pressed={status === value}
                      onClick={() => {
                        setStatus(value)
                        setPage(0)
                        setSelectedId(null)
                      }}
                    >
                      {labels[value] ?? '전체'}
                    </Button>
                  ))}
                </div>
              )}
              {kind === 'reports' && (
                <div className="mb-4 flex flex-wrap gap-2" aria-label="신고 대상">
                  {['', 'MEMBER', 'COMMENT', 'MESSAGE', 'POST', 'BOARD_POST'].map((t) => (
                    <Button
                      key={t}
                      size="sm"
                      variant={type === t ? 'secondary' : 'outline'}
                      aria-pressed={type === t}
                      onClick={() => {
                        setType(t)
                        setPage(0)
                        setSelectedId(null)
                      }}
                    >
                      {labels[t] ?? '전체 대상'}
                    </Button>
                  ))}
                </div>
              )}
              {kind === 'posts' && (
                <label className="mb-4 flex gap-2 text-small">
                  <input
                    type="checkbox"
                    checked={reportedFirst}
                    onChange={(e) => {
                      setReportedFirst(e.target.checked)
                      setPage(0)
                    }}
                  />
                  신고된 글 우선 보기
                </label>
              )}
              <p className="mb-3 text-small text-text-sub">총 {query.data?.totalElements ?? 0}건</p>
              {query.isPending && <p role="status">불러오는 중입니다…</p>}
              {query.isError && (
                <div role="alert">
                  <p className="mb-3 text-danger">{errorText(query.error)}</p>
                  <Button onClick={() => query.refetch()}>다시 시도</Button>
                </div>
              )}
              {query.data?.content.length === 0 && (
                <p className="py-12 text-center text-text-sub">조회된 항목이 없습니다.</p>
              )}
              <div className="divide-y divide-divider">
                {query.data?.content.map((item) => (
                  <button
                    key={item.id}
                    aria-pressed={selected?.id === item.id && !creating}
                    className={`block w-full cursor-pointer border-l-2 px-3 py-4 text-left hover:bg-bg ${selected?.id === item.id && !creating ? 'border-text bg-surface-sub' : 'border-transparent'}`}
                    onClick={() => {
                      setDetailOpen(true)
                      if (kind === 'contests') setContestView('edit')
                      setSelectedId(item.id)
                      setCreating(false)
                      setFeedback('')
                    }}
                  >
                    <div className="flex justify-between gap-3">
                      <span className="font-bold">
                        {item.title ??
                          item.nickname ??
                          `${labels[item.targetType ?? ''] ?? '신고'} #${item.id}`}
                      </span>
                      {(item.deletedAt || item.status) && (
                        <span className="h-fit shrink-0 rounded-full border border-line-strong px-2 py-1 text-caption text-text-sub">
                          {item.deletedAt ? '숨김' : (labels[item.status ?? ''] ?? '')}
                        </span>
                      )}
                    </div>
                    <p className="mt-2 line-clamp-2 text-small break-words text-text-sub">
                      {item.email ??
                        (item.reason ? formatReportReason(item.reason) : undefined) ??
                        item.categoryName ??
                        item.content}
                    </p>
                    <p className="mt-2 text-caption text-text-sub">
                      #{item.id} · <Stamp value={item.createdAt ?? item.endDate} />
                      {item.reportCount ? ` · 접수 신고 ${item.reportCount}건` : ''}
                    </p>
                  </button>
                ))}
              </div>
              <div className="mt-6 flex items-center justify-center gap-4">
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
                <span className="text-small">
                  {page + 1} / {Math.max(query.data?.totalPages ?? 1, 1)}
                </span>
                <Button
                  size="sm"
                  disabled={!query.data || page + 1 >= query.data.totalPages}
                  onClick={() => {
                    setPage(page + 1)
                    setSelectedId(null)
                  }}
                >
                  다음
                </Button>
              </div>
            </section>
          </div>
        )}
        {kind === 'contests' && contestView === 'categories' && <CategoryPanel act={act} />}
        {(kind === 'contests' ? contestView === 'edit' : isWide || detailOpen) && (
          <section className="min-w-0 space-y-4" aria-label="상세 및 처리">
            {(!isWide || kind === 'contests') && (
              <Button
                onClick={() => {
                  setDetailOpen(false)
                  if (kind === 'contests') setContestView('list')
                  setCreating(false)
                }}
              >
                ← 목록으로
              </Button>
            )}
            {kind === 'notices' && (creating || selected) ? (
              <NoticeForm
                key={creating ? 'new' : `${selected!.id}-${selected!.title}-${selected!.content}`}
                item={creating ? undefined : selected}
                act={act}
              />
            ) : selected ? (
              <>
                {kind === 'members' && <MemberDetail key={selected.id} item={selected} act={act} />}
                {kind === 'reports' && <ReportDetail key={selected.id} item={selected} act={act} />}
                {kind === 'posts' && <PostDetail item={selected} act={act} />}
                {kind === 'contests' && (
                  <ContestForm
                    key={`${selected.id}-${selected.endDate}-${selected.categoryId}`}
                    item={selected}
                    act={act}
                  />
                )}
              </>
            ) : (
              <div className={panel}>
                <p className="text-text-sub">
                  {kind === 'contests'
                    ? '공모전 목록에서 수정할 공모전을 선택해 주세요.'
                    : '목록에서 항목을 선택해 주세요.'}
                </p>
              </div>
            )}
          </section>
        )}
      </div>
      <Modal
        open={confirmation !== null}
        title={confirmation?.label ?? '처리 확인'}
        description="선택한 대상에 이 작업을 적용할까요?"
        onClose={() => {
          if (!mutation.isPending) setConfirmation(null)
        }}
        footer={
          <>
            <Button disabled={mutation.isPending} onClick={() => setConfirmation(null)}>
              취소
            </Button>
            <Button
              variant="primary"
              loading={mutation.isPending}
              onClick={() => confirmation && mutation.mutate(confirmation)}
            >
              확인
            </Button>
          </>
        }
      >
        <p className="text-small break-words">{confirmation?.targetLabel}</p>
        {mutation.isError && (
          <p role="alert" className="mt-4 text-danger">
            {errorText(mutation.error)}
          </p>
        )}
      </Modal>
    </main>
  )
}
function NoticeForm({ item, act }: { item?: AdminItem; act: Act }) {
  const [title, setTitle] = useState(item?.title ?? '')
  const [content, setContent] = useState(item?.content ?? '')
  const [policyChange, setPolicyChange] = useState(item?.policyChange ?? false)
  const [sendEmail, setSendEmail] = useState(false)
  return (
    <form
      className={`${panel} space-y-5`}
      onSubmit={(e) => {
        e.preventDefault()
        act({
          label: sendEmail
            ? '정책 변경 공지 저장 및 전체 회원 이메일 발송 요청'
            : item
              ? '공지 수정'
              : '공지 등록 및 전체 회원 사이트 알림',
          path: `service-notices${item ? `/${item.id}` : ''}`,
          method: item ? 'patch' : 'post',
          body: { title, content, policyChange, sendEmail },
        })
      }}
    >
      <h2 className="text-subheading font-bold">{item ? '운영 공지 수정' : '새 운영 공지'}</h2>
      <Field label="제목 (필수)">
        <input
          required
          maxLength={50}
          className={field}
          value={title}
          disabled={!!item?.deletedAt}
          onChange={(e) => setTitle(e.target.value)}
        />
      </Field>
      <Field label="내용 (필수)">
        <textarea
          required
          maxLength={2000}
          rows={10}
          className={field}
          value={content}
          disabled={!!item?.deletedAt}
          onChange={(e) => setContent(e.target.value)}
        />
      </Field>
      <p className="text-small text-text-sub">
        {content.length} / 2,000 · 등록 시 전체 회원에게 사이트 알림을 보냅니다.
      </p>
      <label className="flex items-center gap-2 text-small">
        <input
          type="checkbox"
          checked={policyChange}
          disabled={!!item?.deletedAt || !!item?.emailQueuedAt}
          onChange={(e) => {
            setPolicyChange(e.target.checked)
            if (!e.target.checked) setSendEmail(false)
          }}
        />
        정책 변경 공지
      </label>
      {policyChange && !item?.emailQueuedAt && (
        <label className="flex items-center gap-2 text-small">
          <input
            type="checkbox"
            checked={sendEmail}
            disabled={!!item?.deletedAt}
            onChange={(e) => setSendEmail(e.target.checked)}
          />
          저장 시 전체 회원에게 이메일 발송 요청
        </label>
      )}
      {item?.emailQueuedAt && (
        <p className="text-small text-text-sub">
          이메일 발송 요청됨 · {item.emailQueuedAt} (재발송하지 않음)
        </p>
      )}
      <div className="flex flex-wrap justify-end gap-3">
        {item && !item.deletedAt && (
          <Button
            variant="danger"
            onClick={() =>
              act({
                label: '공지 소프트 삭제',
                path: `service-notices/${item.id}`,
                method: 'delete',
              })
            }
          >
            소프트 삭제
          </Button>
        )}
        <MobileActionBar>
          <Button
            variant="primary"
            type="submit"
            disabled={!!item?.deletedAt || !title.trim() || !content.trim()}
          >
            {item?.deletedAt ? '삭제된 공지' : item ? '수정 저장' : '공지 등록'}
          </Button>
        </MobileActionBar>
      </div>
    </form>
  )
}
function MemberDetail({ item, act }: { item: AdminItem; act: Act }) {
  return (
    <>
      <div className={panel}>
        <h2 className="mb-5 text-subheading font-bold">회원 상세 · 로그인 잠금 관리</h2>
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Info label="이메일">{item.email}</Info>
          <Info label="닉네임">{item.nickname}</Info>
          <Info label="계정 상태">{labels[item.status ?? '']}</Info>
          <Info label="권한">{labels[item.role ?? '']}</Info>
          <Info label="로그인 실패">{item.loginFailCount}회</Info>
          <Info label="잠금 종료">
            <Stamp value={item.lockedUntil} />
          </Info>
          <Info label="이용 정지 종료">
            <Stamp value={item.suspendedUntil} />
          </Info>
          <Info label="경고">{item.warningCount}회</Info>
          <Info label="닉네임 변경 요구">{item.nicknameChangeRequired ? '변경 필요' : '없음'}</Info>
          <Info label="최근 로그인">
            <Stamp value={item.lastLoginAt} />
          </Info>
        </dl>
        <Button
          className="mt-6"
          disabled={item.status !== 'ACTIVE'}
          onClick={() =>
            act({
              label: '로그인 잠금 해제 및 실패 횟수 초기화',
              path: `member-management/${item.id}/actions`,
              method: 'post',
              body: { action: 'UNLOCK' },
            })
          }
        >
          로그인 잠금 해제
        </Button>
        <p className="mt-4 text-small text-text-sub">잠금 해제는 이용 정지를 해제하지 않습니다.</p>
        <Button
          className="mt-4"
          disabled={item.status !== 'ACTIVE' || item.role === 'ADMIN'}
          onClick={() =>
            act({
              label: '관리자 권한 부여 (다른 회원에게 관리자 권한을 부여할 수 있는 동일 권한)',
              path: `members/${item.id}/admin-role`,
              method: 'post',
            })
          }
        >
          {item.role === 'ADMIN' ? '관리자 권한 보유' : '관리자 권한 부여'}
        </Button>
        <p className="mt-3 text-small text-text-sub">
          모든 관리자는 동일한 관리 권한을 가지며, 다른 회원에게도 관리자 권한을 부여할 수 있습니다.
        </p>
      </div>
      <ActionForm
        title="계정 제재 · 이용 정지 관리"
        disabled={item.status !== 'ACTIVE'}
        actions={['WARN', 'SUSPEND', 'UNSUSPEND', 'REQUIRE_NICKNAME']}
        onAction={(body) =>
          act({
            label: '회원 조치 적용',
            path: `member-management/${item.id}/actions`,
            method: 'post',
            body,
          })
        }
      />
    </>
  )
}
const actionLabels: Record<string, string> = {
  WARN: '경고',
  SUSPEND: '이용 정지',
  UNSUSPEND: '이용 정지 해제',
  REQUIRE_NICKNAME: '닉네임 변경 요구',
  HIDE_COMMENT: '댓글 숨김',
  HIDE_POST: '모집글 강제 숨김',
  HIDE_BOARD_POST: '게시글 강제 숨김',
  DISMISS: '신고 기각',
}
function ActionForm({
  title = '신고 처리 및 계정 조치',
  actions,
  disabled = false,
  onAction,
}: {
  title?: string
  actions: string[]
  disabled?: boolean
  onAction: (body: { action: string; reason: string; until?: string }) => void
}) {
  const [action, setAction] = useState(actions[0])
  const [reason, setReason] = useState('')
  const [until, setUntil] = useState('')
  return (
    <form
      className={`${panel} space-y-4`}
      onSubmit={(e) => {
        e.preventDefault()
        onAction({ action, reason, ...(action === 'SUSPEND' ? { until: `${until}:00` } : {}) })
      }}
    >
      <h2 className="text-subheading font-bold">{title}</h2>
      <fieldset disabled={disabled}>
        <legend className="mb-2 text-small text-text-sub">적용할 조치를 선택하세요.</legend>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {actions.map((a) => (
            <label
              key={a}
              className={`flex cursor-pointer items-center gap-2 rounded-full border px-4 py-2 text-small ${action === a ? 'border-text bg-text text-surface' : 'border-line-strong bg-surface'}`}
            >
              <input
                className="accent-primary"
                type="radio"
                name="moderation-action"
                value={a}
                checked={action === a}
                onChange={() => setAction(a)}
              />
              {actionLabels[a]}
            </label>
          ))}
        </div>
      </fieldset>
      {action === 'SUSPEND' && (
        <Field label="정지 종료 시각 (한국 시간)">
          <input
            type="datetime-local"
            required
            className={field}
            value={until}
            onChange={(e) => setUntil(e.target.value)}
          />
        </Field>
      )}
      <Field label="처리 사유 (필수, 최대 200자)">
        <textarea
          disabled={disabled}
          required
          maxLength={200}
          rows={4}
          className={field}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </Field>
      <p className="text-small text-text-sub">{reason.length} / 200</p>
      <div className="flex justify-end">
        <MobileActionBar>
          <Button type="submit" variant="primary" disabled={disabled || !reason.trim()}>
            조치 적용
          </Button>
        </MobileActionBar>
      </div>
    </form>
  )
}
function ReportDetail({ item, act }: { item: AdminItem; act: Act }) {
  const detail = useQuery({
    queryKey: ['admin', 'report-detail', item.id],
    queryFn: () =>
      readAdmin<{
        report: AdminItem
        memberId: number
        content: string
        deletedAt?: string | null
      }>(`reports/${item.id}`),
  })
  const actions = [
    ...(item.targetType === 'COMMENT'
      ? ['HIDE_COMMENT']
      : item.targetType === 'POST'
        ? ['HIDE_POST']
        : item.targetType === 'BOARD_POST'
          ? ['HIDE_BOARD_POST']
          : []),
    'REQUIRE_NICKNAME',
    'WARN',
    'SUSPEND',
    'DISMISS',
  ]
  return (
    <>
      <div className={panel}>
        <h2 className="mb-4 text-subheading font-bold">
          신고 #{item.id} · {labels[item.status ?? '']}
        </h2>
        <p className="break-words whitespace-pre-wrap">{formatReportReason(item.reason)}</p>
        <dl className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Info label="신고자">#{item.reporterId}</Info>
          <Info label="대상">
            {labels[item.targetType ?? '']} #{item.targetId ?? detail.data?.memberId}
          </Info>
          <Info label="처리자">{item.reviewerId}</Info>
          <Info label="처리 시각">
            <Stamp value={item.reviewedAt} />
          </Info>
        </dl>
        <h3 className="mt-6 font-bold">대상 내용</h3>
        {detail.data?.deletedAt && (
          <p role="status" className="mt-2 text-small text-text-sub">
            이미 삭제된 글입니다. 삭제 시각: <Stamp value={detail.data.deletedAt} />
          </p>
        )}
        {detail.isPending ? (
          <p>불러오는 중…</p>
        ) : detail.isError ? (
          <p role="alert" className="text-danger">
            {errorText(detail.error)}
          </p>
        ) : (
          <p className="mt-3 rounded-input bg-bg p-4 break-words whitespace-pre-wrap">
            {detail.data.content ?? '텍스트가 없는 대상입니다.'}
          </p>
        )}
        {item.reviewNote && (
          <p className="mt-4 whitespace-pre-wrap">처리 사유: {item.reviewNote}</p>
        )}
      </div>
      <ActionForm
        actions={actions}
        disabled={item.status !== 'RECEIVED' || detail.isPending || detail.isError}
        onAction={(body) =>
          act({
            label: actionLabels[body.action],
            path: `reports/${item.id}/review`,
            method: 'post',
            body,
          })
        }
      />
    </>
  )
}
function PostDetail({ item, act }: { item: AdminItem; act: Act }) {
  const comments = useQuery({
    queryKey: ['admin', 'post-comments', item.id],
    queryFn: () => readAdmin<AdminItem[]>(`posts/${item.id}/comments`),
  })
  return (
    <>
      <div className={panel}>
        <h2 className="text-subheading font-bold">{item.title}</h2>
        <p className="mt-2 text-small text-text-sub">
          작성자 #{item.authorId} · {labels[item.status ?? '']}
        </p>
        <p className="my-6 break-words whitespace-pre-wrap">{item.content}</p>
        <p className="mb-4 text-small text-text-sub">
          강제 숨김 시 모집을 종료하고 지원자 전원에게 알립니다. 팀과 워크스페이스는 유지됩니다.
        </p>
        <Button
          variant="danger"
          disabled={!!item.deletedAt}
          onClick={() =>
            act({
              label: '모집글 강제 숨김 및 지원자 알림',
              path: `posts/${item.id}/hide`,
              method: 'post',
            })
          }
        >
          {item.deletedAt ? '숨김 처리됨' : '강제 숨김'}
        </Button>
      </div>
      <div className={panel}>
        <h2 className="mb-4 text-subheading font-bold">댓글</h2>
        {comments.isPending && <p>불러오는 중…</p>}
        {comments.isError && (
          <p role="alert" className="text-danger">
            {errorText(comments.error)}
          </p>
        )}
        {comments.data?.length === 0 && <p className="text-text-sub">등록된 댓글이 없습니다.</p>}
        {comments.data?.map((c) => (
          <div className="border-b border-divider py-4" key={c.id}>
            <p className="break-words whitespace-pre-wrap">{c.content}</p>
            <Button
              className="mt-3"
              size="sm"
              disabled={!!c.deletedAt}
              variant="danger"
              onClick={() =>
                act({ label: `댓글 #${c.id} 숨김`, path: `comments/${c.id}/hide`, method: 'post' })
              }
            >
              댓글 숨김
            </Button>
          </div>
        ))}
      </div>
    </>
  )
}
function ContestForm({ item, act }: { item: AdminItem; act: Act }) {
  const [title, setTitle] = useState(item.title ?? '')
  const [endDate, setEndDate] = useState(item.endDate ?? '')
  const [categoryId, setCategoryId] = useState(item.categoryId ?? 0)
  const [confirmed, setConfirmed] = useState(false)
  const categories = useQuery({
    queryKey: ['admin', 'categories'],
    queryFn: () => readAdmin<Category[]>('categories'),
  })
  const teams = useQuery({
    queryKey: ['admin', 'contest-teams', item.id],
    queryFn: () => readAdmin<{ id: number; name: string }[]>(`contests/${item.id}/teams`),
  })
  return (
    <form
      className={`${panel} space-y-5`}
      onSubmit={(e: FormEvent) => {
        e.preventDefault()
        act({
          label: `공모전 수정 · 연결 팀 ${teams.data?.length ?? 0}개 일정 재계산`,
          path: `contests/${item.id}`,
          method: 'patch',
          body: { title, endDate, categoryId, expectedEndDate: item.endDate },
        })
      }}
    >
      <h2 className="text-subheading font-bold">공모전 수정 · 영향 팀 확인</h2>
      <Field label="제목">
        <input
          required
          maxLength={200}
          className={field}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
      </Field>
      <Field label="접수 마감일">
        <input
          type="date"
          required
          className={field}
          value={endDate}
          onChange={(e) => {
            setEndDate(e.target.value)
            setConfirmed(false)
          }}
        />
      </Field>
      <Field label="카테고리">
        <select
          required
          className={field}
          value={categoryId}
          onChange={(e) => setCategoryId(Number(e.target.value))}
        >
          {categories.data?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </Field>
      {categories.isError && <p className="text-danger">{errorText(categories.error)}</p>}
      <div className="rounded-input bg-bg p-4">
        <h3 className="font-bold">연결된 팀 {teams.data?.length ?? 0}개</h3>
        {teams.isPending && <p>확인 중…</p>}
        {teams.isError && (
          <p role="alert" className="text-danger">
            {errorText(teams.error)}
          </p>
        )}
        <ul className="mt-2 space-y-1 text-small">
          {teams.data?.map((t) => (
            <li key={t.id}>
              #{t.id} {t.name}
            </li>
          ))}
        </ul>
        <p className="mt-3 text-small">
          마감일 변경 시 연결 팀의 D-7·D-1·마감 일정과 워크스페이스 종료 기준일이 함께 변경됩니다.
        </p>
      </div>
      <label className="flex gap-2 text-small">
        <input
          type="checkbox"
          checked={confirmed}
          onChange={(e) => setConfirmed(e.target.checked)}
        />
        영향받는 팀과 변경 내용을 확인했습니다.
      </label>
      <MobileActionBar>
        <Button
          type="submit"
          variant="primary"
          disabled={!confirmed || !teams.isSuccess || !categories.isSuccess}
        >
          수정 저장
        </Button>
      </MobileActionBar>
    </form>
  )
}
function CategoryPanel({ act }: { act: Act }) {
  const categories = useQuery({
    queryKey: ['admin', 'categories'],
    queryFn: () => readAdmin<Category[]>('categories'),
  })
  return (
    <>
      <div className={panel}>
        <h2 className="mb-4 text-subheading font-bold">카테고리 관리</h2>
        <p className="mb-4 text-small text-text-sub">카테고리 이름과 표시 순서를 수정합니다.</p>
        {categories.isPending && <p role="status">카테고리를 불러오는 중입니다…</p>}
        {categories.isError && (
          <div role="alert">
            <p className="mb-3 text-danger">{errorText(categories.error)}</p>
            <Button onClick={() => categories.refetch()}>다시 시도</Button>
          </div>
        )}
        {categories.isSuccess && categories.data.length === 0 && <p>등록된 카테고리가 없습니다.</p>}
        {categories.data?.map((c) => (
          <CategoryRow key={`${c.id}-${c.name}-${c.sortOrder}`} item={c} act={act} />
        ))}
      </div>
    </>
  )
}
function CategoryRow({ item, act }: { item: Category; act: Act }) {
  const [name, setName] = useState(item.name)
  const [sortOrder, setSortOrder] = useState(item.sortOrder)
  return (
    <form
      className="space-y-2 border-t border-divider py-4"
      onSubmit={(e) => {
        e.preventDefault()
        act({
          label: `${item.name} 카테고리 수정`,
          targetLabel: `${item.name} → ${name} · 정렬 순서 ${sortOrder}`,
          path: `categories/${item.id}`,
          method: 'patch',
          body: { name, sortOrder },
        })
      }}
    >
      <p className="text-caption text-text-sub">{item.code}</p>
      <div className="flex flex-wrap gap-2">
        <input
          aria-label={`${item.code} 이름`}
          required
          maxLength={20}
          className={`${field} flex-1`}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <input
          aria-label={`${item.code} 정렬 순서`}
          type="number"
          min={0}
          required
          className="w-20 rounded-input border border-line-input px-3"
          value={sortOrder}
          onChange={(e) => setSortOrder(Number(e.target.value))}
        />
        <Button type="submit" size="sm">
          저장
        </Button>
      </div>
    </form>
  )
}
