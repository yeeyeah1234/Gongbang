import { useEffect, useRef, useState } from 'react'
import { CrownIcon, MoreHorizontalIcon, PlusIcon } from '../../../components/icons/Icons'
import { Avatar } from '../../../components/ui/Avatar'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { Drawer } from '../../../components/ui/Drawer'
import type { ChatRoom, TeamMember } from '../types'

interface RoomSettingsDrawerProps {
  open: boolean
  room: ChatRoom | undefined
  members: TeamMember[]
  currentUserId: number
  onClose: () => void
  onStartDirectChat: (member: TeamMember) => void
  onRequestKickMember: (member: TeamMember) => void
  onRequestInviteMember: () => void
  onRequestDeleteRoom: () => void
}

/** STYLE_GUIDE.md 5-7 우측 방 설정 / 팀원 관리 서랍 */
export function RoomSettingsDrawer({
  open,
  room,
  members,
  currentUserId,
  onClose,
  onStartDirectChat,
  onRequestKickMember,
  onRequestInviteMember,
  onRequestDeleteRoom,
}: RoomSettingsDrawerProps) {
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null)
  const [openMenuMemberId, setOpenMenuMemberId] = useState<number | null>(null)
  const menuContainerRef = useRef<HTMLDivElement | null>(null)

  const currentUser = members.find((member) => member.id === currentUserId)
  const isLeader = currentUser?.role === 'LEADER'

  const roomMembers = room
    ? members.filter((member) => room.memberIds.includes(member.id))
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

                  {isMemberLeader ? (
                    <Badge variant="primary-soft">팀장</Badge>
                  ) : (
                    !isSelf && (
                      <button
                        type="button"
                        aria-label={`${member.name} 메뉴`}
                        onClick={(event) => {
                          event.stopPropagation()
                          setSelectedMemberId(member.id)
                          setOpenMenuMemberId((prev) => (prev === member.id ? null : member.id))
                        }}
                        className="flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-full text-text-sub transition-colors hover:bg-bg hover:text-text"
                      >
                        <MoreHorizontalIcon className="h-5 w-5" />
                      </button>
                    )
                  )}
                </div>

                {isMenuOpen && (
                  <div
                    role="menu"
                    className="absolute top-14 right-2 z-30 w-[180px] rounded-popover border border-line bg-surface py-2 shadow-pop"
                  >
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        setOpenMenuMemberId(null)
                        onStartDirectChat(member)
                      }}
                      className="flex h-11 w-full cursor-pointer items-center px-4 text-left text-body text-text hover:bg-bg"
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
                        className="flex h-11 w-full cursor-pointer items-center px-4 text-left text-body text-danger hover:bg-bg"
                      >
                        강제 퇴장
                      </button>
                    )}
                  </div>
                )}
              </li>
            )
          })}
        </ul>

        {isLeader && (
          <>
            <hr className="my-5 border-t border-divider" />

            <div className="space-y-3">
              <h3 className="text-body font-bold text-text">팀장 전용 관리 메뉴</h3>
              <Button
                variant="outline"
                size="md"
                onClick={onRequestInviteMember}
                className="w-full"
              >
                <PlusIcon className="h-4 w-4" />
                <span>새 팀원 초대하기</span>
              </Button>
              <Button variant="danger" size="md" onClick={onRequestDeleteRoom} className="w-full">
                채팅방 삭제 (팀장 전용)
              </Button>
            </div>
          </>
        )}
      </div>
    </Drawer>
  )
}
