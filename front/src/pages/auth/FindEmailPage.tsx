import { MobileActionBar } from '../../components/layout/MobileActionBar'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { getApiError } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { findEmail } from '../../features/auth/api'
import { AuthCard } from '../../features/auth/components/AuthCard'
import type { FindEmailResponse } from '../../features/auth/types'
import { formatDate } from '../../features/member/constants'

// AUTH-02 ① 이메일 찾기 / Figma 02
export default function FindEmailPage() {
  const [nickname, setNickname] = useState('')
  const [result, setResult] = useState<FindEmailResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!nickname.trim()) return
    setLoading(true)
    setError(null)
    setResult(null)
    try {
      setResult(await findEmail(nickname.trim()))
    } catch (e) {
      setError(getApiError(e).message ?? '입력한 닉네임으로 가입된 계정이 없어요')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthCard title="이메일 찾기" description="가입할 때 등록한 닉네임을 입력해 주세요.">
      <form onSubmit={handleSubmit} className="flex flex-col gap-6">
        <Input
          id="find-nickname"
          label="닉네임"
          placeholder="닉네임"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          errorText={error ?? undefined}
        />
        {!result && (
          <MobileActionBar>
            <Button type="submit" variant="primary" size="lg" className="w-full" loading={loading}>
              이메일 찾기
            </Button>
          </MobileActionBar>
        )}
      </form>

      {result && (
        <div className="mt-6">
          <div className="rounded-card border border-line bg-surface p-6 text-center">
            <p className="text-small text-text-sub">가입한 이메일을 찾았어요</p>
            <p className="mt-2 text-heading font-bold text-text">{result.maskedEmail}</p>
            <p className="mt-2 text-small text-text-sub">
              {result.loginType === 'KAKAO' ? '카카오 가입' : '이메일 가입'} · 가입일{' '}
              {formatDate(result.joinedAt)}
            </p>
          </div>
          <div className="mt-6 flex gap-3">
            <Link to="/find-password" className="flex-1">
              <Button variant="outline" size="lg" className="w-full">
                비밀번호 찾기
              </Button>
            </Link>
            <Link to="/login" className="flex-1">
              <MobileActionBar>
                <Button variant="primary" size="lg" className="w-full">
                  로그인
                </Button>
              </MobileActionBar>
            </Link>
          </div>
        </div>
      )}
    </AuthCard>
  )
}
