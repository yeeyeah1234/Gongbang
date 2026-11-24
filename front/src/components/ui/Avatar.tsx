import { CrownIcon } from '../icons/Icons'

export type MemberColorIndex = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8
export type AvatarSize = 'md' | 'sm'

interface AvatarProps {
  name: string
  colorIndex: MemberColorIndex
  size?: AvatarSize
  isLeader?: boolean
  /** 기본값은 name. null을 넘기면 네이티브 title 속성을 붙이지 않는다(커스텀 툴팁과 겹침 방지). */
  title?: string | null
  className?: string
}

const MEMBER_COLOR_CLASSES: Record<MemberColorIndex, string> = {
  1: 'border-member-1 text-member-1',
  2: 'border-member-2 text-member-2',
  3: 'border-member-3 text-member-3',
  4: 'border-member-4 text-member-4',
  5: 'border-member-5 text-member-5',
  6: 'border-member-6 text-member-6',
  7: 'border-member-7 text-member-7',
  8: 'border-member-8 text-member-8',
}

const SIZE_CLASSES: Record<AvatarSize, string> = {
  md: 'h-10 w-10 text-small',
  sm: 'h-8 w-8 text-caption',
}

/** STYLE_GUIDE.md 5-9 아바타: 원형 40/32px, 팀원 고유 색 2px 테두리, 이름 두 글자 700, 팀장은 왕관 아이콘 */
export function Avatar({
  name,
  colorIndex,
  size = 'md',
  isLeader = false,
  title,
  className = '',
}: AvatarProps) {
  const shortLabel = name.length > 2 ? name.slice(-2) : name
  const nativeTitle = title === undefined ? name : (title ?? undefined)

  return (
    <div className={['relative inline-flex shrink-0', className].filter(Boolean).join(' ')}>
      <div
        title={nativeTitle}
        className={[
          'flex items-center justify-center rounded-full border-2 bg-surface font-bold select-none',
          MEMBER_COLOR_CLASSES[colorIndex],
          SIZE_CLASSES[size],
        ].join(' ')}
      >
        {shortLabel}
      </div>
      {isLeader && (
        <span
          aria-label="팀장"
          className="absolute -top-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-surface text-warning shadow-pop"
        >
          <CrownIcon className="h-3 w-3" />
        </span>
      )}
    </div>
  )
}
