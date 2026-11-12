import { ArrowLeftIcon } from '../../../components/icons/Icons'
import { Avatar } from '../../../components/ui/Avatar'
import { Badge } from '../../../components/ui/Badge'
import type { TeamMember, WorkspaceInfo } from '../types'

interface WorkspaceHeaderProps {
  workspace: WorkspaceInfo
  members: TeamMember[]
  onBack: () => void
}

/** STYLE_GUIDE.md 4장 워크스페이스 헤더 (높이 96px, 흰 배경, divider 1px) */
export function WorkspaceHeader({ workspace, members, onBack }: WorkspaceHeaderProps) {
  const onlineMembers = members.filter((member) => member.isOnline)

  const renderDDayBadge = () => {
    if (workspace.isEnded) {
      return <Badge variant="surface-sub">종료</Badge>
    }
    if (workspace.dDay <= 7) {
      return <Badge variant="danger-soft">D-{workspace.dDay}</Badge>
    }
    return <Badge variant="surface-sub">D-{workspace.dDay}</Badge>
  }

  return (
    <header className="h-24 shrink-0 border-b border-divider bg-surface">
      <div className="mx-auto flex h-full max-w-[1680px] items-center justify-between gap-4 px-6 lg:px-12 xl:px-[120px]">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            onClick={onBack}
            aria-label="이전 화면으로 이동"
            className="flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full border border-line-strong bg-surface text-text transition-colors hover:bg-bg"
          >
            <ArrowLeftIcon className="h-5 w-5" />
          </button>
          <h1 className="truncate text-heading font-bold text-text">{workspace.teamName}</h1>
          {renderDDayBadge()}
        </div>

        <div className="flex shrink-0 items-center gap-3">
          <span className="hidden text-small text-text-sub sm:inline">
            접속 중 {onlineMembers.length}명
          </span>
          <div className="flex items-center gap-2">
            {onlineMembers.map((member) => (
              <Avatar
                key={member.id}
                name={member.name}
                colorIndex={member.colorIndex}
                size="md"
                isLeader={member.role === 'LEADER'}
              />
            ))}
          </div>
        </div>
      </div>
    </header>
  )
}

