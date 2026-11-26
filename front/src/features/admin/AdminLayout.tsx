import { Link, NavLink, Outlet } from 'react-router'
import gongbangLogo from '../../assets/gongbang-logo.png'
import { useLoginMember } from '../auth/store'
import { useState, type ReactNode } from 'react'
import { useIsWide } from '../../hooks/useIsWide'
import { Button } from '../../components/ui/Button'
import { Gnb } from '../../components/layout/Gnb'

export function AdminLayout() {
  const member = useLoginMember()
  if (member?.role !== 'ADMIN')
    return (
      <main className="min-h-screen bg-bg px-6 py-16 text-text">
        <div className="mx-auto max-w-lg rounded-card border border-line bg-surface p-8">
          <h1 className="text-heading font-bold">관리자만 접근할 수 있어요</h1>
          <p className="mt-3 text-body text-text-sub">현재 계정에는 관리자 권한이 없습니다.</p>
          <Link to="/" className="mt-6 inline-block text-body font-bold text-primary">
            서비스로 돌아가기
          </Link>
        </div>
      </main>
    )
  return (
    <AdminShell nickname={member.nickname}>
      <Outlet />
    </AdminShell>
  )
}

export function AdminShell({
  nickname,
  basePath = '/admin',
  children,
}: {
  nickname: string
  basePath?: string
  children: ReactNode
}) {
  const isWide = useIsWide()
  const [menuOpen, setMenuOpen] = useState(false)
  return (
    <div className="min-h-screen bg-surface text-text">
      {basePath === '/admin' ? (
        <Gnb />
      ) : (
        <header className="flex flex-wrap items-center justify-between gap-4 border-b border-divider bg-surface px-6 py-5 sm:px-8">
          <Link to={`${basePath}/verifications`} className="flex items-center gap-3">
            <img src={gongbangLogo} alt="공방" className="h-9 w-auto md:h-10" />
            <span className="border-l border-line pl-3 text-body">
              공모전 · 커뮤니티 · 워크스페이스
            </span>
          </Link>
          <div className="flex items-center gap-6 text-small">
            <span className="text-text-sub">{nickname} 님</span>
            <Link to="/" className="font-medium text-primary hover:underline">
              서비스로 돌아가기
            </Link>
          </div>
        </header>
      )}
      <div className="mx-auto flex max-w-[1680px] flex-col gap-6 px-4 pt-8 pb-[calc(112px+env(safe-area-inset-bottom))] md:px-6 md:py-12 lg:flex-row lg:gap-8 lg:px-8">
        {!isWide && (
          <Button aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}>
            ≡ 관리자 메뉴 · {nickname}
          </Button>
        )}
        {(isWide || menuOpen) && (
          <aside className="border-b border-divider bg-surface pb-4 lg:w-52 lg:shrink-0 lg:border-b-0">
            <h1 className="mb-5 text-subheading font-bold">
              <span className="text-primary">관리자</span> 페이지
            </h1>
            <div className="mb-6 bg-surface-sub p-4">
              <div className="flex items-center justify-between gap-2">
                <p className="min-w-0 text-body font-bold break-words">{nickname} 님</p>
                <span className="shrink-0 bg-text px-2 py-1 text-caption text-surface">ADMIN</span>
              </div>
              <span className="text-caption text-text-sub">함께 도전하는 공간</span>
            </div>
            <nav
              aria-label="관리자 메뉴"
              className="divide-y divide-divider border-y border-divider"
            >
              {[
                { path: 'verifications', label: '자격증·수상 심사' },
                { path: 'notices', label: '운영 공지 관리' },
                { path: 'reports', label: '신고 처리' },
                { path: 'members', label: '회원 관리' },
                { path: 'posts', label: '모집글·댓글 관리' },
                { path: 'contests', label: '공모전 관리' },
              ].map((item) => (
                <NavLink
                  onClick={() => setMenuOpen(false)}
                  key={item.path}
                  to={`${basePath}/${item.path}`}
                  className={({ isActive }) =>
                    `flex items-center justify-between gap-2 px-3 py-3 text-small ${isActive ? 'bg-surface-sub font-bold text-text' : 'hover:bg-bg'}`
                  }
                >
                  {item.label}
                  <span aria-hidden="true">›</span>
                </NavLink>
              ))}
            </nav>
            <p className="mt-6 hidden text-small leading-relaxed text-text-sub lg:block">
              운영 메뉴에서 서비스의
              <br />
              주요 콘텐츠와 회원을 관리합니다.
            </p>
          </aside>
        )}
        <div className="min-w-0 flex-1">{children}</div>
      </div>
    </div>
  )
}
