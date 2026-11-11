import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { CloseIcon } from '../icons/Icons'

export type ModalSize = 'sm' | 'md' | 'lg'

interface ModalProps {
  open: boolean
  title: string
  description?: string
  size?: ModalSize
  onClose: () => void
  children?: ReactNode
  footer?: ReactNode
}

const MODAL_SIZE_CLASSES: Record<ModalSize, string> = {
  sm: 'max-w-[460px] rounded-card p-8',
  md: 'max-w-[520px] rounded-modal p-10',
  lg: 'max-w-[600px] rounded-modal p-10',
}

/** STYLE_GUIDE.md 5-6 모달 (배경 막 40%, Esc·바깥 클릭·우측 상단 X 닫기) */
export function Modal({
  open,
  title,
  description,
  size = 'md',
  onClose,
  children,
  footer,
}: ModalProps) {
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
      aria-labelledby="modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-text/40 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <div
        className={['relative w-full bg-surface shadow-modal', MODAL_SIZE_CLASSES[size]].join(' ')}
      >
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="absolute top-6 right-6 flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-text-sub transition-colors hover:bg-bg hover:text-text"
        >
          <CloseIcon className="h-6 w-6" />
        </button>

        <h2 id="modal-title" className="pr-8 text-heading font-bold text-text">
          {title}
        </h2>
        {description && <p className="mt-3 text-body text-text-sub">{description}</p>}

        {children && <div className="mt-6">{children}</div>}

        {footer && <div className="mt-8 flex items-center justify-end gap-3">{footer}</div>}
      </div>
    </div>
  )
}
