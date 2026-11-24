import type { DocumentSaveState } from '../useDocumentSync'

interface DocumentSaveStatusProps {
  state: DocumentSaveState
  onRetry: () => void
}

// WS-04 저장 상태 표시 (작업 영역 오른쪽 아래)
export function DocumentSaveStatus({ state, onRetry }: DocumentSaveStatusProps) {
  if (state === 'offline') {
    return (
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-auto rounded-input border border-warning/40 bg-warning/10 px-3.5 py-2 text-small font-medium text-warning shadow-pop"
      >
        오프라인 - 연결되면 자동으로 반영됩니다
      </div>
    )
  }

  if (state === 'failed') {
    return (
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-auto flex items-center gap-3 rounded-input border border-danger-line bg-danger-soft px-3.5 py-2 text-small font-medium text-danger shadow-pop"
      >
        <span>저장하지 못했어요</span>
        <button
          type="button"
          onClick={onRetry}
          className="cursor-pointer font-bold underline underline-offset-2 hover:opacity-80"
        >
          다시 시도
        </button>
      </div>
    )
  }

  if (state === 'saving') {
    return (
      <div
        role="status"
        aria-live="polite"
        className="rounded-input border border-line bg-surface/95 px-3 py-1.5 text-small text-text-sub shadow-pop"
      >
        저장 중…
      </div>
    )
  }

  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center gap-1.5 rounded-input border border-line bg-surface/95 px-3 py-1.5 text-small text-text-sub shadow-pop"
    >
      <span aria-hidden="true" className="h-2 w-2 rounded-full bg-success" />
      <span>저장됨</span>
    </div>
  )
}
