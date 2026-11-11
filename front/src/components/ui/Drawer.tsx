import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { CloseIcon } from '../icons/Icons'

interface DrawerProps {
  open: boolean
  title?: string
  onClose: () => void
  children: ReactNode
}

/** STYLE_GUIDE.md 5-7 서랍 (오른쪽에서 열림, 너비 380px, 왼쪽 모서리 12px, shadow-modal, 안쪽 20px) */
export function Drawer({ open, title, onClose, children }: DrawerProps) {
  useEffect(() => {
    if (!open) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title ?? '패널'}
      className="fixed inset-0 z-40 flex justify-end bg-text/20"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <aside className="flex h-full w-full max-w-[380px] flex-col overflow-y-auto rounded-l-popover bg-surface p-5 shadow-modal">
        <div className="mb-4 flex items-center justify-between">
          {title ? (
            <h2 className="text-subheading font-bold text-text">{title}</h2>
          ) : (
            <span aria-hidden="true" />
          )}
          <button
            type="button"
            onClick={onClose}
            aria-label="서랍 닫기"
            className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-text-sub transition-colors hover:bg-bg hover:text-text"
          >
            <CloseIcon className="h-6 w-6" />
          </button>
        </div>
        <div className="flex flex-1 flex-col">{children}</div>
      </aside>
    </div>
  )
}

