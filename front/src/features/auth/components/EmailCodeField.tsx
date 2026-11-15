import { useEffect, useState } from 'react'
import { Button } from '../../../components/ui/Button'
import { getApiError } from '../../../api/errors'
import { sendCode, verifyCode } from '../api'
import type { VerificationPurpose } from '../types'

interface EmailCodeFieldProps {
  email: string
  purpose: VerificationPurpose
  // 인증번호 받기 전 이메일 확인 결과(가입된 이메일·탈퇴 계정 등)는 부모가 처리한다
  onSendError?: (code: string | null, message: string | null) => void
  onVerified: (verificationToken: string) => void
  autoSend?: boolean
  verified?: boolean
  // AUTH-05 ⑦ [새로 가입하기]를 고른 탈퇴 이메일
  newSignup?: boolean
}

function mmss(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

// AUTH-05 ①~④: [인증번호 받기] → 6자리 입력, 3분 타이머, [재발송] 30초 뒤, [메일이 안 와요] 안내
export function EmailCodeField({
  email,
  purpose,
  onSendError,
  onVerified,
  autoSend = false,
  verified = false,
  newSignup = false,
}: EmailCodeFieldProps) {
  const [sent, setSent] = useState(false)
  const [expiresAt, setExpiresAt] = useState<number | null>(null)
  const [resendAt, setResendAt] = useState<number | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [helpOpen, setHelpOpen] = useState(false)

  useEffect(() => {
    if (!sent || verified) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [sent, verified])

  const remaining = expiresAt ? Math.max(0, Math.ceil((expiresAt - now) / 1000)) : 0
  const resendWait = resendAt ? Math.max(0, Math.ceil((resendAt - now) / 1000)) : 0

  const send = async () => {
    setSending(true)
    setError(null)
    try {
      const res = await sendCode(email.trim(), purpose, newSignup)
      setSent(true)
      setCode('')
      setNow(Date.now())
      setExpiresAt(new Date(res.expiresAt).getTime())
      setResendAt(new Date(res.resendAvailableAt).getTime())
    } catch (e) {
      const { code: errCode, message } = getApiError(e)
      if (onSendError && errCode !== 'TOO_MANY_REQUESTS' && errCode !== 'MAIL_SEND_FAILED') {
        onSendError(errCode, message)
      } else {
        setError(message ?? '메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요')
      }
    } finally {
      setSending(false)
    }
  }

  useEffect(() => {
    if (!autoSend) return
    // 화면이 뜬 다음 한 번 보낸다 (StrictMode에서 두 번 보내지 않게 정리 함수로 취소)
    const timer = window.setTimeout(() => void send(), 0)
    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 처음 한 번만 보낸다
  }, [])

  const verify = async () => {
    if (remaining === 0) {
      setError('인증 시간이 지났어요. 인증번호를 다시 받아 주세요')
      return
    }
    setVerifying(true)
    setError(null)
    try {
      const token = await verifyCode(email.trim(), purpose, code)
      onVerified(token)
    } catch (e) {
      setError(getApiError(e).message ?? '인증번호가 맞지 않아요')
    } finally {
      setVerifying(false)
    }
  }

  if (verified) {
    return <p className="mt-2 text-small text-success">인증되었어요</p>
  }

  return (
    <div className="flex flex-col">
      {!sent ? (
        <Button
          variant="outline"
          onClick={send}
          loading={sending}
          disabled={!email.trim()}
          className="self-start"
        >
          인증번호 받기
        </Button>
      ) : (
        <>
          <label
            htmlFor={`code-${purpose}`}
            className="mb-2 flex items-center justify-between text-body font-bold text-text"
          >
            인증번호
            <span className="text-small font-bold text-danger" aria-live="polite">
              {mmss(remaining)}
            </span>
          </label>
          <div className="flex gap-3">
            <input
              id={`code-${purpose}`}
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              placeholder="6자리 숫자"
              className={[
                'h-14 min-w-0 flex-1 rounded-input bg-surface px-4 text-body tracking-[0.3em] placeholder:tracking-normal placeholder:text-text-disabled focus:outline-none',
                error
                  ? 'border-2 border-danger'
                  : 'border border-line-input focus:border-2 focus:border-primary',
              ].join(' ')}
            />
            <Button
              variant="outline"
              size="lg"
              onClick={send}
              disabled={resendWait > 0}
              loading={sending}
              className="h-14 text-body"
            >
              {resendWait > 0 ? `재발송 ${resendWait}초` : '재발송'}
            </Button>
          </div>
          {error && <p className="mt-2 text-small text-danger">{error}</p>}
          <Button
            variant="primary"
            className="mt-4 w-full"
            onClick={verify}
            loading={verifying}
            disabled={code.length !== 6}
          >
            인증하기
          </Button>
          <button
            type="button"
            onClick={() => setHelpOpen((v) => !v)}
            className="mt-3 cursor-pointer self-start text-small text-text-sub underline"
          >
            메일이 안 와요
          </button>
          {helpOpen && (
            <div className="mt-2 rounded-input bg-bg px-4 py-3 text-small text-text-sub">
              <p>· 스팸함(정크 메일함)을 확인해 주세요.</p>
              <p>· 이메일 주소에 오타가 없는지 확인해 주세요.</p>
              <p>· 30초가 지나면 [재발송]으로 다시 받을 수 있어요.</p>
            </div>
          )}
        </>
      )}
      {!sent && error && <p className="mt-2 text-small text-danger">{error}</p>}
    </div>
  )
}
