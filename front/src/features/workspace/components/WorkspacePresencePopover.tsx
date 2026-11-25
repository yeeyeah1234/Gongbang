import { useEffect, useRef, useState } from 'react'
import { CrownIcon } from '../../../components/icons/Icons'
import type { MemberColorIndex } from '../../../components/ui/Avatar'
import { formatPresenceTooltip } from '../onlineMembers'
import type { WorkspaceOnlineMember } from '../types'
import { UsersIcon } from './WorkspaceIcons'

const MEMBER_DOT_CLASSES: Record<MemberColorIndex, string> = {
  1: 'bg-member-1',
  2: 'bg-member-2',
  3: 'bg-member-3',
  4: 'bg-member-4',
  5: 'bg-member-5',
  6: 'bg-member-6',
  7: 'bg-member-7',
  8: 'bg-member-8',
}

interface WorkspacePresencePopoverProps {
  self: WorkspaceOnlineMember | null
  members: WorkspaceOnlineMember[]
}

/**
 * WS-03 휴대폰(<640px) 헤더 접속자 버튼 + 목록 팝오버.
 * hover가 없는 터치 환경에서도 접속 중인 팀원 전체와 위치를 확인할 수 있다.
 */
export function WorkspacePresencePopover({ self, members }: WorkspacePresencePopoverProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return

    const handlePointerDown = (event: PointerEvent) => {
      if (containerRef.current?.contains(event.target as Node)) return
      setOpen(false)
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        setOpen(false)
        buttonRef.current?.focus()
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  if (members.length === 0) return null

  return (
    <div ref={containerRef} className="relative shrink-0">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-label={`접속 중 ${members.length}명, 목록 보기`}
        aria-haspopup="true"
        aria-expanded={open}
        className="inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-full border border-line-strong bg-surface px-3 text-small font-bold text-text transition-colors hover:bg-bg focus-visible:outline-2 focus-visible:outline-primary"
      >
        <UsersIcon className="h-4 w-4 shrink-0 text-text-sub" />
        <span>{members.length}</span>
      </button>

      {open && (
        <div
          aria-label="접속 중인 팀원"
          className="absolute top-full right-0 z-30 mt-2 w-[min(18rem,calc(100vw-2rem))] rounded-popover border border-line bg-surface py-2 shadow-pop"
        >
          <p className="border-b border-divider px-3.5 pb-2 text-caption font-bold text-text-sub">
            접속 중 {members.length}명
          </p>
          <ul className="max-h-64 overflow-y-auto py-1">
            {members.map((member) => {
              const isSelf = self !== null && member.memberId === self.memberId
              return (
                <li
                  key={member.memberId}
                  className="flex items-start gap-2 px-3.5 py-2 text-small text-text"
                >
                  <span
                    aria-hidden="true"
                    className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${MEMBER_DOT_CLASSES[member.colorIndex]}`}
                  />
                  {member.isLeader && (
                    <CrownIcon className="mt-0.5 h-3.5 w-3.5 shrink-0 text-warning" />
                  )}
                  <span className="min-w-0 flex-1 wrap-anywhere">
                    {formatPresenceTooltip(member, isSelf)}
                  </span>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </div>
  )
}
