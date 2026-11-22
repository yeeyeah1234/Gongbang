import { ArrowLeftIcon } from '../../../components/icons/Icons'
import { Avatar } from '../../../components/ui/Avatar'
import { Badge } from '../../../components/ui/Badge'
import { getDeadlineBadge } from '../deadline'
import { withSelfFirst } from '../onlineMembers'
import type { WorkspaceOnlineMember } from '../types'

// 아바타는 최대 5개(나 포함), 나머지는 「+N」
const MAX_AVATARS = 5

// "(나) 닉네임", 팀장이면 "(나) [팀장] 닉네임"
function selfLabel(self: WorkspaceOnlineMember) {
  return self.isLeader ? `(나) [팀장] ${self.nickname}` : `(나) ${self.nickname}`
}

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
  const members = withSelfFirst(self, onlineMembers)
  const shown = members.slice(0, MAX_AVATARS)
  const hiddenCount = members.length - shown.length
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
            접속 중 {members.length}명
          </span>
          {shown.length > 0 && (
            <ul className="flex items-center gap-2" aria-label="접속 중인 팀원">
              {shown.map((member) => {
                if (self && member.memberId === self.memberId) {
                  const label = selfLabel(member)
                  // TODO(필요함): Avatar(components/ui)에 title·aria-label prop이 없어 name에 라벨을 넘긴다.
                  // Avatar는 name 끝 두 글자를 그리므로 화면 글자는 닉네임 그대로, title만 라벨이 된다.
                  return (
                    <li key={member.memberId}>
                      <span role="img" aria-label={label} className="inline-flex">
                        <Avatar
                          name={label}
                          colorIndex={member.colorIndex}
                          size="md"
                          isLeader={member.isLeader}
                        />
                      </span>
                    </li>
                  )
                }
                return (
                  <li key={member.memberId}>
                    <Avatar
                      name={member.nickname}
                      colorIndex={member.colorIndex}
                      size="md"
                      isLeader={member.isLeader}
                    />
                  </li>
                )
              })}
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
