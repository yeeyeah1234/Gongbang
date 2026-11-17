import type { ReactNode } from 'react'
import { NavLink } from 'react-router'
import { useLoginMember } from '../../auth/store'

// MY-06 ① 왼쪽 메뉴: 내 프로필 / 내 글 관리 / 북마크 / 계정 설정 (④ 좁은 화면은 위쪽 가로 탭)
const MENUS = [
  { to: '/mypage/profile', label: '내 프로필' },
  { to: '/mypage/posts', label: '내 글 관리' },
  { to: '/mypage/bookmarks', label: '북마크' },
  { to: '/mypage/settings', label: '계정 설정' },
]

interface MyPageLayoutProps {
  breadcrumb: string
  title: string
  description?: string
  action?: ReactNode
  children: ReactNode
}

// Figma 08~11 공통: 왼쪽 「마이페이지」 + 닉네임 카드 + 메뉴, 오른쪽에 경로·제목·설명
export function MyPageLayout({
  breadcrumb,
  title,
  description,
  action,
  children,
}: MyPageLayoutProps) {
  const member = useLoginMember()
  return (
    <main className="mx-auto flex w-full max-w-[1680px] flex-col gap-8 px-4 py-10 md:flex-row md:px-12 xl:px-[120px]">
      <aside className="md:w-[220px] md:shrink-0">
        <h2 className="hidden text-heading font-bold text-text md:block">마이페이지</h2>
        <div className="mt-4 hidden rounded-input bg-surface-sub px-4 py-3 md:block">
          <p className="text-body font-bold text-text">{member?.nickname ?? ''} 님</p>
          <p className="text-caption text-text-sub">함께 도전하는 공간</p>
        </div>
        <nav
          aria-label="마이페이지 메뉴"
          className="flex gap-2 overflow-x-auto border-b border-divider md:mt-6 md:flex-col md:gap-0 md:border-b-0"
        >
          {MENUS.map((menu) => (
            <NavLink
              key={menu.to}
              to={menu.to}
              className={({ isActive }) =>
                [
                  'flex shrink-0 items-center justify-between px-3 py-3 text-body whitespace-nowrap md:border-b md:border-line',
                  isActive
                    ? 'border-b-2 border-primary font-bold text-text md:border-b md:border-line md:bg-primary-soft md:text-primary'
                    : 'text-text-sub hover:text-text md:text-text',
                ].join(' ')
              }
            >
              {menu.label}
              <span aria-hidden="true" className="hidden text-text-sub md:inline">
                ›
              </span>
            </NavLink>
          ))}
        </nav>
      </aside>
      <section className="min-w-0 flex-1">
        <p className="text-caption text-text-sub">{breadcrumb}</p>
        <div className="mt-2 flex items-start justify-between gap-4">
          <div>
            <h1 className="text-title font-bold text-text">{title}</h1>
            {description && <p className="mt-1 text-small text-text-sub">{description}</p>}
          </div>
          {action}
        </div>
        <div className="mt-6">{children}</div>
      </section>
    </main>
  )
}
