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

// 640 미만(#117, STYLE_GUIDE 8): 확인 모달(sm·md)은 가운데 카드 그대로 여백만 줄이고,
// 작성 모달(lg)은 화면 전체로 열고 버튼 줄을 아래에 둔다. 모든 크기는 높이가 넘치면 본문만 스크롤된다.
const MODAL_SIZE_CLASSES: Record<ModalSize, string> = {
  sm: 'max-w-[460px] rounded-card p-8 max-sm:p-6',
  md: 'max-w-[520px] rounded-modal p-10 max-sm:p-6',
  lg: 'max-w-[600px] rounded-modal p-10 max-sm:h-full max-sm:max-w-none max-sm:rounded-none max-sm:px-4 max-sm:pt-6 max-sm:pb-[calc(1.5rem+env(safe-area-inset-bottom))]',
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

  const fullScreenOnPhone = size === 'lg'

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      className={[
        'fixed inset-0 z-50 flex items-center justify-center bg-text/40 p-4',
        fullScreenOnPhone ? 'max-sm:p-0' : '',
      ].join(' ')}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <div
        className={[
          'relative flex max-h-full w-full flex-col bg-surface shadow-modal',
          MODAL_SIZE_CLASSES[size],
        ].join(' ')}
      >
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className={[
            'absolute top-6 right-6 flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-text-sub transition-colors hover:bg-bg hover:text-text',
            size === 'sm' ? '' : 'max-sm:top-5 max-sm:right-4',
          ].join(' ')}
        >
          <CloseIcon className="h-6 w-6" />
        </button>

        <h2 id="modal-title" className="shrink-0 pr-8 text-heading font-bold text-text">
          {title}
        </h2>
        {description && <p className="mt-3 shrink-0 text-body text-text-sub">{description}</p>}

        {children && (
          // 입력창 포커스 테두리가 스크롤 영역에 잘리지 않게 좌우 4px씩 여유를 둔다
          <div
            className={[
              '-mx-1 mt-6 min-h-0 overflow-y-auto px-1',
              fullScreenOnPhone ? 'max-sm:flex-1' : '',
            ].join(' ')}
          >
            {children}
          </div>
        )}

        {footer && (
          <div
            className={[
              'mt-8 flex shrink-0 flex-wrap items-center justify-end gap-3',
              fullScreenOnPhone ? 'max-sm:mt-auto max-sm:pt-6' : '',
            ].join(' ')}
          >
            {footer}
          </div>
        )}
      </div>
    </div>
  )
}
