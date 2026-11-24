import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useIsLoggedIn } from '../../features/contest/hooks'
import { loginPathWithRedirect } from '../../features/auth/redirect'
import { UserMenu } from '../../features/auth/components/UserMenu'
import { NotificationBell } from '../../features/notification/components/NotificationBell'
import { useTimedToast } from '../../hooks/useTimedToast'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { Toast } from '../ui/Toast'

// SRCH-03 ①: 2글자 미만이면 검색하지 않는다
const MIN_KEYWORD_LENGTH = 2

// GNB-01 ③ 메인 메뉴
// activePrefixes: 이 경로들에 있으면 메뉴를 선택된 것으로 보여 준다 (공모전 상세도 「공모전」)
const MENUS = [
  { label: '공모전', to: '/', activePrefixes: ['/contests/'] },
  // TODO(확인 필요): 커뮤니티(팀원 모집 게시판, PST-001 김수형) 화면 경로가 정해지면 맞춘다
  { label: '커뮤니티', to: '/posts', activePrefixes: ['/posts/', '/board'] },
  // 비로그인도 보인다. 비로그인이 누르면 「로그인이 필요한 서비스입니다」 팝업 → [로그인]으로 로그인 화면 (GNB-01 ④)
  // 경로 /workspaces는 이상현과 확정 (11/13, WS-08)
  {
    label: '워크스페이스',
    to: '/workspaces',
    activePrefixes: ['/workspaces/'],
    requiresLogin: true,
  },
]

// TODO(확인 필요): 로그인 화면 경로는 인증 담당(정진우)과 맞춘다 (api/client.ts의 401 처리와 같은 /login)
const LOGIN_PATH = '/login'

function SearchIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="size-5"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  )
}

// #117 좁은 화면 메뉴 버튼 (SearchIcon과 같은 24px 선 아이콘 형식)
function MenuIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="size-6"
    >
      <path d="M4 6h16" />
      <path d="M4 12h16" />
      <path d="M4 18h16" />
    </svg>
  )
}

function CloseMenuIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="size-6"
    >
      <path d="M18 6 6 18" />
      <path d="m6 6 12 12" />
    </svg>
  )
}

interface SearchBarProps {
  initialKeyword: string
  onInvalid: (message: string) => void
  // 좁은 화면에서 펼친 검색창: 열리면 바로 입력하고, 검색하면 닫는다
  autoFocus?: boolean
  onSearched?: () => void
  className?: string
}

// SRCH-01·03 통합 검색창
function SearchBar({
  initialKeyword,
  onInvalid,
  autoFocus,
  onSearched,
  className = 'w-full max-w-95',
}: SearchBarProps) {
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState(initialKeyword)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const trimmed = keyword.trim() // SRCH-01 ④
    if (trimmed.length === 0) return // SRCH-03 ②: 공백만 입력하면 아무것도 하지 않는다
    if (trimmed.length < MIN_KEYWORD_LENGTH) {
      onInvalid('검색어는 2글자 이상 입력해주세요') // SRCH-03 ①
      return
    }
    // SRCH-01 ③
    navigate(`/search?keyword=${encodeURIComponent(trimmed)}`)
    onSearched?.()
  }

  return (
    <form role="search" onSubmit={handleSubmit} className={`relative ${className}`}>
      <input
        type="search"
        autoFocus={autoFocus}
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        placeholder="공모전, 모집글, 작성자를 한 번에 검색해 보세요"
        aria-label="통합 검색"
        className="h-10 w-full rounded-input border border-line-input bg-surface pr-12 pl-4 text-small placeholder:text-text-disabled focus:border-primary focus:outline-none"
      />
      <button
        type="submit"
        aria-label="검색"
        className="absolute top-1/2 right-2 flex size-8 -translate-y-1/2 items-center justify-center rounded-full text-text-sub hover:bg-bg hover:text-text"
      >
        <SearchIcon />
      </button>
    </form>
  )
}

// GNB-01 상단 고정 바 (STYLE_GUIDE 4: 높이 72px, 흰 배경, 아래 divider 1px)
// 좁은 화면 (#117, Tailwind 기본 구간): 1024px 미만은 검색창을 돋보기 버튼으로 접고,
// 768px 미만은 메인 메뉴를 메뉴 버튼 하나로 묶는다. 펼친 검색창·메뉴는 상단바 아래에 한 줄로 연다.
export function Gnb() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const isLoggedIn = useIsLoggedIn()
  const toast = useTimedToast()
  const [loginModalOpen, setLoginModalOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  // 검색 결과 화면으로 가거나 뒤로 가면 입력칸도 주소의 검색어에 맞춘다 (key가 바뀌면 새로 그림)
  const keywordParam = useSearchParams()[0].get('keyword') ?? ''

  const closePanels = () => {
    setSearchOpen(false)
    setMenuOpen(false)
  }

  const menuLinks = (variant: 'bar' | 'panel') =>
    MENUS.map((menu) => {
      const isActive =
        pathname === menu.to || menu.activePrefixes.some((p) => pathname.startsWith(p))
      return (
        <Link
          key={menu.to}
          to={menu.to}
          aria-current={isActive ? 'page' : undefined}
          onClick={(event) => {
            setMenuOpen(false)
            if ('requiresLogin' in menu && !isLoggedIn) {
              event.preventDefault()
              setLoginModalOpen(true)
            }
          }}
          className={`text-body font-bold ${isActive ? 'text-primary' : 'text-text hover:text-primary'} ${
            variant === 'panel' ? 'flex h-11 items-center rounded-popover px-4 hover:bg-bg' : ''
          }`}
        >
          {menu.label}
        </Link>
      )
    })

  return (
    <>
      <header className="sticky top-0 z-30 border-b border-divider bg-surface">
        <div className="mx-auto flex h-18 max-w-[1680px] items-center gap-4 px-4 md:gap-8 md:px-6 lg:gap-10 lg:px-12 xl:px-[120px]">
          {/* GNB-01 ②: 로고를 누르면 메인으로 */}
          <Link
            to="/"
            onClick={closePanels}
            className="shrink-0 text-subheading font-bold text-primary"
          >
            공방
          </Link>

          <nav aria-label="메인 메뉴" className="hidden shrink-0 gap-8 md:flex">
            {menuLinks('bar')}
          </nav>

          {/* 768px 미만에서는 닉네임 글자를 숨기고 사진만 둔다 (UserMenu 안의 닉네임 span) */}
          <div className="ml-auto flex min-w-0 flex-1 items-center justify-end gap-2 md:gap-4 max-md:[&_button>span.truncate]:hidden">
            <SearchBar
              key={keywordParam}
              initialKeyword={keywordParam}
              onInvalid={toast.show}
              className="hidden w-full max-w-95 lg:block"
            />
            <button
              type="button"
              aria-label="검색창 열기"
              aria-expanded={searchOpen}
              onClick={() => {
                setSearchOpen((open) => !open)
                setMenuOpen(false)
              }}
              className="flex size-10 shrink-0 cursor-pointer items-center justify-center rounded-full text-text hover:bg-bg lg:hidden"
            >
              <SearchIcon />
            </button>
            {/* GNB-02 ①: 비로그인은 [로그인] 버튼
              TODO(확인 필요): GNB-02 ③④ 닉네임 드롭다운·GNB-03 로그아웃은 인증 모듈(정진우, AUTH-01·11)과 함께 만든다 */}
            {!isLoggedIn && (
              <Button
                variant="outline"
                size="sm"
                className="shrink-0"
                onClick={() => navigate(loginPathWithRedirect(pathname === '/' ? '' : pathname))}
              >
                로그인
              </Button>
            )}
            {/* AUTH-01 ⑦: 로그인 후 [로그인] → 알림 종 + [닉네임 ▾] (정진우 NOTI-03·MY-06 ③·AUTH-11) */}
            {isLoggedIn && (
              <>
                <NotificationBell onError={toast.show} />
                <UserMenu />
              </>
            )}
            <button
              type="button"
              aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'}
              aria-expanded={menuOpen}
              aria-controls="gnb-mobile-menu"
              onClick={() => {
                setMenuOpen((open) => !open)
                setSearchOpen(false)
              }}
              className="flex size-10 shrink-0 cursor-pointer items-center justify-center rounded-full text-text hover:bg-bg md:hidden"
            >
              {menuOpen ? <CloseMenuIcon /> : <MenuIcon />}
            </button>
          </div>
        </div>

        {searchOpen && (
          <div className="border-t border-divider px-4 py-3 md:px-6 lg:hidden">
            <SearchBar
              key={keywordParam}
              initialKeyword={keywordParam}
              onInvalid={toast.show}
              autoFocus
              onSearched={() => setSearchOpen(false)}
              className="w-full"
            />
          </div>
        )}
        {menuOpen && (
          <nav
            id="gnb-mobile-menu"
            aria-label="메인 메뉴"
            className="flex flex-col gap-1 border-t border-divider px-2 py-2 md:hidden"
          >
            {menuLinks('panel')}
          </nav>
        )}
      </header>
      {/* 토스트는 상단바(sticky·z-index) 밖에 둬야 화면 위에 제대로 그려진다 */}
      <Toast message={toast.message} />
      <Modal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        title="로그인이 필요한 서비스입니다"
        size="sm"
        footer={
          <Button
            onClick={() => {
              setLoginModalOpen(false)
              // TODO(확인 필요): 로그인 후 워크스페이스(/workspaces)로 돌아오게 한다 (이상현 제안).
              // 돌아갈 주소를 넘기는 방식은 로그인 화면(정진우, AUTH-01)이 정해지면 맞춘다
              // → 11/16 정진우: /login?redirect=/workspaces 로 넘긴다
              navigate(loginPathWithRedirect('/workspaces') || LOGIN_PATH)
            }}
          >
            로그인
          </Button>
        }
      />
    </>
  )
}
