import { useState } from 'react'

export type ProfilePhotoSize = 'lg' | 'md' | 'sm'

// lg: 마이페이지·공개 프로필(Figma 08-1), md: 사용자 메뉴·프로필 카드·지원자 관리, sm: 글·댓글 작성자
const SIZE_CLASSES: Record<ProfilePhotoSize, string> = {
  lg: 'size-20 text-subheading',
  md: 'size-12 text-small',
  sm: 'size-8 text-caption',
}

/**
 * 회원 프로필 사진(원형). 사진이 없거나 불러오지 못하면 닉네임 첫 글자를 보여 준다.
 * 실패한 주소를 기억해 두어, 다른 사진 주소로 바뀌면 다시 불러온다.
 * decorative: 바로 옆에 닉네임이 보이는 자리면 true (화면 읽기 프로그램이 이름을 두 번 읽지 않게 alt를 비운다)
 */
export function ProfilePhoto({
  url,
  name,
  size = 'lg',
  decorative = false,
}: {
  url: string | null
  name: string
  size?: ProfilePhotoSize
  decorative?: boolean
}) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const box = SIZE_CLASSES[size]
  if (url && url !== failedUrl) {
    return (
      <img
        src={url}
        alt={decorative ? '' : `${name} 프로필 사진`}
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
