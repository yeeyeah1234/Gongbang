import type { ButtonHTMLAttributes, ReactNode } from 'react'

export type ButtonVariant = 'primary' | 'outline' | 'secondary' | 'danger' | 'text' | 'kakao'
export type ButtonSize = 'lg' | 'md' | 'sm'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
  children: ReactNode
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-surface font-bold hover:bg-primary-hover',
  outline: 'bg-surface border border-line-strong text-text font-medium hover:bg-bg',
  secondary: 'bg-surface-sub text-text font-medium hover:bg-bg',
  danger: 'bg-surface border border-danger-line text-danger font-medium hover:bg-bg',
  text: 'bg-transparent text-primary font-medium hover:bg-primary-soft',
  kakao: 'bg-kakao text-text font-bold hover:brightness-95',
}

const SIZE_CLASSES: Record<ButtonSize, string> = {
  lg: 'h-14 px-7 text-subheading',
  md: 'h-11 px-5 text-[17px]',
  sm: 'h-9 px-4 text-small',
}

/** STYLE_GUIDE.md 5-1 규격을 따르는 공용 버튼 컴포넌트 */
export function Button({
  variant = 'outline',
  size = 'md',
  loading = false,
  disabled = false,
  type = 'button',
  className = '',
  children,
  ...props
}: ButtonProps) {
  const isDisabled = disabled || loading

  return (
    <button
      type={type}
      disabled={isDisabled}
      className={[
        'inline-flex shrink-0 cursor-pointer items-center justify-center gap-2 rounded-full whitespace-nowrap transition-colors',
        'disabled:cursor-not-allowed disabled:border-transparent disabled:bg-surface-sub disabled:text-text-disabled',
        VARIANT_CLASSES[variant],
        SIZE_CLASSES[size],
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      {loading && (
        <span
          aria-hidden="true"
          className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  )
}

