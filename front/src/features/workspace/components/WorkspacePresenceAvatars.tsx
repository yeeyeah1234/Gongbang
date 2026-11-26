import { Avatar } from '../../../components/ui/Avatar'
import { formatPresenceTooltip, withSelfFirst } from '../onlineMembers'
import type { WorkspaceOnlineMember } from '../types'
import { useIsPhone } from '../useIsPhone'
import { WorkspacePresencePopover } from './WorkspacePresencePopover'

// WS-03 ① 아바타는 최대 5개(나 포함), 넘치면 「+N」
const MAX_AVATARS = 5

interface WorkspacePresenceAvatarsProps {
  self: WorkspaceOnlineMember | null
  onlineMembers: WorkspaceOnlineMember[]
}

/**
 * WS-03 워크스페이스 헤더 접속자 표시:
 * ① 아바타 최대 5개 + 초과 시 「+N」 + 「접속 중 N명」 (휴대폰 <640px은 버튼 + 팝오버)
 * ② 팀장 아바타 오른쪽 위 왕관
 * ③ 팀원 고유 색(8색) 테두리
 * ④ 호버·포커스 시 「[팀장] 닉네임 · 탭 > 문서명」 툴팁
 */
export function WorkspacePresenceAvatars({ self, onlineMembers }: WorkspacePresenceAvatarsProps) {
  const isPhone = useIsPhone()
  const members = withSelfFirst(self, onlineMembers)

  if (isPhone) {
    return <WorkspacePresencePopover self={self} members={members} />
  }

  const shown = members.slice(0, MAX_AVATARS)
  const hiddenMembers = members.slice(MAX_AVATARS)
  const hiddenCount = hiddenMembers.length

  return (
    <div className="flex shrink-0 items-center gap-3">
      <span className="hidden text-small text-text-sub sm:inline">접속 중 {members.length}명</span>
      {shown.length > 0 && (
        <ul className="flex items-center gap-2" aria-label="접속 중인 팀원">
          {shown.map((member) => {
            const isSelf = self !== null && member.memberId === self.memberId
            const tooltip = formatPresenceTooltip(member, isSelf)
            return (
              <li key={member.memberId} className="relative">
                <div
                  tabIndex={0}
                  role="img"
                  aria-label={tooltip}
                  className="group relative inline-flex rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  <Avatar
                    name={member.nickname}
                    colorIndex={member.colorIndex}
                    size="md"
                    isLeader={member.isLeader}
                    title={null}
                  />
                  <span
                    role="tooltip"
                    className="pointer-events-none invisible absolute top-full right-0 z-30 mt-2 rounded-popover border border-line bg-surface px-3 py-1.5 text-caption font-medium whitespace-nowrap text-text opacity-0 shadow-pop transition-opacity group-focus-within:visible group-focus-within:opacity-100 group-hover:visible group-hover:opacity-100"
                  >
                    {tooltip}
                  </span>
                </div>
              </li>
            )
          })}
          {hiddenCount > 0 && (
            <li className="relative">
              <div
                tabIndex={0}
                aria-label={`외 ${hiddenCount}명`}
                className="group relative flex h-10 min-w-10 cursor-default items-center justify-center rounded-full bg-surface-sub px-2 text-small font-bold text-text-sub focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                +{hiddenCount}
                <div
                  role="tooltip"
                  className="pointer-events-none invisible absolute top-full right-0 z-30 mt-2 flex flex-col gap-1 rounded-popover border border-line bg-surface px-3 py-2 text-caption font-medium whitespace-nowrap text-text opacity-0 shadow-pop transition-opacity group-focus-within:visible group-focus-within:opacity-100 group-hover:visible group-hover:opacity-100"
                >
                  {hiddenMembers.map((member) => (
                    <span key={member.memberId}>{formatPresenceTooltip(member, false)}</span>
                  ))}
                </div>
              </div>
            </li>
          )}
        </ul>
      )}
    </div>
  )
}
