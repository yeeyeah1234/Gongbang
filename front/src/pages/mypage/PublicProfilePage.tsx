import { useState } from 'react'
import { useParams } from 'react-router'
import { FlagIcon } from '../../components/icons/Icons'
import { Toast } from '../../components/ui/Toast'
import { useIsLoggedIn, useLoginMember } from '../../features/auth/store'
import { useCategories } from '../../features/contest/hooks'
import { ProfilePhoto } from '../../components/ui/ProfilePhoto'
import { formatDate, formatMonth, roleLabel } from '../../features/member/constants'
import { usePublicProfile } from '../../features/member/hooks'
import { LoginRequiredModal } from '../../features/post/components/LoginRequiredModal'
import { ReportModal } from '../../features/report/components/ReportModal'
import { useTimedToast } from '../../hooks/useTimedToast'

// MY-05 공개 프로필 전용 화면 (팝오버의 [프로필 보기])
export default function PublicProfilePage() {
  const memberId = Number(useParams().memberId)
  const { data, isLoading, isError } = usePublicProfile(Number.isFinite(memberId) ? memberId : null)
  const categories = useCategories()
  const isLoggedIn = useIsLoggedIn()
  const loginMember = useLoginMember()
  const toast = useTimedToast()
  const [reportOpen, setReportOpen] = useState(false)
  const [loginModalOpen, setLoginModalOpen] = useState(false)

  if (isLoading)
    return (
      <main className="mx-auto max-w-[960px] px-4 py-10">
        <div className="h-64 animate-pulse rounded-card bg-surface-sub" />
      </main>
    )
  if (isError || !data)
    return (
      <main className="mx-auto max-w-[960px] px-4 py-20 text-center text-body text-text-sub">
        없거나 탈퇴한 회원이에요
      </main>
    )

  const interests = data.interestCategoryCodes.map(
    (code) => categories.data?.find((c) => c.categoryCode === code)?.name ?? code,
  )

  const isSelf = loginMember?.memberId === data.memberId

  const handleReportClick = () => {
    if (!isLoggedIn) {
      setLoginModalOpen(true)
      return
    }
    setReportOpen(true)
  }

  return (
    <main className="mx-auto flex max-w-[960px] flex-col gap-6 px-4 py-10">
      <section className="flex flex-wrap items-center justify-between gap-4 rounded-card border border-line bg-surface p-8">
        <div className="flex items-center gap-6">
          <ProfilePhoto url={data.profileImageUrl} name={data.nickname} />
          <div>
            <h1 className="text-title font-bold text-text">{data.nickname}</h1>
            <p className="text-small text-text-sub">
              {[data.school, data.major].filter(Boolean).join(' · ')}
            </p>
            {data.bio && <p className="mt-2 text-body text-text">{data.bio}</p>}
          </div>
        </div>
        {!isSelf && (
          <button
            type="button"
            onClick={handleReportClick}
            className="inline-flex h-10 cursor-pointer items-center gap-1.5 rounded-full border border-danger-line px-4 text-small font-medium text-danger transition-colors hover:bg-danger-soft"
          >
            <FlagIcon className="h-4 w-4" />
            <span>신고하기</span>
          </button>
        )}
      </section>
      <section className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <div className="rounded-card border border-line bg-surface p-6">
          <h2 className="text-body font-bold text-text">관심 분야 · 희망 역할</h2>
          <div className="mt-3 flex flex-wrap gap-2">
            {[...interests, ...data.desiredRoles.map(roleLabel)].map((t) => (
              <span
                key={t}
                className="rounded-full border border-line-strong px-3 py-1 text-caption"
              >
                {t}
              </span>
            ))}
          </div>
          {data.skillTags.length > 0 && (
            <div className="mt-4 flex flex-wrap gap-2">
              {data.skillTags.map((t) => (
                <span key={t} className="rounded-full bg-surface-sub px-3 py-1 text-caption">
                  #{t}
                </span>
              ))}
            </div>
          )}
          {data.portfolioUrl && (
            <a
              href={data.portfolioUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-4 inline-block text-small text-primary underline"
            >
              포트폴리오 보기
            </a>
          )}
        </div>
        <div className="flex flex-col gap-4 rounded-card border border-line bg-surface p-6">
          <h2 className="text-body font-bold text-text">경력</h2>
          {data.careers.length === 0 ? (
            <p className="text-small text-text-sub">-</p>
          ) : (
            data.careers.map((c, i) => (
              <p key={i} className="text-small text-text">
                {c.organization} · {c.role}{' '}
                <span className="text-text-sub">
                  ({formatMonth(c.startDate)} – {c.endDate ? formatMonth(c.endDate) : '현재'})
                </span>
                {c.linkUrl && (
                  <>
                    {' '}
                    <a
                      href={c.linkUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-primary underline"
                    >
                      관련 링크
                    </a>
                  </>
                )}
              </p>
            ))
          )}
          <h2 className="mt-2 text-body font-bold text-text">자격증</h2>
          {data.certificates.length === 0 ? (
            <p className="text-small text-text-sub">-</p>
          ) : (
            data.certificates.map((c, i) => (
              <p key={i} className="flex items-center gap-2 text-small text-text">
                {c.name} <span className="text-text-sub">{formatDate(c.acquiredDate)}</span>
                {c.verified && (
                  <span className="rounded-[4px] bg-success px-1.5 text-caption text-surface">
                    ✓ 인증
                  </span>
                )}
              </p>
            ))
          )}
          <h2 className="mt-2 text-body font-bold text-text">수상 경력</h2>
          {data.awards.length === 0 ? (
            <p className="text-small text-text-sub">-</p>
          ) : (
            data.awards.map((a, i) => (
              <p key={i} className="flex items-center gap-2 text-small text-text">
                {a.contestName} {a.awardName}{' '}
                <span className="text-text-sub">{formatDate(a.awardedDate)}</span>
                {a.verified && (
                  <span className="rounded-[4px] bg-success px-1.5 text-caption text-surface">
                    ✓ 인증
                  </span>
                )}
              </p>
            ))
          )}
        </div>
      </section>

      <ReportModal
        open={reportOpen}
        reportedMemberId={data.memberId}
        targetNickname={data.nickname}
        targetType="MEMBER"
        onClose={() => setReportOpen(false)}
        onSubmitted={toast.show}
      />

      <LoginRequiredModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        redirectTo={`/members/${data.memberId}`}
      />

      <Toast message={toast.message} />
    </main>
  )
}
