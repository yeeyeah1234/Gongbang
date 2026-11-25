import type { ReactNode } from 'react'

interface AuthCardProps {
  title: string
  description?: ReactNode
  wide?: boolean
  children: ReactNode
}

// Figma 01~07: 가운데 한 줄 폼(1920 기준 480px), 위에 제목 28px + 설명 14px
export function AuthCard({ title, description, wide = false, children }: AuthCardProps) {
  return (
    <main className="px-4 pt-8 pb-[calc(112px+env(safe-area-inset-bottom))] md:pt-16 md:pb-24">
      <div className={['mx-auto w-full', wide ? 'max-w-[720px]' : 'max-w-[480px]'].join(' ')}>
        <h1 className="text-title font-bold text-text">{title}</h1>
        {description && <p className="mt-2 text-small text-text-sub">{description}</p>}
        <div className="mt-8">{children}</div>
      </div>
    </main>
  )
}
