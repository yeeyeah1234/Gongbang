import { useState } from 'react'

// 회원 프로필 사진. 없거나 불러오지 못하면 닉네임 첫 글자 (PST-005 작성자, REC-003 지원자)
export function MemberPhoto({
  url,
  nickname,
  className,
}: {
  url: string | null
  nickname: string
  // 크기와 글자 크기 (예: 'size-8 text-caption')
  className: string
}) {
  const [failed, setFailed] = useState(false)
  if (url && !failed) {
    return (
      <img
        src={url}
        alt=""
        onError={() => setFailed(true)}
        className={`${className} shrink-0 rounded-full object-cover`}
      />
    )
  }
  return (
    <span
      aria-hidden="true"
      className={`${className} flex shrink-0 items-center justify-center rounded-full bg-surface-sub font-bold text-text-sub`}
    >
      {nickname.slice(0, 1)}
    </span>
  )
}
