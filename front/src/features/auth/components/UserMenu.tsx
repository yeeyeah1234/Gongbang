import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ChevronDownIcon } from '../../../components/icons/Icons'
import { ProfilePhoto } from '../../member/components/ProfilePhoto'
import { signOut } from '../session'
import { useLoginMember } from '../store'

// MY-06 ③: GNB 드롭다운 [마이페이지] [북마크] [내 글 관리] [설정] → 각 메뉴가 바로 열린다. AUTH-11 ① [로그아웃]
const ITEMS = [
  { to: '/mypage/profile', label: '마이페이지' },
  { to: '/mypage/bookmarks', label: '북마크' },
  { to: '/mypage/posts', label: '내 글 관리' },
  { to: '/mypage/settings', label: '설정' },
]

export function UserMenu() {
  const navigate = useNavigate()
  const member = useLoginMember()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    const esc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', esc)
    }
  }, [open])

  const handleLogout = async () => {
    setOpen(false)
    await signOut()
    // AUTH-11 ②: 비로그인 메인으로
    navigate('/', { replace: true })
  }

  const nickname = member?.nickname ?? ''

  return (
    <div ref={ref} className="relative shrink-0">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="flex h-10 cursor-pointer items-center gap-2 rounded-full pr-2 pl-1 hover:bg-bg"
      >
        <ProfilePhoto url={member?.profileImageUrl ?? null} name={nickname} size="md" />
        <span className="max-w-24 truncate text-body font-bold text-text">{nickname}</span>
        <ChevronDownIcon />
      </button>
      {open && (
        <ul
          role="menu"
          className="absolute right-0 z-40 mt-2 w-[200px] rounded-popover border border-line bg-surface py-2 shadow-pop"
        >
          {ITEMS.map((item) => (
            <li key={item.to} role="none">
              <Link
                role="menuitem"
                to={item.to}
                onClick={() => setOpen(false)}
                className="flex h-11 items-center px-4 text-body text-text hover:bg-bg"
              >
                {item.label}
              </Link>
            </li>
          ))}
          <li role="none" className="mt-1 border-t border-divider pt-1">
            <button
              type="button"
              role="menuitem"
              onClick={handleLogout}
              className="flex h-11 w-full cursor-pointer items-center px-4 text-body text-text hover:bg-bg"
            >
              로그아웃
            </button>
          </li>
        </ul>
      )}
    </div>
  )
}
