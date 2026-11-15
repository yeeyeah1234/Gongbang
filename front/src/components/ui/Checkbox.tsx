import type { InputHTMLAttributes, ReactNode } from 'react'

interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: ReactNode
}

/** STYLE_GUIDE.md 5-2 체크박스 20x20, 모서리 4px, 체크 시 primary 배경 (정진우 추가) */
export function Checkbox({ label, className = '', ...props }: CheckboxProps) {
  return (
    <label className={['inline-flex cursor-pointer items-center gap-2', className].join(' ')}>
      <input
        type="checkbox"
        className="checked:bg-[url('data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 24 24%22 fill=%22none%22 stroke=%22white%22 stroke-width=%223%22><path d=%22M5 12l5 5 9-10%22/></svg>')] size-5 shrink-0 cursor-pointer appearance-none rounded-[4px] border border-line-strong bg-surface bg-center bg-no-repeat checked:border-primary checked:bg-primary focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:bg-surface-sub"
        {...props}
      />
      {label && <span className="text-small text-text">{label}</span>}
    </label>
  )
}
