import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { getApiError } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Chip } from '../../components/ui/Chip'
import { Input } from '../../components/ui/Input'
import { Toast } from '../../components/ui/Toast'
import { useTimedToast } from '../../hooks/useTimedToast'
import { useCategories } from '../../features/contest/hooks'
import { checkNickname, signup } from '../../features/auth/api'
import { AuthCard } from '../../features/auth/components/AuthCard'
import { EmailCodeField } from '../../features/auth/components/EmailCodeField'
import { PasswordInput } from '../../features/auth/components/PasswordInput'
import { PasswordRules } from '../../features/auth/components/PasswordRules'
import { StepIndicator } from '../../features/auth/components/StepIndicator'
import { TERMS_VERSION, TermsModal } from '../../features/auth/components/TermsModal'
import { WithdrawnAccountModal } from '../../features/auth/components/WithdrawnAccountModal'
import { isNicknameFormatValid, isPasswordValid } from '../../features/auth/passwordRules'
import { safeRedirect } from '../../features/auth/redirect'
import { loadLoginMember } from '../../features/auth/session'
import { useAuthStore } from '../../features/auth/store'
import type { TermsType } from '../../features/auth/types'
import { BIO_MAX, DESIRED_ROLES, GRADES, roleLabel } from '../../features/member/constants'

type Agreements = Record<TermsType, boolean>

// Figma 04 약관 항목 (AUTH-04 ①: 필수 2개 + 선택 마케팅)
const TERMS_ROWS: { type: TermsType; label: string }[] = [
  { type: 'SERVICE', label: '[필수] 서비스 이용약관 동의' },
  { type: 'PRIVACY', label: '[필수] 개인정보 수집·이용 동의' },
  { type: 'MARKETING', label: '[선택] 마케팅 정보 수신 동의' },
]

// AUTH-04~07 회원가입 4단계 / Figma 04·05·06·07-1·07-2·13·20-1
export default function SignupPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const redirectTo = safeRedirect(params.get('redirect'))
  const toast = useTimedToast()
  const categories = useCategories()

  const [step, setStep] = useState(1)
  // 1 약관
  const [agreed, setAgreed] = useState<Agreements>({
    SERVICE: false,
    PRIVACY: false,
    MARKETING: false,
  })
  const [termsOpen, setTermsOpen] = useState<TermsType | null>(null)
  // 2 이메일 인증
  const [email, setEmail] = useState('')
  const [verificationToken, setVerificationToken] = useState<string | null>(null)
  const [emailGuide, setEmailGuide] = useState<{
    code: string | null
    message: string | null
  } | null>(null)
  const [newSignupConfirmed, setNewSignupConfirmed] = useState(false)
  // 3 계정 정보
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [nickname, setNickname] = useState('')
  const [nicknameChecked, setNicknameChecked] = useState<string | null>(null)
  const [nicknameError, setNicknameError] = useState<string | null>(null)
  const [checking, setChecking] = useState(false)
  // 4 프로필
  const [interests, setInterests] = useState<string[]>([])
  const [roles, setRoles] = useState<string[]>([])
  const [school, setSchool] = useState('')
  const [major, setMajor] = useState('')
  const [grade, setGrade] = useState('')
  const [bio, setBio] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  const requiredAgreed = agreed.SERVICE && agreed.PRIVACY
  const allAgreed = requiredAgreed && agreed.MARKETING
  const passwordMismatch = confirm.length > 0 && password !== confirm
  const nicknameOk = nicknameChecked !== null && nicknameChecked === nickname.trim()
  const step3Ready = isPasswordValid(password) && password === confirm && nicknameOk

  const toggle = (list: string[], value: string) =>
    list.includes(value) ? list.filter((v) => v !== value) : [...list, value]

  const handleCheckNickname = async () => {
    const value = nickname.trim()
    if (!isNicknameFormatValid(value)) {
      setNicknameError('닉네임은 2~10자 한글·영문·숫자로 입력해 주세요')
      return
    }
    setChecking(true)
    setNicknameError(null)
    try {
      const available = await checkNickname(value)
      if (available) setNicknameChecked(value)
      else setNicknameError('이미 쓰고 있는 닉네임이에요')
    } catch (e) {
      setNicknameError(getApiError(e).message ?? '닉네임을 확인하지 못했어요')
    } finally {
      setChecking(false)
    }
  }

  const submit = async (skipOptional: boolean) => {
    if (!verificationToken || interests.length === 0) return
    setSubmitting(true)
    try {
      const res = await signup({
        email: email.trim(),
        verificationToken,
        password,
        nickname: nickname.trim(),
        terms: TERMS_ROWS.map((row) => ({
          type: row.type,
          version: TERMS_VERSION,
          agreed: agreed[row.type],
        })),
        interestCategoryCodes: interests,
        ...(skipOptional
          ? {}
          : {
              desiredRoles: roles,
              school: school.trim() || undefined,
              major: major.trim() || undefined,
              grade: grade || undefined,
              bio: bio.trim() || undefined,
            }),
      })
      // ⑤ 바로 로그인된 상태
      useAuthStore.getState().setLogin(res.accessToken)
      await loadLoginMember().catch(() => undefined)
      setDone(true)
    } catch (e) {
      const { code, message } = getApiError(e)
      if (code === 'TOKEN_EXPIRED') {
        setVerificationToken(null)
        setStep(2)
      }
      if (code === 'NICKNAME_DUPLICATED') {
        setNicknameChecked(null)
        setStep(3)
      }
      toast.show(message ?? '가입하지 못했어요. 잠시 후 다시 시도해 주세요')
    } finally {
      setSubmitting(false)
    }
  }

  // Figma 07-2 가입 완료
  if (done) {
    const interestNames = interests
      .map((code) => categories.data?.find((c) => c.categoryCode === code)?.name ?? code)
      .join(', ')
    return (
      <AuthCard title="">
        <div className="flex flex-col items-center text-center">
          <span
            className="flex size-16 items-center justify-center rounded-full bg-primary text-title font-bold text-surface"
            aria-hidden="true"
          >
            ✓
          </span>
          <h1 className="mt-6 text-title font-bold text-text">{nickname.trim()} 님, 환영해요!</h1>
          <p className="mt-2 text-small text-text-sub">
            가입이 완료되었어요. 관심 분야에 맞는 공모전과 팀원을 찾아보세요.
          </p>
          <dl className="mt-6 grid w-full grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-input bg-bg px-5 py-4 text-left text-small">
            <dt className="text-text-sub">관심 분야</dt>
            <dd className="font-bold text-text">{interestNames}</dd>
            <dt className="text-text-sub">희망 역할</dt>
            <dd className="font-bold text-text">
              {roles.length ? roles.map(roleLabel).join(', ') : '-'}
            </dd>
          </dl>
          <Button
            variant="primary"
            size="lg"
            className="mt-8 w-full"
            onClick={() => navigate(redirectTo, { replace: true })}
          >
            {redirectTo === '/' ? '추천 공모전 보러 가기' : '보던 화면으로 돌아가기'}
          </Button>
          <Button
            variant="outline"
            size="lg"
            className="mt-3 w-full"
            onClick={() => navigate('/mypage/profile', { replace: true })}
          >
            프로필 마저 채우기
          </Button>
        </div>
      </AuthCard>
    )
  }

  return (
    <AuthCard title={step === 2 ? '이메일 인증' : '회원가입'} wide={step === 4}>
      <StepIndicator current={step} />

      {step === 1 && (
        <div className="mt-8">
          <div className="rounded-input bg-surface-sub px-4 py-4">
            <Checkbox
              label={<span className="text-body font-bold">약관 전체 동의</span>}
              checked={allAgreed}
              onChange={(e) =>
                setAgreed({
                  SERVICE: e.target.checked,
                  PRIVACY: e.target.checked,
                  MARKETING: e.target.checked,
                })
              }
            />
          </div>
          <ul className="mt-4 flex flex-col gap-3 px-4">
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
          <Button
            variant="primary"
            size="lg"
            className="mt-8 w-full"
            disabled={!requiredAgreed}
            onClick={() => setStep(2)}
          >
            다음
          </Button>
        </div>
      )}

      {step === 2 && (
        <div className="mt-8 flex flex-col gap-6">
          <Input
            id="signup-email"
            label="이메일"
            type="email"
            placeholder="example@email.com"
            value={email}
            onChange={(e) => {
              // ⑤ 인증 뒤 이메일을 고치면 다시 인증
              setEmail(e.target.value)
              setVerificationToken(null)
              setEmailGuide(null)
            }}
          />
          <EmailCodeField
            key={`${email}-${newSignupConfirmed}`}
            email={email}
            purpose="SIGNUP"
            newSignup={newSignupConfirmed}
            verified={verificationToken !== null}
            onSendError={(code, message) => setEmailGuide({ code, message })}
            onVerified={setVerificationToken}
          />
          {emailGuide && emailGuide.code !== 'ACCOUNT_WITHDRAWN' && (
            <div className="rounded-input bg-bg px-4 py-3 text-small" role="alert">
              <p className="text-danger">{emailGuide.message}</p>
              {emailGuide.code === 'SOCIAL_EMAIL_REGISTERED' && (
                <div className="mt-2 flex gap-4 font-bold text-primary">
                  <Link to="/login">간편 로그인하러 가기</Link>
                </div>
              )}
              {emailGuide.code === 'EMAIL_DUPLICATED' && (
                <div className="mt-2 flex gap-4 font-bold text-primary">
                  <Link to="/login">로그인</Link>
                  <Link to="/find-password">이메일·비밀번호 찾기</Link>
                </div>
              )}
            </div>
          )}
          <div className="flex gap-3">
            <Button variant="outline" size="lg" onClick={() => setStep(1)}>
              이전
            </Button>
            <Button
              variant="primary"
              size="lg"
              className="flex-1"
              disabled={!verificationToken}
              onClick={() => setStep(3)}
            >
              다음
            </Button>
          </div>
        </div>
      )}

      {step === 3 && (
        <div className="mt-8 flex flex-col gap-6">
          <Input id="signup-email-readonly" label="이메일" value={email} disabled />
          <div>
            <PasswordInput
              id="signup-password"
              label="비밀번호"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <PasswordRules password={password} />
          </div>
          <PasswordInput
            id="signup-password-confirm"
            label="비밀번호 확인"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            errorText={passwordMismatch ? '비밀번호가 일치하지 않아요' : undefined}
            successText={confirm && !passwordMismatch ? '비밀번호가 일치해요' : undefined}
          />
          <div>
            {/* 라벨은 따로 두고 버튼을 입력칸 윗선에 맞춘다 → 아래 안내 문구가 생겨도 버튼이 움직이지 않는다 */}
            <label htmlFor="signup-nickname" className="mb-2 block text-body font-bold text-text">
              닉네임
            </label>
            <div className="flex items-start gap-3">
              <div className="flex-1">
                <Input
                  id="signup-nickname"
                  placeholder="2~10자 한글·영문·숫자"
                  value={nickname}
                  maxLength={10}
                  onChange={(e) => {
                    // ③ 확인 뒤 닉네임을 고치면 다시 확인
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
                className="h-14 shrink-0 text-body"
                loading={checking}
                onClick={handleCheckNickname}
                disabled={!nickname.trim()}
              >
                중복 확인
              </Button>
            </div>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" size="lg" onClick={() => setStep(2)}>
              이전
            </Button>
            <Button
              variant="primary"
              size="lg"
              className="flex-1"
              disabled={!step3Ready}
              onClick={() => setStep(4)}
            >
              다음
            </Button>
          </div>
        </div>
      )}

      {step === 4 && (
        <div className="mt-8 flex flex-col gap-6">
          <fieldset>
            <legend className="mb-3 text-body font-bold text-text">
              관심 분야 <span className="font-normal text-text-sub">(필수)</span>
            </legend>
            <div className="flex flex-wrap gap-2">
              {categories.data?.map((c) => (
                <Chip
                  key={c.categoryCode}
                  selected={interests.includes(c.categoryCode)}
                  onClick={() => setInterests(toggle(interests, c.categoryCode))}
                >
                  {c.name}
                </Chip>
              ))}
            </div>
          </fieldset>
          <fieldset>
            <legend className="mb-3 text-body font-bold text-text">
              희망 역할 <span className="font-normal text-text-sub">(선택)</span>
            </legend>
            <div className="flex flex-wrap gap-2">
              {DESIRED_ROLES.map((r) => (
                <Chip
                  key={r.code}
                  selected={roles.includes(r.code)}
                  onClick={() => setRoles(toggle(roles, r.code))}
                >
                  {r.label}
                </Chip>
              ))}
            </div>
          </fieldset>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input
              id="signup-school"
              label="학교"
              placeholder="한국대학교"
              value={school}
              maxLength={50}
              onChange={(e) => setSchool(e.target.value)}
            />
            <Input
              id="signup-major"
              label="전공"
              placeholder="컴퓨터공학"
              value={major}
              maxLength={50}
              onChange={(e) => setMajor(e.target.value)}
            />
          </div>
          <div className="flex flex-col">
            <label htmlFor="signup-grade" className="mb-2 text-body font-bold text-text">
              학년
            </label>
            <select
              id="signup-grade"
              value={grade}
              onChange={(e) => setGrade(e.target.value)}
              className="h-14 rounded-input border border-line-input bg-surface px-4 text-body text-text focus:border-2 focus:border-primary focus:outline-none"
            >
              <option value="">선택 안 함</option>
              {GRADES.map((g) => (
                <option key={g.code} value={g.code}>
                  {g.label}
                </option>
              ))}
            </select>
          </div>
          <Input
            id="signup-bio"
            label="한 줄 소개"
            placeholder="나를 한 줄로 소개해 보세요"
            value={bio}
            maxLength={BIO_MAX}
            onChange={(e) => setBio(e.target.value)}
            helperText={`${bio.length} / ${BIO_MAX}`}
          />
          <p className="text-small text-text-sub">
            경력·자격증·수상 이력은 가입 후 마이페이지 &gt; 내 프로필에서 같은 입력 창으로 추가할 수
            있어요.
          </p>
          <div className="flex gap-3">
            <Button
              variant="outline"
              size="lg"
              onClick={() => submit(true)}
              disabled={interests.length === 0 || submitting}
            >
              나중에 입력
            </Button>
            <Button
              variant="primary"
              size="lg"
              className="flex-1"
              disabled={interests.length === 0}
              loading={submitting}
              onClick={() => submit(false)}
            >
              가입 완료
            </Button>
          </div>
        </div>
      )}

      <TermsModal
        type={termsOpen}
        onClose={() => setTermsOpen(null)}
        onAgree={(type) => {
          setAgreed({ ...agreed, [type]: true })
          setTermsOpen(null)
        }}
      />
      <WithdrawnAccountModal
        open={emailGuide?.code === 'ACCOUNT_WITHDRAWN'}
        email={email.trim()}
        message={emailGuide?.message ?? null}
        onClose={() => setEmailGuide(null)}
        onSignupNew={() => {
          // ⑦ [새로 가입하기]: 이전 계정은 가입 완료 때 서버가 바로 완전 삭제한다
          setNewSignupConfirmed(true)
          setEmailGuide(null)
          toast.show('새로 가입하면 이전 계정의 프로필·이력·북마크가 모두 지워져요')
        }}
      />
      <Toast message={toast.message} />
    </AuthCard>
  )
}
