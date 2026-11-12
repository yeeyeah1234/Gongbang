interface ToastProps {
  message: string | null
}

/** STYLE_GUIDE.md 5-8 토스트 (화면 위 가운데, #1F1F24 배경, 흰 글자 16px, 모서리 12px) */
export function Toast({ message }: ToastProps) {
  if (!message) return null

  return (
    <div className="pointer-events-none fixed top-6 left-1/2 z-50 -translate-x-1/2">
      <div
        role="status"
        aria-live="polite"
        className="rounded-popover bg-text px-5 py-3.5 text-body text-surface shadow-pop"
      >
        {message}
      </div>
    </div>
  )
}

