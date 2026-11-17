import type { ButtonHTMLAttributes } from 'react'

interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  selected?: boolean
  size?: 'md' | 'sm'
}

/** STYLE_GUIDE.md 5-3 칩: 높이 40, 알약, 기본 surface+line-strong / 선택 primary+흰 글자 700 (정진우 추가) */
export function Chip({
  selected = false,
  size = 'md',
  className = '',
  children,
  ...props
}: ChipProps) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      className={[
        'inline-flex shrink-0 cursor-pointer items-center justify-center rounded-full border whitespace-nowrap transition-colors',
        size === 'md' ? 'h-10 px-5 text-small' : 'h-8 px-3 text-caption',
        selected
          ? 'border-primary bg-primary font-bold text-surface'
          : 'border-line-strong bg-surface text-text hover:bg-bg',
        className,
      ].join(' ')}
      {...props}
    >
      {children}
    </button>
  )
}
