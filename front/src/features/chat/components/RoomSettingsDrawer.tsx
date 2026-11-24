import { useEffect, useRef, useState } from 'react'
import { CrownIcon, MoreHorizontalIcon } from '../../../components/icons/Icons'
import { Avatar } from '../../../components/ui/Avatar'
import { Badge } from '../../../components/ui/Badge'
import { Drawer } from '../../../components/ui/Drawer'
import type { ChatRoom, TeamMember } from '../types'

interface RoomSettingsDrawerProps {
  open: boolean
  room: ChatRoom | undefined
  members: TeamMember[]
  currentUserId: number
  isLeader: boolean
  onClose: () => void
  onRequestDelegateLeader: (member: TeamMember) => void
  onStartDirectChat: (member: TeamMember) => void
  onRequestKickMember: (member: TeamMember) => void
  onRequestReportMember: (member: TeamMember) => void
}

/** STYLE_GUIDE.md 5-7 우측 방 설정 / 팀원 관리 서랍 */
export function RoomSettingsDrawer({
  open,
  room,
  members,
  currentUserId,
  isLeader,
  onClose,
  onRequestDelegateLeader,
  onStartDirectChat,
  onRequestKickMember,
  onRequestReportMember,
}: RoomSettingsDrawerProps) {
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null)
  const [openMenuMemberId, setOpenMenuMemberId] = useState<number | null>(null)
  const menuContainerRef = useRef<HTMLDivElement | null>(null)

  const roomMembers =
    room?.type === 'DIRECT' && room.partnerId !== null
      ? members.filter((member) => member.id === currentUserId || member.id === room.partnerId)
      : members

  useEffect(() => {
    if (openMenuMemberId === null) return
    const handleOutside = (event: MouseEvent) => {
      if (menuContainerRef.current && !menuContainerRef.current.contains(event.target as Node)) {
        setOpenMenuMemberId(null)
      }
    }
    document.addEventListener('mousedown', handleOutside)
    return () => document.removeEventListener('mousedown', handleOutside)
  }, [openMenuMemberId])

  return (
    <Drawer open={open} title={`참여자 목록 (${roomMembers.length}명)`} onClose={onClose}>
      <div ref={menuContainerRef} className="flex flex-1 flex-col">
        <ul className="space-y-3">
          {roomMembers.map((member) => {
            const isSelf = member.id === currentUserId
            const isMemberLeader = member.role === 'LEADER'
            const isSelected = selectedMemberId === member.id
            const isMenuOpen = openMenuMemberId === member.id

            return (
              <li key={member.id} className="relative">
                <div
                  onClick={() => setSelectedMemberId(member.id)}
                  className={[
                    'flex cursor-pointer items-center justify-between gap-3 rounded-popover p-3 transition-colors',
                    isMemberLeader
                      ? 'border border-primary/20 bg-primary-soft'
                      : isSelected
                        ? 'border-2 border-primary bg-surface'
                        : 'border border-line bg-surface hover:bg-bg',
                  ].join(' ')}
                >
                  <div className="flex min-w-0 items-center gap-3">
                    <Avatar
                      name={member.name}
                      colorIndex={member.colorIndex}
                      size="md"
                      isLeader={isMemberLeader}
                    />
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        {isMemberLeader && <CrownIcon className="h-4 w-4 shrink-0 text-warning" />}
                        <span className="truncate text-body font-bold text-text">
                          {member.name}
                          {isSelf ? ' (나)' : ''}
                        </span>
                      </div>
                      <p className="truncate text-small text-text-sub">
                        {isMemberLeader ? '팀장' : '팀원'} · {member.department}
                      </p>
                    </div>
                  </div>

                  <div className="flex shrink-0 items-center gap-1.5 self-start">
                    {isMemberLeader && <Badge variant="primary-soft">팀장</Badge>}
                    {!isSelf && (
                      <button
                        type="button"
                        aria-label={`${member.name} 메뉴`}
                        aria-expanded={isMenuOpen}
                        onClick={(event) => {
                          event.stopPropagation()
                          setSelectedMemberId(member.id)
                          setOpenMenuMemberId((prev) => (prev === member.id ? null : member.id))
                        }}
                        className="-mt-1 -mr-1 flex h-6 w-9 shrink-0 cursor-pointer items-center justify-center rounded-full text-text-sub transition-colors hover:bg-bg hover:text-text"
                      >
                        <MoreHorizontalIcon className="h-5 w-5" />
                      </button>
                    )}
                  </div>
                </div>

                {isMenuOpen && (
                  <div
                    role="menu"
                    className="absolute top-[22px] right-0 z-30 w-[210px] rounded-[10px] border border-line bg-surface p-1.5 shadow-pop"
                  >
                    {isLeader && (
                      <button
                        type="button"
                        role="menuitem"
                        onClick={() => {
                          setOpenMenuMemberId(null)
                          onRequestDelegateLeader(member)
                        }}
                        className="flex h-[38px] w-full cursor-pointer items-center rounded-[6px] px-[13px] text-left text-[14px] leading-[17px] text-text transition-colors hover:bg-surface-sub"
                      >
                        팀장 권한 위임하기
                      </button>
                    )}
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        setOpenMenuMemberId(null)
                        onStartDirectChat(member)
                      }}
                      className="flex h-[38px] w-full cursor-pointer items-center rounded-[6px] px-[13px] text-left text-[14px] leading-[17px] text-text transition-colors hover:bg-surface-sub"
                    >
                      1:1 채팅하기
                    </button>
                    {isLeader && (
                      <button
                        type="button"
                        role="menuitem"
                        onClick={() => {
                          setOpenMenuMemberId(null)
                          onRequestKickMember(member)
                        }}
                        className="flex h-[38px] w-full cursor-pointer items-center rounded-[6px] px-[13px] text-left text-[14px] leading-[17px] text-danger transition-colors hover:bg-surface-sub"
                      >
                        팀원 강제 퇴장
                      </button>
                    )}
                    <hr className="my-1 border-t border-divider" />
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        setOpenMenuMemberId(null)
                        onRequestReportMember(member)
                      }}
                      className="flex h-[38px] w-full cursor-pointer items-center rounded-[6px] px-[13px] text-left text-[14px] leading-[17px] text-danger transition-colors hover:bg-surface-sub"
                    >
                      신고하기
                    </button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      </div>
    </Drawer>
  )
}
