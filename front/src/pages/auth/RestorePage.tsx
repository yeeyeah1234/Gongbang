import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { getApiError } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { restoreAccount } from '../../features/auth/api'
import { AuthCard } from '../../features/auth/components/AuthCard'
import { EmailCodeField } from '../../features/auth/components/EmailCodeField'
import { loadLoginMember } from '../../features/auth/session'
import { useAuthStore } from '../../features/auth/store'

// MY-11 ⑥ 계정 복원: 이메일 인증번호 확인 → 프로필·이력·북마크가 그대로 돌아온다 (Figma 05 계정 복원 · 이메일 인증)
export default function RestorePage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState(params.get('email') ?? '')
  const [token, setToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const restore = async () => {
    if (!token) return
    setLoading(true)
    setError(null)
    try {
      const accessToken = await restoreAccount(email.trim(), token)
      useAuthStore.getState().setLogin(accessToken)
      await loadLoginMember().catch(() => undefined)
      navigate('/', { replace: true })
    } catch (e) {
      setError(getApiError(e).message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthCard
      title="계정 복원"
      description="가입한 이메일로 인증하면 프로필·이력·북마크가 그대로 돌아와요. 탈퇴 때 나간 채팅방·팀은 돌아오지 않아요."
    >
      <div className="flex flex-col gap-6">
        <Input
          id="restore-email"
          label="이메일"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={token !== null}
        />
        <EmailCodeField
          key={email}
          email={email}
          purpose="RESTORE"
          verified={token !== null}
          onSendError={(_, m) => setError(m)}
          onVerified={setToken}
        />
        {error && <p className="text-small text-danger">{error}</p>}
        <Button
          variant="primary"
          size="lg"
          className="w-full"
          disabled={!token}
          loading={loading}
          onClick={restore}
        >
          계정 복원하기
        </Button>
      </div>
    </AuthCard>
  )
}
