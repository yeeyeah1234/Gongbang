import { MobileActionBar } from '../../components/layout/MobileActionBar'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { getApiError } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { Toast } from '../../components/ui/Toast'
import { useTimedToast } from '../../hooks/useTimedToast'
import { resetPassword } from '../../features/auth/api'
import { AuthCard } from '../../features/auth/components/AuthCard'
import { EmailCodeField } from '../../features/auth/components/EmailCodeField'
import { PasswordInput } from '../../features/auth/components/PasswordInput'
import { PasswordRules } from '../../features/auth/components/PasswordRules'
import { isPasswordValid } from '../../features/auth/passwordRules'

// AUTH-02 ②~⑥ 비밀번호 찾기 / Figma 03-1(이메일 인증) · 03-2(새 비밀번호)
export default function FindPasswordPage() {
  const navigate = useNavigate()
  const toast = useTimedToast()
  const [email, setEmail] = useState('')
  const [token, setToken] = useState<string | null>(null)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [guide, setGuide] = useState<{ code: string | null; message: string | null } | null>(null)
  const [loading, setLoading] = useState(false)

  const mismatch = confirm.length > 0 && password !== confirm

  const submit = async () => {
    if (!token) return
    setLoading(true)
    setError(null)
    try {
      await resetPassword(email.trim(), token, password)
      toast.show('비밀번호가 변경되었어요')
      window.setTimeout(() => navigate('/login'), 800)
    } catch (e) {
      const { code, message } = getApiError(e)
      if (code === 'TOKEN_EXPIRED') {
        // ③ 인증 후 10분이 지나면 처음부터 다시
        setToken(null)
        setGuide({ code, message })
      } else {
        setError(message)
      }
    } finally {
      setLoading(false)
    }
  }

  if (token) {
    return (
      <AuthCard title="새 비밀번호 설정" description="새로 사용할 비밀번호를 입력해 주세요.">
        <div className="flex flex-col gap-6">
          <div>
            <PasswordInput
              id="new-password"
              label="새 비밀번호"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <PasswordRules password={password} />
          </div>
          <PasswordInput
            id="new-password-confirm"
            label="새 비밀번호 확인"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            errorText={mismatch ? '비밀번호가 일치하지 않아요' : (error ?? undefined)}
            successText={confirm && !mismatch ? '비밀번호가 일치해요' : undefined}
          />
          <MobileActionBar>
            <Button
              variant="primary"
              size="lg"
              className="w-full"
              loading={loading}
              disabled={!isPasswordValid(password) || password !== confirm}
              onClick={submit}
            >
              비밀번호 변경
            </Button>
          </MobileActionBar>
        </div>
        <Toast message={toast.message} />
      </AuthCard>
    )
  }

  return (
    <AuthCard title="비밀번호 찾기" description="가입한 이메일로 인증번호를 보내드려요.">
      <div className="flex flex-col gap-6">
        <Input
          id="reset-email"
          label="이메일"
          type="email"
          placeholder="example@email.com"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value)
            setGuide(null)
          }}
        />
        <EmailCodeField
          key={email}
          email={email}
          purpose="RESET"
          onSendError={(code, message) => setGuide({ code, message })}
          onVerified={(t) => setToken(t)}
        />
        {guide && (
          <div className="rounded-input bg-bg px-4 py-3 text-small text-text" role="alert">
            <p>{guide.message}</p>
            {/* ⑥ 가입 안 됨 / 탈퇴 신청 계정 안내 */}
            {guide.code === 'EMAIL_NOT_FOUND' && (
              <Link to="/signup" className="mt-2 inline-block font-bold text-primary">
                회원가입
              </Link>
            )}
            {guide.code === 'ACCOUNT_WITHDRAWN' && (
              <Link
                to={`/restore?email=${encodeURIComponent(email.trim())}`}
                className="mt-2 inline-block font-bold text-primary"
              >
                계정 복원하기
              </Link>
            )}
          </div>
        )}
        {/* ③ 이메일을 더 쓸 수 없으면 운영자 메일 안내 — TODO(확인 필요): 운영자 메일 주소 */}
        <p className="text-caption text-text-sub">
          이메일을 더 쓸 수 없나요? [문의하기]로 운영자에게 알려 주세요.
        </p>
      </div>
    </AuthCard>
  )
}
