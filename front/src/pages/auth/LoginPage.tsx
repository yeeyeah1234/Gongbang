import { MobileActionBar } from '../../components/layout/MobileActionBar'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { getApiError } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Input } from '../../components/ui/Input'
import { Toast } from '../../components/ui/Toast'
import { useTimedToast } from '../../hooks/useTimedToast'
import { login } from '../../features/auth/api'
import { AuthCard } from '../../features/auth/components/AuthCard'
import { EmailCodeField } from '../../features/auth/components/EmailCodeField'
import { PasswordInput } from '../../features/auth/components/PasswordInput'
import { SocialLoginButtons } from '../../features/auth/components/SocialLoginButtons'
import { WithdrawnAccountModal } from '../../features/auth/components/WithdrawnAccountModal'
import { safeRedirect } from '../../features/auth/redirect'
import { loadLoginMember } from '../../features/auth/session'
import { useAuthStore } from '../../features/auth/store'

// AUTH-01 이메일 로그인 / Figma 01-1·01-2
export default function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const redirectTo = safeRedirect(params.get('redirect'))
  const toast = useTimedToast()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(
    params.get('reason') === 'suspended'
      ? '이용 정지된 계정입니다. 정지가 해제된 뒤 로그인해 주세요.'
      : null,
  )
  const [failCount, setFailCount] = useState(0)
  const [notRegistered, setNotRegistered] = useState(false)
  // ③ 5번 틀리면 추가 인증 (Figma 01-2)
  const [extraRequired, setExtraRequired] = useState(false)
  const [extraToken, setExtraToken] = useState<string | null>(null)
  const [locked, setLocked] = useState<string | null>(null)
  const [withdrawnMessage, setWithdrawnMessage] = useState<string | null>(null)

  const loginRequired = (location.state as { loginRequired?: boolean } | null)?.loginRequired

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!email.trim() || !password) return
    setLoading(true)
    setError(null)
    setNotRegistered(false)
    try {
      const res = await login({
        email: email.trim(),
        password,
        rememberMe,
        extraVerificationToken: extraToken ?? undefined,
      })
      useAuthStore.getState().setLogin(res.accessToken, res.member)
      await loadLoginMember().catch(() => undefined)
      // ⑥ 로그인이 필요한 화면에서 왔으면 그 화면으로
      navigate(redirectTo, { replace: true })
    } catch (e) {
      const { code, message } = getApiError(e)
      switch (code) {
        case 'LOGIN_FAILED': {
          setError(message)
          const match = message?.match(/(\d)번\)/)
          setFailCount(match ? Number(match[1]) : failCount + 1)
          break
        }
        case 'EMAIL_NOT_FOUND':
          setNotRegistered(true)
          setError(message ?? '가입되지 않은 이메일이에요')
          break
        case 'EXTRA_VERIFICATION_REQUIRED':
          setExtraRequired(true)
          setExtraToken(null)
          setError(null)
          break
        case 'ACCOUNT_SUSPENDED':
          setError(message ?? '이용 정지된 계정입니다. 정지가 해제된 뒤 로그인해 주세요.')
          break
        case 'ACCOUNT_LOCKED':
          setLocked(message)
          break
        case 'ACCOUNT_WITHDRAWN':
          setWithdrawnMessage(message)
          break
        default:
          toast.show(message ?? '로그인하지 못했어요. 잠시 후 다시 시도해 주세요')
      }
    } finally {
      setLoading(false)
    }
  }

  // ③ 3번째부터 [비밀번호 찾기] 강조
  const emphasizeFind = failCount >= 3

  return (
    <AuthCard
      title="로그인"
      description={extraRequired ? undefined : '함께 도전할 팀원을 찾아보세요.'}
    >
      {loginRequired && !extraRequired && (
        <p className="mb-6 rounded-input bg-primary-soft px-4 py-3 text-small font-bold text-primary">
          로그인이 필요한 서비스입니다
        </p>
      )}
      {extraRequired && (
        <div className="mb-6 rounded-input border border-danger bg-danger-soft px-4 py-3">
          <p className="text-small font-bold text-danger">비밀번호를 5회 이상 잘못 입력했어요</p>
          <p className="mt-1 text-caption text-text-sub">
            로그인을 5번 실패했어요. 가입한 이메일로 보낸 인증번호를 입력해 주세요
          </p>
        </div>
      )}
      {locked && (
        <div
          className="mb-6 rounded-input border border-danger bg-danger-soft px-4 py-3 text-small text-danger"
          role="alert"
        >
          {locked}
        </div>
      )}

      <form onSubmit={handleSubmit} className="flex flex-col gap-6" noValidate>
        <Input
          id="login-email"
          label="이메일"
          type="email"
          autoComplete="email"
          placeholder="example@email.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={extraRequired && extraToken === null}
        />
        <PasswordInput
          id="login-password"
          label="비밀번호"
          autoComplete="current-password"
          placeholder="비밀번호를 입력해 주세요"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          errorText={error ?? undefined}
        />

        {notRegistered && (
          <Link to="/signup" className="-mt-4 text-small font-bold text-primary">
            회원가입 하러 가기
          </Link>
        )}

        {extraRequired && (
          <div>
            <p className="mb-2 text-body font-bold text-text">이메일 인증번호</p>
            <EmailCodeField
              email={email}
              purpose="LOGIN"
              autoSend
              verified={extraToken !== null}
              onVerified={(token) => setExtraToken(token)}
            />
          </div>
        )}

        {!extraRequired && (
          <div className="flex items-center justify-between">
            <Checkbox
              label="로그인 유지"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
            />
            <div className="flex items-center gap-3 text-caption text-text-sub">
              <Link to="/find-email" className="hover:text-text">
                이메일 찾기
              </Link>
              <span aria-hidden="true" className="h-3 w-px bg-line" />
              <Link
                to="/find-password"
                className={emphasizeFind ? 'font-bold text-danger' : 'hover:text-text'}
              >
                비밀번호 찾기
              </Link>
            </div>
          </div>
        )}

        <MobileActionBar>
          <Button
            type="submit"
            variant="primary"
            size="lg"
            className="w-full"
            loading={loading}
            disabled={extraRequired && extraToken === null}
          >
            {extraRequired ? '인증하고 로그인' : '로그인'}
          </Button>
        </MobileActionBar>
      </form>

      {extraRequired ? (
        <div className="mt-6 flex justify-center gap-4 text-small text-text-sub">
          <Link to="/find-email">이메일 찾기</Link>
          <Link to="/find-password">비밀번호 찾기</Link>
        </div>
      ) : (
        <>
          <div className="my-6 flex items-center gap-4 text-caption text-text-sub">
            <span className="h-px flex-1 bg-divider" />
            또는
            <span className="h-px flex-1 bg-divider" />
          </div>
          {/* AUTH-10 카카오·구글 (11/17 정진우: 구글도 함. 기능정의서 AUTH-10 문구 수정 필요) */}
          <SocialLoginButtons redirectTo={redirectTo} onUnavailable={toast.show} />
          <p className="mt-6 text-center text-small text-text-sub">
            아직 회원이 아니신가요?{' '}
            <Link
              to={`/signup${params.get('redirect') ? `?redirect=${encodeURIComponent(redirectTo)}` : ''}`}
              className="font-bold text-text underline"
            >
              회원가입
            </Link>
          </p>
        </>
      )}

      <WithdrawnAccountModal
        open={withdrawnMessage !== null}
        email={email.trim()}
        message={withdrawnMessage}
        onClose={() => setWithdrawnMessage(null)}
      />
      <Toast message={toast.message} />
    </AuthCard>
  )
}
