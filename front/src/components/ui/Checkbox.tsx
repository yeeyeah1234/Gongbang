import type { InputHTMLAttributes, ReactNode } from 'react'

interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: ReactNode
}

/** STYLE_GUIDE.md 5-2 체크박스 20x20, 모서리 4px, 체크 시 primary 배경 + 흰 ✓ (정진우 추가) */
export function Checkbox({ label, className = '', ...props }: CheckboxProps) {
  return (
    <label className={['inline-flex cursor-pointer items-center gap-2', className].join(' ')}>
      <span className="relative inline-flex size-5 shrink-0">
        <input
          type="checkbox"
          className="peer size-5 cursor-pointer appearance-none rounded-[4px] border border-line-strong bg-surface checked:border-primary checked:bg-primary focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:bg-surface-sub"
          {...props}
        />
        {/* 체크 표시: 체크했을 때만 흰 ✓ (클릭은 아래 input이 받는다) */}
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={3}
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 m-auto hidden size-3.5 text-surface peer-checked:block"
        >
          <path d="M5 12l5 5 9-10" />
        </svg>
      </span>
      {label && <span className="text-small text-text">{label}</span>}
    </label>
  )
}
