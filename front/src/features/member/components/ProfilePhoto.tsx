import { useState } from 'react'

// Figma 08-1 「사진」 원형 자리. 사진이 없거나 불러오지 못하면 닉네임 첫 글자
export function ProfilePhoto({
  url,
  name,
  size = 'lg',
}: {
  url: string | null
  name: string
  size?: 'lg' | 'md'
}) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const box = size === 'lg' ? 'size-20 text-subheading' : 'size-12 text-small'
  if (url && url !== failedUrl) {
    return (
      <img
        src={url}
        alt={`${name} 프로필 사진`}
        onError={() => setFailedUrl(url)}
        className={`${box} shrink-0 rounded-full object-cover`}
      />
    )
  }
  return (
    <span
      className={`${box} flex shrink-0 items-center justify-center rounded-full bg-surface-sub font-bold text-text-sub`}
      aria-hidden="true"
    >
      {name.slice(0, 1) || '사진'}
    </span>
  )
}
