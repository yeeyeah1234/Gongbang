import { Link } from 'react-router'
import { useCategories } from '../../contest/hooks'
import { roleLabel } from '../constants'
import { usePublicProfile } from '../hooks'
import { ProfilePhoto } from './ProfilePhoto'

interface PublicProfileCardProps {
  memberId: number
  onClose?: () => void
  // 팀 초대·신고 버튼은 김건우 NET-02 팝오버가 넣는다
  footer?: React.ReactNode
}

// MY-05 / Figma 19 공개 프로필 팝오버 (이메일·가입 방식·실명·증빙 파일은 보여 주지 않는다)
// 김건우 NET-02 닉네임 팝오버에서 <PublicProfileCard memberId={...} /> 로 쓴다
export function PublicProfileCard({ memberId, footer }: PublicProfileCardProps) {
  const { data, isLoading, isError } = usePublicProfile(memberId)
  const categories = useCategories()

  if (isLoading)
    return <div className="h-60 w-[320px] animate-pulse rounded-popover bg-surface-sub" />
  if (isError || !data)
    return <p className="w-[320px] p-6 text-small text-text-sub">없거나 탈퇴한 회원이에요</p>

  const tags = [
    ...data.desiredRoles.map(roleLabel),
    ...data.interestCategoryCodes.map(
      (code) => categories.data?.find((c) => c.categoryCode === code)?.name ?? code,
    ),
  ]
  const verifiedAward = data.awards.find((a) => a.verified)

  return (
    <div className="w-[320px] rounded-popover border border-line bg-surface p-5 shadow-pop">
      <div className="flex items-center gap-3">
        <ProfilePhoto url={data.profileImageUrl} name={data.nickname} size="md" />
        <div className="min-w-0">
          <p className="text-body font-bold text-text">{data.nickname}</p>
          <p className="truncate text-caption text-text-sub">
            {[data.school, data.major].filter(Boolean).join(' · ')}
          </p>
        </div>
      </div>
      {data.bio && <p className="mt-3 text-small text-text">{data.bio}</p>}
      {tags.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {tags.map((tag) => (
            <span
              key={tag}
              className="rounded-full border border-line-strong px-2.5 py-0.5 text-caption text-text"
            >
              {tag}
            </span>
          ))}
        </div>
      )}
      <dl className="mt-4 grid grid-cols-3 rounded-input bg-bg py-3 text-center">
        <div>
          <dt className="text-caption text-text-sub">경력</dt>
          <dd className="text-body font-bold">{data.careers.length}</dd>
        </div>
        <div>
          <dt className="text-caption text-text-sub">수상</dt>
          <dd className="text-body font-bold">{data.awards.length}</dd>
        </div>
        <div>
          <dt className="text-caption text-text-sub">자격증</dt>
          <dd className="text-body font-bold">{data.certificates.length}</dd>
        </div>
      </dl>
      {verifiedAward && (
        <p className="mt-3 flex items-center gap-2 text-caption text-text">
          <span className="text-text-sub">대표 이력</span>
          {verifiedAward.contestName} {verifiedAward.awardName}
          <span className="font-bold text-success">✓ 인증</span>
        </p>
      )}
      <Link
        to={`/members/${data.memberId}`}
        className="mt-4 inline-flex h-9 items-center rounded-full border border-line-strong px-4 text-small text-text hover:bg-bg"
      >
        프로필 보기
      </Link>
      {footer}
    </div>
  )
}
