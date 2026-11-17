import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { getApiError } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Chip } from '../../components/ui/Chip'
import { Input } from '../../components/ui/Input'
import { Toast } from '../../components/ui/Toast'
import { useTimedToast } from '../../hooks/useTimedToast'
import { checkNickname } from '../../features/auth/api'
import { AuthCard } from '../../features/auth/components/AuthCard'
import { EmailCodeField } from '../../features/auth/components/EmailCodeField'
import { TERMS_VERSION, TermsModal } from '../../features/auth/components/TermsModal'
import { oauthSignup, takeOAuthRedirect } from '../../features/auth/oauth'
import { isNicknameFormatValid } from '../../features/auth/passwordRules'
import { safeRedirect } from '../../features/auth/redirect'
import { loadLoginMember } from '../../features/auth/session'
import { useAuthStore } from '../../features/auth/store'
import type { TermsType } from '../../features/auth/types'
import { useCategories } from '../../features/contest/hooks'

const TERMS_ROWS: { type: TermsType; label: string }[] = [
  { type: 'SERVICE', label: '[필수] 서비스 이용약관 동의' },
  { type: 'PRIVACY', label: '[필수] 개인정보 수집·이용 동의' },
  { type: 'MARKETING', label: '[선택] 마케팅 정보 수신 동의' },
]

interface SocialSignupState {
  signupToken: string
  loginType: 'KAKAO' | 'GOOGLE' | null
  email: string | null
}

// AUTH-10 ⑤⑥: 처음 온 카카오·구글 계정의 추가 정보 입력 (필수 약관 + 닉네임 중복 확인 + 관심 분야)
// 이력·나머지 프로필은 가입 후 마이페이지에서 입력한다
export default function SocialSignupPage() {
  const navigate = useNavigate()
  const state = useLocation().state as SocialSignupState | null
  const toast = useTimedToast()
  const categories = useCategories()

  const [agreed, setAgreed] = useState<Record<TermsType, boolean>>({
    SERVICE: false,
    PRIVACY: false,
    MARKETING: false,
  })
  const [termsOpen, setTermsOpen] = useState<TermsType | null>(null)
  const [nickname, setNickname] = useState('')
  const [nicknameChecked, setNicknameChecked] = useState<string | null>(null)
  const [nicknameError, setNicknameError] = useState<string | null>(null)
  const [interests, setInterests] = useState<string[]>([])
  // ⑥ 제공자에게서 이메일을 받지 못했을 때만 직접 입력·인증
  const [email, setEmail] = useState('')
  const [verificationToken, setVerificationToken] = useState<string | null>(null)
  const [emailError, setEmailError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!state?.signupToken) return <Navigate to="/login" replace />

  const needEmail = !state.email
  const providerName = state.loginType === 'GOOGLE' ? '구글' : '카카오'
  const nicknameOk = nicknameChecked !== null && nicknameChecked === nickname.trim()
  const ready =
    agreed.SERVICE &&
    agreed.PRIVACY &&
    nicknameOk &&
    interests.length > 0 &&
    (!needEmail || verificationToken !== null)

  const handleCheckNickname = async () => {
    const value = nickname.trim()
    if (!isNicknameFormatValid(value)) {
      setNicknameError('닉네임은 2~10자 한글·영문·숫자로 입력해 주세요')
      return
    }
    try {
      if (await checkNickname(value)) {
        setNicknameChecked(value)
        setNicknameError(null)
      } else setNicknameError('이미 쓰고 있는 닉네임이에요')
    } catch (e) {
      setNicknameError(getApiError(e).message ?? '닉네임을 확인하지 못했어요')
    }
  }

  const submit = async () => {
    setSubmitting(true)
    try {
      const res = await oauthSignup({
        signupToken: state.signupToken,
        nickname: nickname.trim(),
        terms: TERMS_ROWS.map((row) => ({
          type: row.type,
          version: TERMS_VERSION,
          agreed: agreed[row.type],
        })),
        interestCategoryCodes: interests,
        ...(needEmail && verificationToken ? { email: email.trim(), verificationToken } : {}),
      })
      useAuthStore.getState().setLogin(res.accessToken)
      await loadLoginMember().catch(() => undefined)
      navigate(safeRedirect(takeOAuthRedirect()), { replace: true })
    } catch (e) {
      const { code, message } = getApiError(e)
      if (code === 'NICKNAME_DUPLICATED') setNicknameChecked(null)
      toast.show(message ?? '가입하지 못했어요. 잠시 후 다시 시도해 주세요')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCard
      title="추가 정보 입력"
      description={`${providerName} 계정으로 처음 오셨어요. 몇 가지만 더 입력하면 가입이 끝나요.`}
    >
      <div className="flex flex-col gap-6">
        <ul className="flex flex-col gap-3 rounded-input bg-surface-sub px-4 py-4">
          {TERMS_ROWS.map((row) => (
            <li key={row.type} className="flex items-center justify-between">
              <Checkbox
                label={row.label}
                checked={agreed[row.type]}
                onChange={(e) => setAgreed({ ...agreed, [row.type]: e.target.checked })}
              />
              <button
                type="button"
                onClick={() => setTermsOpen(row.type)}
                className="cursor-pointer text-caption text-text-sub underline"
              >
                보기
              </button>
            </li>
          ))}
        </ul>

        {needEmail ? (
          <div className="flex flex-col gap-4">
            <Input
              id="social-email"
              label="이메일"
              type="email"
              placeholder="example@email.com"
              value={email}
              disabled={verificationToken !== null}
              onChange={(e) => {
                setEmail(e.target.value)
                setEmailError(null)
              }}
              helperText={`${providerName}에서 이메일을 받지 못했어요. 직접 입력하고 인증해 주세요.`}
              errorText={emailError ?? undefined}
            />
            <EmailCodeField
              key={email}
              email={email}
              purpose="SIGNUP"
              verified={verificationToken !== null}
              onSendError={(_, message) => setEmailError(message)}
              onVerified={setVerificationToken}
            />
          </div>
        ) : (
          <Input id="social-email-fixed" label="이메일" value={state.email ?? ''} disabled />
        )}

        <div className="flex items-end gap-3">
          <div className="flex-1">
            <Input
              id="social-nickname"
              label="닉네임"
              placeholder="2~10자 한글·영문·숫자"
              maxLength={10}
              value={nickname}
              onChange={(e) => {
                setNickname(e.target.value)
                setNicknameError(null)
              }}
              errorText={nicknameError ?? undefined}
              successText={nicknameOk ? '사용할 수 있는 닉네임이에요' : undefined}
            />
          </div>
          <Button
            variant="outline"
            size="lg"
            className="h-14 text-body"
            onClick={handleCheckNickname}
            disabled={!nickname.trim()}
          >
            중복 확인
          </Button>
        </div>

        <fieldset>
          <legend className="mb-3 text-body font-bold text-text">
            관심 분야 <span className="font-normal text-text-sub">(필수)</span>
          </legend>
          <div className="flex flex-wrap gap-2">
            {categories.data?.map((c) => (
              <Chip
                key={c.categoryCode}
                selected={interests.includes(c.categoryCode)}
                onClick={() =>
                  setInterests(
                    interests.includes(c.categoryCode)
                      ? interests.filter((v) => v !== c.categoryCode)
                      : [...interests, c.categoryCode],
                  )
                }
              >
                {c.name}
              </Chip>
            ))}
          </div>
        </fieldset>

        <Button
          variant="primary"
          size="lg"
          className="w-full"
          disabled={!ready}
          loading={submitting}
          onClick={submit}
        >
          가입 완료
        </Button>
      </div>

      <TermsModal
        type={termsOpen}
        onClose={() => setTermsOpen(null)}
        onAgree={(type) => {
          setAgreed({ ...agreed, [type]: true })
          setTermsOpen(null)
        }}
      />
      <Toast message={toast.message} />
    </AuthCard>
  )
}
