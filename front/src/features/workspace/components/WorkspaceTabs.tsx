import { NavLink } from 'react-router'
import { PinIcon } from '../../../components/icons/Icons'
import { CountBadge } from '../../../components/ui/Badge'
import { WORKSPACE_TABS, type WorkspaceTabKey } from '../types'

interface WorkspaceTabsProps {
  // 링크 앞부분. 보통 /workspaces/:teamId
  basePath: string
  chatUnreadCount: number
  noticeUnreadCount: number
  // 고정 공지 배너가 접혀 있을 때만 넘긴다. 있으면 오른쪽 끝에 「고정 공지 펼치기」 버튼이 보인다
  onExpandPinned?: () => void
}

// WS-02 탭 바 (높이 72, 글자 20, 간격 40). 현재 탭은 URL로 정한다 (NavLink가 aria-current="page"를 붙임)
export function WorkspaceTabs({
  basePath,
  chatUnreadCount,
  noticeUnreadCount,
  onExpandPinned,
}: WorkspaceTabsProps) {
  const unreadOf = (key: WorkspaceTabKey) =>
    key === 'chat' ? chatUnreadCount : key === 'notice' ? noticeUnreadCount : 0

  return (
    <nav
      aria-label="워크스페이스 탭"
      className="h-14 shrink-0 border-b border-divider bg-surface sm:h-[72px]"
    >
      <div className="mx-auto flex h-full max-w-[1680px] items-center gap-3 px-4 sm:gap-4 sm:px-6 lg:px-12 xl:px-[120px]">
        {/* 탭이 넘치면 탭 목록만 가로로 스크롤(스크롤바 숨김)되고, 펼치기 버튼은 오른쪽 끝에 남는다 */}
        <div className="flex h-full min-w-0 flex-1 [scrollbar-width:none] items-end gap-5 overflow-x-auto sm:gap-7 lg:gap-10 [&::-webkit-scrollbar]:hidden">
          {WORKSPACE_TABS.map((tab) => (
            <NavLink
              key={tab.key}
              to={`${basePath}/${tab.key}`}
              className={({ isActive }) =>
                [
                  'inline-flex h-11 shrink-0 items-center gap-1.5 border-b-2 text-body whitespace-nowrap transition-colors sm:h-[52px] sm:gap-2 sm:text-subheading',
                  isActive
                    ? 'border-primary font-bold text-text'
                    : 'border-transparent font-normal text-text-sub hover:text-text',
                ].join(' ')
              }
            >
              <span>{tab.label}</span>
              <CountBadge count={unreadOf(tab.key)} />
            </NavLink>
          ))}
        </div>
        {onExpandPinned && (
          <button
            type="button"
            onClick={onExpandPinned}
            aria-label="고정 공지 펼치기"
            title="고정 공지 펼치기"
            aria-expanded={false}
            className="flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-full bg-primary-soft text-primary transition-colors hover:bg-primary hover:text-surface sm:h-10 sm:w-10"
          >
            <PinIcon className="h-5 w-5" />
          </button>
        )}
      </div>
    </nav>
  )
}
