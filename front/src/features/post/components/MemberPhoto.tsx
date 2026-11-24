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
  // 실패한 주소를 기억해, 다른 사진 주소로 바뀌면 다시 불러온다 (ProfilePhoto와 같은 방식)
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  if (url && url !== failedUrl) {
    return (
      <img
        src={url}
        alt=""
        onError={() => setFailedUrl(url)}
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
