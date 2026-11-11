import { CountBadge } from '../../../components/ui/Badge'
import type { WorkspaceTabKey } from '../types'

interface WorkspaceTabsProps {
  activeTab: WorkspaceTabKey
  chatUnreadCount: number
  noticeUnreadCount: number
  onSelectTab: (tab: WorkspaceTabKey) => void
}

const TAB_ITEMS: Array<{ key: WorkspaceTabKey; label: string }> = [
  { key: 'chat', label: '채팅' },
  { key: 'document', label: '문서' },
  { key: 'whiteboard', label: '화이트보드' },
  { key: 'calendar', label: '캘린더' },
  { key: 'notice', label: '공지' },
]

/** STYLE_GUIDE.md 4장·5-4 워크스페이스 탭 바 (높이 72px, 글자 20px, 간격 40px) */
export function WorkspaceTabs({
  activeTab,
  chatUnreadCount,
  noticeUnreadCount,
  onSelectTab,
}: WorkspaceTabsProps) {
  const getUnreadCount = (key: WorkspaceTabKey) => {
    if (key === 'chat') return chatUnreadCount
    if (key === 'notice') return noticeUnreadCount
    return 0
  }

  return (
    <nav
      aria-label="워크스페이스 탭"
      className="h-[72px] shrink-0 border-b border-divider bg-surface"
    >
      <div className="mx-auto flex h-full max-w-[1680px] items-end gap-10 overflow-x-auto px-6 lg:px-12 xl:px-[120px]">
        {TAB_ITEMS.map((tab) => {
          const isActive = activeTab === tab.key
          const unreadCount = getUnreadCount(tab.key)

          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => onSelectTab(tab.key)}
              aria-current={isActive ? 'page' : undefined}
              className={[
                'inline-flex h-[52px] shrink-0 cursor-pointer items-center gap-2 border-b-2 text-subheading whitespace-nowrap transition-colors',
                isActive
                  ? 'border-primary font-bold text-text'
                  : 'border-transparent font-normal text-text-sub hover:text-text',
              ].join(' ')}
            >
              <span>{tab.label}</span>
              <CountBadge count={unreadCount} />
            </button>
          )
        })}
      </div>
    </nav>
  )
}
