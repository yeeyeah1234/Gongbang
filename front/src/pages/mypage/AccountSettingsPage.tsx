import { useIsWide } from '../../hooks/useIsWide'
import { MobileActionBar } from '../../components/layout/MobileActionBar'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getApiError, getErrorMessage } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Modal } from '../../components/ui/Modal'
import { Toast } from '../../components/ui/Toast'
import { Toggle } from '../../components/ui/Toggle'
import { useTimedToast } from '../../hooks/useTimedToast'
import { PasswordInput } from '../../features/auth/components/PasswordInput'
import { PasswordRules } from '../../features/auth/components/PasswordRules'
import { isPasswordValid } from '../../features/auth/passwordRules'
import { useAuthStore } from '../../features/auth/store'
import { changePassword, withdraw } from '../../features/member/api'
import { MyPageLayout } from '../../features/member/components/MyPageLayout'
import { formatDate } from '../../features/member/constants'
import { useMyProfile } from '../../features/member/hooks'
import {
  fetchNotificationSettings,
  saveNotificationSettings,
} from '../../features/notification/api'
import type { NotificationSetting, SettingType } from '../../features/notification/types'

type Tab = 'password' | 'notification' | 'withdraw'
const TABS: { key: Tab; label: string }[] = [
  { key: 'password', label: '비밀번호 변경' },
  { key: 'notification', label: '알림 수신 설정' },
  { key: 'withdraw', label: '회원 탈퇴' },
]

// MY-04·10·11 / Figma 11-1·11-2·11-3·20-2·20-3
export default function AccountSettingsPage() {
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((t) => t.key === params.get('tab'))?.key ?? 'password') as Tab
  const toast = useTimedToast()

  return (
    <MyPageLayout breadcrumb="마이페이지 / 계정 설정" title="계정 설정">
      <div
        className="grid grid-cols-3 gap-2 border-b border-divider sm:flex sm:gap-10"
        role="tablist"
      >
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setParams({ tab: t.key })}
            className={[
              '-mb-px h-[52px] cursor-pointer border-b-2 text-subheading',
              tab === t.key
                ? 'border-primary font-bold text-text'
                : 'border-transparent text-text-sub',
            ].join(' ')}
          >
            <span className="inline-flex flex-col items-center text-small whitespace-nowrap sm:block sm:text-subheading">
              {t.key === 'withdraw' ? (
                <>
                  회원<span className="hidden sm:inline"> </span>
                  <span className="sm:inline">탈퇴</span>
                </>
              ) : t.key === 'password' ? (
                <>
                  비밀번호<span className="hidden sm:inline"> </span>
                  <span className="sm:inline">변경</span>
                </>
              ) : (
                <>
                  알림 수신<span className="hidden sm:inline"> </span>
                  <span className="sm:inline">설정</span>
                </>
              )}
            </span>
          </button>
        ))}
      </div>
      <div className="mt-6">
        {tab === 'password' && <PasswordTab onToast={toast.show} />}
        {tab === 'notification' && (
          <NotificationSettingsTab onToast={toast.show} offType={params.get('off')} />
        )}
        {tab === 'withdraw' && <WithdrawTab />}
      </div>
      <Toast message={toast.message} />
    </MyPageLayout>
  )
}

// MY-04 비밀번호 변경 (Figma 11-1)
function PasswordTab({ onToast }: { onToast: (m: string) => void }) {
  const { data: profile, isPending, isError, refetch } = useMyProfile()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  if (isPending) return <p role="status">계정 정보를 불러오는 중입니다…</p>
  if (isError || !profile)
    return (
      <div role="alert">
        <p>계정 정보를 불러오지 못했어요.</p>
        <Button onClick={() => refetch()}>다시 시도</Button>
      </div>
    )

  // 소셜 계정의 비밀번호는 해당 로그인 서비스에서 관리한다.
  if (profile.loginType !== 'EMAIL') {
    return (
      <p className="rounded-input bg-bg px-5 py-4 text-small text-text-sub">
        {profile.loginType === 'GOOGLE' ? '구글' : '카카오'}로 가입한 계정은 해당 로그인 서비스에서
        비밀번호를 관리해요
      </p>
    )
  }

  const mismatch = confirm.length > 0 && next !== confirm
  const ready = current && isPasswordValid(next) && next === confirm && next !== current

  const submit = async () => {
    if (!ready || saving) return
    setSaving(true)
    setError(null)
    try {
      await changePassword(current, next)
      setCurrent('')
      setNext('')
      setConfirm('')
      onToast('비밀번호를 바꿨어요')
    } catch (e) {
      setError(getApiError(e).message ?? '비밀번호를 바꾸지 못했어요')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form
      className="mx-auto flex w-full max-w-[560px] flex-col gap-5 rounded-card border border-line bg-surface p-4 sm:p-6"
      onSubmit={(event) => {
        event.preventDefault()
        void submit()
      }}
    >
      <div>
        <PasswordInput
          id="current-password"
          label="현재 비밀번호"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          errorText={error ?? undefined}
        />
        {/* ④ 기억나지 않으면 */}
        <Link
          to="/find-password"
          className="mt-2 inline-block text-caption text-text-sub underline"
        >
          비밀번호가 기억나지 않나요? 비밀번호 찾기
        </Link>
      </div>
      <div>
        <PasswordInput
          id="next-password"
          label="새 비밀번호"
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
        />
        <PasswordRules password={next} current={current} />
      </div>
      <PasswordInput
        id="next-password-confirm"
        label="새 비밀번호 확인"
        autoComplete="new-password"
        value={confirm}
        onChange={(e) => setConfirm(e.target.value)}
        errorText={mismatch ? '비밀번호가 일치하지 않아요' : undefined}
        successText={confirm && !mismatch ? '비밀번호가 일치해요' : undefined}
      />
      <div className="rounded-input bg-bg px-4 py-3">
        <p className="text-small font-bold text-text">현재 기기는 로그인 상태가 유지돼요</p>
        <p className="mt-1 text-caption text-text-sub">
          · 비밀번호를 바꾸면 이 기기를 제외한 다른 기기에서는 모두 로그아웃돼요.
        </p>
      </div>
      <div className="flex flex-wrap items-center justify-end gap-3">
        <MobileActionBar>
          <Button
            type="button"
            variant="outline"
            disabled={saving}
            onClick={() => {
              setCurrent('')
              setNext('')
              setConfirm('')
              setError(null)
            }}
          >
            취소
          </Button>
          <Button type="submit" variant="primary" disabled={!ready || saving} loading={saving}>
            비밀번호 변경
          </Button>
        </MobileActionBar>
      </div>
    </form>
  )
}

// MY-10 알림 수신 설정 (Figma 11-2). ④ 바꾸는 즉시 저장 + 「저장했어요」
const SETTING_ROWS: { type: SettingType; title: string; description: string }[] = [
  {
    type: 'COMMUNITY_NEW',
    title: '커뮤니티 새 글',
    description: '수신을 켜면 새 게시글을 사이트 알림으로 알려드려요. 이메일은 보내지 않아요.',
  },
  { type: 'TEAM_INVITE', title: '팀 초대', description: '새로운 팀 초대를 알려드려요.' },
  {
    type: 'APPLY_RESULT',
    title: '지원 결과',
    description: '모집글 지원의 수락·거절 결과를 알려드려요.',
  },
  {
    type: 'APPLY_NEW',
    title: '내 모집글 새 지원',
    description: '내 모집글에 새 지원이 오면 알려드려요.',
  },
  {
    type: 'POST_DELETED',
    title: '지원한 모집글 삭제',
    description: '지원한 모집글이 삭제되면 알려드려요.',
  },
  { type: 'COMMENT', title: '댓글·답글', description: '내 글에 달린 댓글과 답글을 알려드려요.' },
  {
    type: 'WORKSPACE',
    title: '워크스페이스',
    description: '일정·새 공지·공모전 마감 임박을 알려드려요. (일정 알림 메일은 기본 꺼짐)',
  },
  {
    type: 'BOOKMARK_DEADLINE',
    title: '북마크 공모전 마감',
    description: '저장한 공모전의 마감 전날 알려드려요.',
  },
]

function NotificationSettingsTab({
  onToast,
  offType,
}: {
  onToast: (m: string) => void
  offType: string | null
}) {
  const isWide = useIsWide()
  const queryClient = useQueryClient()
  const {
    data,
    isLoading,
    isError,
    error: loadError,
    refetch,
  } = useQuery({
    queryKey: ['notifications', 'settings'],
    queryFn: fetchNotificationSettings,
    retry: false,
  })
  const [settings, setSettings] = useState<NotificationSetting[] | null>(null)
  const list = settings ?? data ?? []
  const savingRef = useRef(false)
  const [saving, setSaving] = useState(false)

  const save = async (nextList: NotificationSetting[], changed: NotificationSetting[]) => {
    if (savingRef.current || !data || isError) return
    savingRef.current = true
    setSaving(true)
    setSettings(nextList)
    try {
      await queryClient.cancelQueries({ queryKey: ['notifications', 'settings'] })
      const saved = await saveNotificationSettings(changed)
      setSettings(saved)
      queryClient.setQueryData(['notifications', 'settings'], saved)
      onToast('저장했어요')
    } catch (e) {
      setSettings(null)
      onToast(getErrorMessage(e))
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }

  // NOTI-06 ⑦ 메일의 [이 종류 메일 끄기] 링크로 들어오면 그 종류 이메일을 끈다
  useEffect(() => {
    if (!offType || !data) return
    const target = data.find((s) => s.type === offType)
    if (target && target.emailEnabled) {
      const changed = { ...target, emailEnabled: false }
      const timer = window.setTimeout(
        () =>
          void save(
            data.map((s) => (s.type === offType ? changed : s)),
            [changed],
          ),
        0,
      )
      return () => window.clearTimeout(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 들어올 때 한 번
  }, [offType, data])

  const change = (type: SettingType, field: 'siteEnabled' | 'emailEnabled', value: boolean) => {
    const target = list.find((s) => s.type === type)
    if (!target) return
    const changed = { ...target, [field]: value }
    void save(
      list.map((s) => (s.type === type ? changed : s)),
      [changed],
    )
  }

  const allEmailOff = list.length > 0 && list.every((s) => !s.emailEnabled)
  const turnOffAllEmail = (off: boolean) => {
    const changed = list.map((s) => ({ ...s, emailEnabled: s.type !== 'COMMUNITY_NEW' && !off }))
    void save(changed, changed)
  }

  if (isLoading)
    return (
      <div
        role="status"
        aria-label="알림 설정 조회 중"
        className="h-64 animate-pulse rounded-card bg-surface-sub"
      />
    )
  if (isError || !data)
    return (
      <div role="alert" className="space-y-3">
        <p>{getErrorMessage(loadError, '알림 설정을 불러오지 못했어요.')}</p>
        <Button onClick={() => refetch()}>다시 시도</Button>
      </div>
    )

  return (
    <div className="mx-auto w-full max-w-[960px]">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-input bg-surface-sub px-5 py-4">
        <div>
          <p className="text-body font-bold text-text">이메일 알림 모두 끄기</p>
          <p className="text-caption text-text-sub">사이트 알림은 기존 설정을 유지해요.</p>
        </div>
        <Toggle
          disabled={saving}
          label="이메일 알림 모두 끄기"
          checked={allEmailOff}
          onChange={turnOffAllEmail}
        />
      </div>
      {!isWide ? (
        <div className="mt-4 divide-y divide-divider">
          {SETTING_ROWS.map((row) => {
            const setting = list.find((s) => s.type === row.type)
            return (
              <section key={row.type} className="space-y-3 py-4">
                <h3 className="text-body font-bold">{row.title}</h3>
                <p className="text-small text-text-sub">{row.description}</p>
                <div className="flex flex-wrap gap-6">
                  <div className="flex min-h-10 items-center gap-3">
                    <span>사이트</span>
                    <Toggle
                      disabled={saving}
                      label={`${row.title} 사이트 알림`}
                      checked={setting?.siteEnabled ?? row.type !== 'COMMUNITY_NEW'}
                      onChange={(v) => change(row.type, 'siteEnabled', v)}
                    />
                  </div>
                  <div className="flex min-h-10 items-center gap-3">
                    <span>이메일</span>
                    {row.type === 'COMMUNITY_NEW' ? (
                      <span>발송 안 함</span>
                    ) : (
                      <Toggle
                        disabled={saving}
                        label={`${row.title} 이메일 알림`}
                        checked={setting?.emailEnabled ?? true}
                        onChange={(v) => change(row.type, 'emailEnabled', v)}
                      />
                    )}
                  </div>
                </div>
              </section>
            )
          })}
          <p className="py-4 text-small text-text-sub">
            운영 공지 · 계정 보안 메일은 끌 수 없어요.
          </p>
        </div>
      ) : (
        <table className="mt-4 w-full border-collapse text-small">
          <thead>
            <tr className="bg-surface-sub text-text-sub">
              <th className="px-5 py-3 text-left font-medium">알림 종류</th>
              <th className="w-28 px-5 py-3 font-medium">사이트</th>
              <th className="w-28 px-5 py-3 font-medium">이메일</th>
            </tr>
          </thead>
          <tbody>
            {SETTING_ROWS.map((row) => {
              const s = list.find((x) => x.type === row.type)
              return (
                <tr key={row.type} className="border-b border-divider">
                  <td className="px-5 py-3">
                    <p className="font-bold text-text">{row.title}</p>
                    <p className="text-caption text-text-sub">{row.description}</p>
                  </td>
                  <td className="px-5 py-3 text-center">
                    <Toggle
                      disabled={saving}
                      label={`${row.title} 사이트 알림`}
                      checked={s?.siteEnabled ?? row.type !== 'COMMUNITY_NEW'}
                      onChange={(v) => change(row.type, 'siteEnabled', v)}
                    />
                  </td>
                  <td className="px-5 py-3 text-center">
                    {row.type === 'COMMUNITY_NEW' ? (
                      <span>발송 안 함</span>
                    ) : (
                      <Toggle
                        disabled={saving}
                        label={`${row.title} 이메일 알림`}
                        checked={s?.emailEnabled ?? true}
                        onChange={(v) => change(row.type, 'emailEnabled', v)}
                      />
                    )}
                  </td>
                </tr>
              )
            })}
            {/* ⑤ 끌 수 없는 것 */}
            <tr>
              <td className="px-5 py-3">
                <p className="font-bold text-text">운영 공지 · 계정 보안 메일</p>
                <p className="text-caption text-text-sub">
                  운영 공지 이메일은 정책 변경 시에만 보내요. 정책 변경·인증 결과·계정 보안 메일은
                  끌 수 없어요.
                </p>
              </td>
              <td className="px-5 py-3 text-center">
                <Toggle label="운영 공지 사이트 알림" checked disabled onChange={() => undefined} />
              </td>
              <td className="px-5 py-3 text-center">
                <Toggle label="보안 메일" checked disabled onChange={() => undefined} />
              </td>
            </tr>
          </tbody>
        </table>
      )}
      <p className="mt-3 text-caption text-text-sub">
        {saving ? '저장 중입니다…' : '바꾸는 즉시 저장돼요.'}
      </p>
    </div>
  )
}

// MY-11 회원 탈퇴 (Figma 11-3·20-2)
function WithdrawTab() {
  const navigate = useNavigate()
  const { data: profile, isPending, isError, refetch } = useMyProfile()
  const [confirmText, setConfirmText] = useState('')
  const [password, setPassword] = useState('')
  const [agree, setAgree] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [leaderBlocked, setLeaderBlocked] = useState(false)
  const [done, setDone] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const isSocial = !!profile && profile.loginType !== 'EMAIL'
  const ready =
    !!profile && !isError && confirmText.trim() === '탈퇴합니다' && agree && (isSocial || password)

  const submit = async () => {
    if (!ready || saving) return
    setSaving(true)
    setError(null)
    try {
      const res = await withdraw(password, confirmText.trim())
      setDone(formatDate(res.restoreDeadline))
    } catch (e) {
      const { code, message } = getApiError(e)
      if (code === 'LEADER_CANNOT_WITHDRAW') setLeaderBlocked(true)
      else setError(message ?? '탈퇴하지 못했어요')
    } finally {
      setSaving(false)
    }
  }

  if (isPending) return <p role="status">계정 정보를 불러오는 중입니다…</p>
  if (isError || !profile)
    return (
      <div role="alert" className="space-y-3">
        <p>계정 정보를 불러오지 못했어요.</p>
        <Button onClick={() => refetch()}>다시 시도</Button>
      </div>
    )
  return (
    <div className="mx-auto flex w-full max-w-[640px] flex-col gap-6">
      <div className="rounded-input border border-danger bg-danger-soft px-5 py-4">
        <p className="text-small font-bold text-danger">탈퇴 전에 꼭 확인해 주세요</p>
        <ul className="mt-2 flex flex-col gap-1 text-caption text-text">
          <li>
            · 탈퇴 후 30일 동안은 같은 이메일로 계정을 복원할 수 있어요. 30일이 지나면 개인정보를
            완전히 지워 복원할 수 없어요(같은 이메일로 새로 가입은 가능).
          </li>
          <li>· 쓴 글·댓글은 남고 작성자는 「탈퇴한 사용자」로 보여요.</li>
          <li>· 참여 중인 채팅방·팀에서 나가요. 팀장인 채팅방이 있으면 먼저 정리해야 해요.</li>
        </ul>
      </div>
      <div className="flex flex-col">
        <label htmlFor="withdraw-confirm" className="mb-2 text-body font-bold text-text">
          아래 칸에 「탈퇴합니다」를 입력해 주세요
        </label>
        <input
          id="withdraw-confirm"
          value={confirmText}
          onChange={(e) => setConfirmText(e.target.value)}
          placeholder="탈퇴합니다"
          className="h-14 rounded-input border border-line-input bg-surface px-4 text-body focus:border-2 focus:border-primary focus:outline-none"
        />
      </div>
      {/* 서버와 동일하게 이메일 계정만 서비스 비밀번호를 확인한다. */}
      {!isSocial && (
        <PasswordInput
          id="withdraw-password"
          label="비밀번호 확인"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          errorText={error ?? undefined}
        />
      )}
      {isSocial && error && <p className="text-small text-danger">{error}</p>}
      <Checkbox
        label="안내 사항을 모두 확인했으며, 탈퇴에 동의합니다."
        checked={agree}
        onChange={(e) => setAgree(e.target.checked)}
      />
      <div className="flex flex-wrap items-center justify-end gap-3">
        <MobileActionBar>
          <Button variant="outline" onClick={() => navigate('/mypage/profile')}>
            취소
          </Button>
          <Button variant="danger" disabled={!ready} loading={saving} onClick={submit}>
            탈퇴 신청
          </Button>
        </MobileActionBar>
      </div>

      {/* Figma 20-2 팀장 탈퇴 불가 안내 */}
      <Modal
        open={leaderBlocked}
        size="md"
        title="지금은 탈퇴할 수 없어요"
        description="팀장인 채팅방을 먼저 정리해 주세요"
        onClose={() => setLeaderBlocked(false)}
        footer={
          <>
            <Button variant="outline" size="lg" onClick={() => setLeaderBlocked(false)}>
              닫기
            </Button>
            <Button variant="primary" size="lg" onClick={() => navigate('/workspaces')}>
              워크스페이스로 가기
            </Button>
          </>
        }
      />
      {/* ④ 탈퇴 완료 → 로그아웃되어 메인으로 */}
      <Modal
        open={done !== null}
        size="md"
        title="탈퇴 신청이 완료됐어요"
        description={`복원 기한: ${done ?? ''}. 기한 안에는 로그인 화면에서 계정을 복원할 수 있어요.`}
        onClose={() => {
          useAuthStore.getState().clear()
          navigate('/', { replace: true })
        }}
        footer={
          <Button
            variant="primary"
            size="lg"
            onClick={() => {
              useAuthStore.getState().clear()
              navigate('/', { replace: true })
            }}
          >
            확인
          </Button>
        }
      />
    </div>
  )
}
