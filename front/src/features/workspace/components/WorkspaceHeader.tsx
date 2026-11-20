import { ArrowLeftIcon } from '../../../components/icons/Icons'
import { Avatar } from '../../../components/ui/Avatar'
import { Badge } from '../../../components/ui/Badge'
import { getDeadlineBadge } from '../deadline'
import type { WorkspaceOnlineMember } from '../types'

// 아바타는 최대 5개, 나머지는 「+N」
const MAX_AVATARS = 5

interface WorkspaceHeaderProps {
  teamName: string
  contestTitle: string | null
  deadline: string | null
  onlineMembers: WorkspaceOnlineMember[]
  onBack: () => void
  // 미리보기·테스트용. 기본은 오늘
  today?: Date
}

// WS-02 워크스페이스 헤더 (높이 96, 흰 배경, 아래 divider)
export function WorkspaceHeader({
  teamName,
  contestTitle,
  deadline,
  onlineMembers,
  onBack,
  today,
}: WorkspaceHeaderProps) {
  const badge = getDeadlineBadge(deadline, today)
  const shown = onlineMembers.slice(0, MAX_AVATARS)
  const hiddenCount = onlineMembers.length - shown.length
  const title = contestTitle ? `${contestTitle} · ${teamName}` : teamName

  return (
    <header className="h-24 shrink-0 border-b border-divider bg-surface">
      <div className="mx-auto flex h-full max-w-[1680px] items-center justify-between gap-4 px-6 lg:px-12 xl:px-[120px]">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            onClick={onBack}
            aria-label="워크스페이스 목록"
            title="워크스페이스 목록"
            className="flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full border border-line-strong bg-surface text-text transition-colors hover:bg-bg"
          >
            <ArrowLeftIcon className="h-5 w-5" />
          </button>
          <h1 className="truncate text-heading font-bold text-text" title={title}>
            {title}
          </h1>
          {/* D-7부터 danger-soft, 그 전과 「종료」는 surface-sub */}
          {badge && (
            <Badge variant={badge.tone === 'danger' ? 'danger-soft' : 'surface-sub'}>
              {badge.label}
            </Badge>
          )}
        </div>

        <div className="flex shrink-0 items-center gap-3">
          <span className="hidden text-small text-text-sub sm:inline">
            접속 중 {onlineMembers.length}명
          </span>
          {shown.length > 0 && (
            <ul className="flex items-center gap-2" aria-label="접속 중인 팀원">
              {shown.map((member) => (
                <li key={member.memberId}>
                  <Avatar
                    name={member.nickname}
                    colorIndex={member.colorIndex}
                    size="md"
                    isLeader={member.isLeader}
                  />
                </li>
              ))}
              {hiddenCount > 0 && (
                <li
                  className="flex h-10 min-w-10 items-center justify-center rounded-full bg-surface-sub px-2 text-small font-bold text-text-sub"
                  aria-label={`외 ${hiddenCount}명`}
                >
                  +{hiddenCount}
                </li>
              )}
            </ul>
          )}
        </div>
      </div>
    </header>
  )
}
