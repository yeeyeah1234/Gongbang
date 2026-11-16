import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { getErrorMessage } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { AuthCard } from '../../features/auth/components/AuthCard'
import {
  PROVIDER_LABEL,
  exchangeOAuthCode,
  takeOAuthRedirect,
  type OAuthProvider,
} from '../../features/auth/oauth'
import { safeRedirect } from '../../features/auth/redirect'
import { loadLoginMember } from '../../features/auth/session'
import { useAuthStore } from '../../features/auth/store'

// AUTH-10 ③: 백엔드가 /oauth/callback?code=1회용키 로 돌려보내면 그 키를 로그인 토큰으로 바꾼다
export default function OAuthCallbackPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const code = params.get('code')
  const errorCode = params.get('error')
  const provider = (params.get('provider') ?? 'kakao') as OAuthProvider
  const label = PROVIDER_LABEL[provider] ?? '간편'
  const [failed, setFailed] = useState<string | null>(null)
  const started = useRef(false)

  useEffect(() => {
    if (!code || started.current) return
    started.current = true // 1회용 키라 한 번만 보낸다
    exchangeOAuthCode(code)
      .then(async (res) => {
        if (res.isNewMember && res.signupToken) {
          // ⑤ 처음이면 추가 정보 입력
          navigate('/signup/social', {
            replace: true,
            state: { signupToken: res.signupToken, loginType: res.loginType, email: res.email },
          })
          return
        }
        if (res.accessToken) {
          useAuthStore.getState().setLogin(res.accessToken)
          await loadLoginMember().catch(() => undefined)
          navigate(safeRedirect(takeOAuthRedirect()), { replace: true })
        }
      })
      .catch((e) =>
        setFailed(getErrorMessage(e, '간편 로그인을 완료하지 못했어요. 다시 시도해 주세요')),
      )
  }, [code, navigate])

  // ⑦⑧ 안내
  let message: string | null = failed
  if (!code) {
    if (errorCode === 'EMAIL_REGISTERED') message = '이미 이메일로 가입된 계정이에요'
    else if (errorCode === 'WITHDRAWN') message = '탈퇴 신청한 계정이에요'
    else message = `${label} 로그인을 완료하지 못했어요. 다시 시도해 주세요`
  }

  if (!message) {
    return (
      <AuthCard title="로그인 중이에요">
        <div className="h-14 animate-pulse rounded-input bg-surface-sub" />
      </AuthCard>
    )
  }

  return (
    <AuthCard title="로그인">
      <p className="rounded-input bg-danger-soft px-4 py-3 text-small text-danger" role="alert">
        {message}
      </p>
      <Link to="/login" className="mt-6 block">
        <Button variant="primary" size="lg" className="w-full">
          {errorCode === 'EMAIL_REGISTERED' ? '이메일로 로그인' : '로그인 화면으로'}
        </Button>
      </Link>
    </AuthCard>
  )
}
