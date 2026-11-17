import { useState, type InputHTMLAttributes } from 'react'

interface PasswordInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string
  errorText?: string
  successText?: string
}

function EyeIcon({ open }: { open: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      className="size-5"
      aria-hidden="true"
    >
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
      {!open && <path d="M3 3l18 18" />}
    </svg>
  )
}

// AUTH-01 ①, MY-04 ①: 눈 아이콘으로 보기/숨기기. 모양은 공통 Input(STYLE_GUIDE 5-2)과 같다
export function PasswordInput({
  id,
  label,
  errorText,
  successText,
  className = '',
  ...props
}: PasswordInputProps) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="flex flex-col">
      <label htmlFor={id} className="mb-2 text-body font-bold text-text">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          className={[
            'h-14 w-full rounded-input bg-surface pr-12 pl-4 text-body text-text placeholder:text-text-disabled focus:outline-none',
            errorText
              ? 'border-2 border-danger'
              : 'border border-line-input focus:border-2 focus:border-primary',
            className,
          ].join(' ')}
          {...props}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? '비밀번호 숨기기' : '비밀번호 보기'}
          className="absolute top-1/2 right-3 flex size-8 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full text-text-sub hover:bg-bg"
        >
          <EyeIcon open={visible} />
        </button>
      </div>
      {errorText && <p className="mt-2 text-small text-danger">{errorText}</p>}
      {!errorText && successText && <p className="mt-2 text-small text-success">{successText}</p>}
    </div>
  )
}
