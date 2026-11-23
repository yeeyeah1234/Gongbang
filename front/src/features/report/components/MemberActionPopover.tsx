import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ChevronDownIcon, FlagIcon, UserIcon } from '../../../components/icons/Icons'
import { useIsLoggedIn, useLoginMember } from '../../auth/store'
import { LoginRequiredModal } from '../../post/components/LoginRequiredModal'
import { ReportModal } from './ReportModal'

interface MemberActionPopoverProps {
  memberId: number | null
  nickname: string
  children: ReactNode
  redirectTo: string
  onReportSuccess?: (message: string) => void
}

/**
 * NET-02 작성자 프로필/닉네임 클릭 드롭다운 메뉴 (프로필 보기 · 신고하기).
 * 본인 또는 탈퇴한 사용자(`memberId === null`)는 드롭다운을 열지 않는다.
 *
 * TODO(확인 필요): 팀 초대(`team_invitation`) API가 구현되면 초대 메뉴 항목을 추가한다.
 */
export function MemberActionPopover({
  memberId,
  nickname,
  children,
  redirectTo,
  onReportSuccess,
}: MemberActionPopoverProps) {
  const isLoggedIn = useIsLoggedIn()
  const loginMember = useLoginMember()
  const [menuOpen, setMenuOpen] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const [loginModalOpen, setLoginModalOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement | null>(null)

  const isSelf = memberId !== null && loginMember?.memberId === memberId
  const canOpenMenu = memberId !== null && !isSelf && nickname !== '탈퇴한 사용자'

  useEffect(() => {
    if (!menuOpen) return
    const handleOutsideClick = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setMenuOpen(false)
      }
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleOutsideClick)
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick)
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [menuOpen])

  if (!canOpenMenu) {
    return <span className="inline-flex items-center gap-2 font-bold text-text">{children}</span>
  }

  const handleOpenReport = () => {
    setMenuOpen(false)
    if (!isLoggedIn) {
      setLoginModalOpen(true)
      return
    }
    setReportOpen(true)
  }

  return (
    <>
      <div ref={containerRef} className="relative inline-block">
        <button
          type="button"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((prev) => !prev)}
          className="inline-flex cursor-pointer items-center gap-1.5 rounded-full py-0.5 pr-1 font-bold text-text transition-colors hover:text-primary"
        >
          {children}
          <ChevronDownIcon
            className={[
              'size-4 text-text-sub transition-transform',
              menuOpen ? 'rotate-180' : '',
            ].join(' ')}
          />
        </button>

        {menuOpen && (
          <ul
            role="menu"
            aria-label={`${nickname} 메뉴`}
            className="absolute top-full left-0 z-30 mt-2 w-[176px] rounded-popover border border-line bg-surface py-1.5 shadow-pop"
          >
            <li role="none">
              <Link
                role="menuitem"
                to={`/members/${memberId}`}
                onClick={() => setMenuOpen(false)}
                className="flex h-10 items-center gap-2.5 px-4 text-small font-medium text-text transition-colors hover:bg-bg"
              >
                <UserIcon className="size-4 text-text-sub" />
                <span>프로필 보기</span>
              </Link>
            </li>
            <li role="none" className="mt-1 border-t border-divider pt-1">
              <button
                type="button"
                role="menuitem"
                onClick={handleOpenReport}
                className="flex h-10 w-full cursor-pointer items-center gap-2.5 px-4 text-left text-small font-medium text-danger transition-colors hover:bg-danger-soft"
              >
                <FlagIcon className="size-4" />
                <span>신고하기</span>
              </button>
            </li>
          </ul>
        )}
      </div>

      <ReportModal
        open={reportOpen}
        reportedMemberId={memberId}
        targetNickname={nickname}
        targetType="MEMBER"
        onClose={() => setReportOpen(false)}
        onSubmitted={onReportSuccess}
      />

      <LoginRequiredModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        redirectTo={redirectTo}
      />
    </>
  )
}
