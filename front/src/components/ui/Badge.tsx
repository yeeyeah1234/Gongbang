import type { ReactNode } from 'react'

export type BadgeVariant = 'primary-soft' | 'danger-soft' | 'danger' | 'surface-sub' | 'primary'

interface BadgeProps {
  variant?: BadgeVariant
  className?: string
  children: ReactNode
}

const BADGE_CLASSES: Record<BadgeVariant, string> = {
  'primary-soft': 'bg-primary-soft text-primary',
  'danger-soft': 'bg-danger-soft text-danger',
  danger: 'bg-danger text-surface',
  'surface-sub': 'bg-surface-sub text-text-sub',
  primary: 'bg-primary text-surface',
}

/** STYLE_GUIDE.md 5-3 상태 배지 (높이 26px, 알약 모양, 12px 700) */
export function Badge({ variant = 'surface-sub', className = '', children }: BadgeProps) {
  return (
    <span
      className={[
        'inline-flex h-[26px] shrink-0 items-center justify-center rounded-full px-2.5 text-caption font-bold whitespace-nowrap',
        BADGE_CLASSES[variant],
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </span>
  )
}

interface CountBadgeProps {
  count: number
  className?: string
}

/** STYLE_GUIDE.md 5-4 안 읽은 개수 숫자 배지 (최소 20x20) */
export function CountBadge({ count, className = '' }: CountBadgeProps) {
  if (count <= 0) return null
  return (
    <span
      className={[
        'inline-flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full bg-primary px-1 text-caption font-bold text-surface',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {count > 99 ? '99+' : count}
    </span>
  )
}
