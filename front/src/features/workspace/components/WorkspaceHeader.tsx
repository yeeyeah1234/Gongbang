import { ArrowLeftIcon } from '../../../components/icons/Icons'
import { Badge } from '../../../components/ui/Badge'
import { getDeadlineBadge } from '../deadline'
import type { WorkspaceOnlineMember } from '../types'
import { WorkspacePresenceAvatars } from './WorkspacePresenceAvatars'

interface WorkspaceHeaderProps {
  teamName: string
  contestTitle: string | null
  deadline: string | null
  // 로그인한 나. 항상 맨 앞에 그리고 「접속 중」에 센다 (null이면 그리지 않음)
  self: WorkspaceOnlineMember | null
  // 실시간 접속자. 나를 넣어도 빼도 된다 (memberId로 중복 제거)
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
  self,
  onlineMembers,
  onBack,
  today,
}: WorkspaceHeaderProps) {
  const badge = getDeadlineBadge(deadline, today)
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

        <WorkspacePresenceAvatars self={self} onlineMembers={onlineMembers} />
      </div>
    </header>
  )
}
