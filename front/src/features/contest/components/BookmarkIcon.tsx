// MAIN-06 ③·MAIN-02 ④: 북마크 아이콘. 마이페이지 북마크 목록(MY-03)과 같은 모양이다.
// 색은 부모의 글자 색(currentColor)을 따른다. 켜져 있으면 채운다.
export function BookmarkIcon({
  filled,
  className = 'size-5',
}: {
  filled: boolean
  className?: string
}) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={2}
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M6 3h12v18l-6-4-6 4z" />
    </svg>
  )
}
