import type { ReactNode } from 'react'
/** STYLE_GUIDE 8: 모바일 주요 동작과 본문 사이의 공간을 함께 확보한다. */
export function MobileActionBar({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-[calc(80px+env(safe-area-inset-bottom))] md:contents">
      <div className="fixed inset-x-0 bottom-0 z-20 flex flex-wrap items-center justify-end gap-3 border-t border-divider bg-surface px-4 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))] md:contents">
        {children}
      </div>
    </div>
  )
}
