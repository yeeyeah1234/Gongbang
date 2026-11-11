import type { InputHTMLAttributes } from 'react'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  helperText?: string
  errorText?: string
  successText?: string
}

/** STYLE_GUIDE.md 5-2 입력창 (높이 56px, 모서리 10px, 라벨 필수 표기) */
export function Input({
  id,
  label,
  required = false,
  helperText,
  errorText,
  successText,
  disabled = false,
  className = '',
  ...props
}: InputProps) {
  const borderClass = errorText
    ? 'border-2 border-danger focus:border-danger'
    : 'border border-line-input focus:border-2 focus:border-primary'

  return (
    <div className="flex flex-col">
      {label && (
        <label htmlFor={id} className="mb-2 text-body font-bold text-text">
          {label}
          {required && <span className="ml-1 font-normal text-text-sub">(필수)</span>}
        </label>
      )}
      <input
        id={id}
        required={required}
        disabled={disabled}
        className={[
          'h-14 w-full rounded-input bg-surface px-4 text-body text-text placeholder:text-text-disabled focus:outline-none',
          'disabled:border disabled:border-line disabled:bg-surface-sub disabled:text-text-disabled',
          borderClass,
          className,
        ]
          .filter(Boolean)
          .join(' ')}
        {...props}
      />
      {errorText && <p className="mt-2 text-small text-danger">{errorText}</p>}
      {!errorText && successText && <p className="mt-2 text-small text-success">{successText}</p>}
      {!errorText && !successText && helperText && (
        <p className="mt-2 text-small text-text-sub">{helperText}</p>
      )}
    </div>
  )
}

