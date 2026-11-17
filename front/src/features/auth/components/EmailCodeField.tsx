import { useEffect, useState } from 'react'
import { Button } from '../../../components/ui/Button'
import { getApiError } from '../../../api/errors'
import { sendCode, verifyCode } from '../api'
import { isEmailFormatValid } from '../passwordRules'
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
  const [devCode, setDevCode] = useState<string | null>(null)

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
      // 로컬 개발(메일 서버 없음)에서는 서버가 준 번호를 미리 채워 준다
      setDevCode(res.devCode ?? null)
      setCode(res.devCode ?? '')
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

  // Figma 03-1·05: [인증번호 받기] 버튼과 인증번호 칸은 처음부터 보이고, 받기 전에는 칸만 잠겨 있다
  const emailOk = isEmailFormatValid(email)

  return (
    <div className="flex flex-col">
      <Button
        variant="outline"
        size="lg"
        className="w-full text-body"
        onClick={send}
        loading={sending}
        disabled={!emailOk || (sent && resendWait > 0)}
      >
        {!sent
          ? '인증번호 받기'
          : resendWait > 0
            ? `재발송 (${resendWait}초 뒤)`
            : '인증번호 재발송'}
      </Button>
      {!emailOk && email.trim() && (
        <p className="mt-2 text-small text-text-sub">이메일 주소를 끝까지 입력해 주세요</p>
      )}

      <label
        htmlFor={`code-${purpose}`}
        className="mt-6 mb-2 flex items-center justify-between text-body font-bold text-text"
      >
        인증번호
        {sent && (
          <span className="text-small font-bold text-danger" aria-live="polite">
            남은 시간 {mmss(remaining)}
          </span>
        )}
      </label>
      <input
        id={`code-${purpose}`}
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        value={code}
        disabled={!sent}
        onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
        placeholder={sent ? '6자리 숫자' : '[인증번호 받기]를 먼저 눌러 주세요'}
        className={[
          'h-14 w-full rounded-input bg-surface px-4 text-body tracking-[0.3em] placeholder:tracking-normal placeholder:text-text-disabled focus:outline-none disabled:border disabled:border-line disabled:bg-surface-sub',
          error
            ? 'border-2 border-danger'
            : 'border border-line-input focus:border-2 focus:border-primary',
        ].join(' ')}
      />
      {devCode && (
        <p className="mt-2 text-small text-text-sub" role="status">
          개발 환경이라 메일 대신 인증번호를 보여 드려요: <b className="text-text">{devCode}</b>
        </p>
      )}
      {error && <p className="mt-2 text-small text-danger">{error}</p>}
      <Button
        variant="outline"
        size="lg"
        className="mt-3 w-full text-body"
        onClick={verify}
        loading={verifying}
        disabled={!sent || code.length !== 6}
      >
        인증하기
      </Button>
      {sent && (
        <>
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
    </div>
  )
}
