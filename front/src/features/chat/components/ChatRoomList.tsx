import { useEffect, useRef, useState } from 'react'
import { ChevronDownIcon, MessageSquareIcon } from '../../../components/icons/Icons'
import { Badge, CountBadge } from '../../../components/ui/Badge'
import type { ChatRoom, RoomSortOrder } from '../types'

interface ChatRoomListProps {
  rooms: ChatRoom[]
  selectedRoomId: number | null
  sortOrder: RoomSortOrder
  isLoading?: boolean
  onChangeSortOrder: (sort: RoomSortOrder) => void
  onSelectRoom: (roomId: number) => void
}

const SORT_LABELS: Record<RoomSortOrder, string> = {
  latest: '최신순 정렬',
  unread: '안 읽은 순',
}

/** STYLE_GUIDE.md 4장·5-5·5-7 좌측 참여 중인 채팅방 목록 패널 (너비 340~400px) */
export function ChatRoomList({
  rooms,
  selectedRoomId,
  sortOrder,
  isLoading = false,
  onChangeSortOrder,
  onSelectRoom,
}: ChatRoomListProps) {
  const [sortMenuOpen, setSortMenuOpen] = useState(false)
  const sortRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!sortMenuOpen) return
    const handleOutsideClick = (event: MouseEvent) => {
      if (sortRef.current && !sortRef.current.contains(event.target as Node)) {
        setSortMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleOutsideClick)
    return () => document.removeEventListener('mousedown', handleOutsideClick)
  }, [sortMenuOpen])

  return (
    <section
      aria-label="참여 중인 채팅방"
      className="flex w-full shrink-0 flex-col rounded-card border border-line bg-surface p-5 lg:w-[360px]"
    >
      <div className="mb-4 flex items-center justify-between gap-2">
        <h2 className="text-subheading font-bold text-text">참여 중인 채팅방</h2>

        <div ref={sortRef} className="relative">
          <button
            type="button"
            onClick={() => setSortMenuOpen((prev) => !prev)}
            aria-expanded={sortMenuOpen}
            className="inline-flex h-11 min-w-[140px] cursor-pointer items-center justify-between gap-2 rounded-full border border-line-strong bg-surface px-4 text-small font-medium text-text transition-colors hover:bg-bg"
          >
            <span>{SORT_LABELS[sortOrder]}</span>
            <ChevronDownIcon className="h-4 w-4 text-text-sub" />
          </button>

          {sortMenuOpen && (
            <div
              role="menu"
              className="absolute right-0 z-20 mt-2 w-[160px] rounded-popover border border-line bg-surface py-2 shadow-pop"
            >
              {(Object.keys(SORT_LABELS) as RoomSortOrder[]).map((order) => (
                <button
                  key={order}
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    onChangeSortOrder(order)
                    setSortMenuOpen(false)
                  }}
                  className={[
                    'flex h-11 w-full cursor-pointer items-center px-4 text-left text-body transition-colors hover:bg-bg',
                    sortOrder === order ? 'font-bold text-primary' : 'text-text',
                  ].join(' ')}
                >
                  {SORT_LABELS[order]}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {isLoading && rooms.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center py-12 text-center">
          <p className="text-body text-text-sub">채팅방을 불러오고 있어요…</p>
        </div>
      ) : rooms.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-12 text-center">
          <MessageSquareIcon className="h-12 w-12 text-text-disabled" />
          <p className="text-body text-text-sub">참여 중인 채팅방이 없습니다</p>
        </div>
      ) : (
        <ul className="flex flex-1 flex-col gap-3 overflow-y-auto">
          {rooms.map((room) => {
            const isSelected = room.id === selectedRoomId
            return (
              <li key={room.id}>
                <button
                  type="button"
                  onClick={() => onSelectRoom(room.id)}
                  className={[
                    'flex w-full cursor-pointer flex-col gap-2 rounded-popover px-5 py-4 text-left transition-colors',
                    isSelected ? 'bg-primary-soft' : 'border border-line bg-surface hover:bg-bg',
                    room.isEnded ? 'opacity-50' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <Badge variant={room.type === 'TEAM' ? 'primary' : 'surface-sub'}>
                        {room.type === 'TEAM' ? '팀채팅' : '개인채팅'}
                      </Badge>
                      {room.isEnded && <Badge variant="surface-sub">종료</Badge>}
                    </div>
                    <span className="text-small text-text-sub">{room.lastMessageAt}</span>
                  </div>

                  <p className="truncate text-body font-bold text-text">{room.name}</p>

                  <div className="flex items-center justify-between gap-3">
                    <p className="truncate text-small text-text-sub">{room.lastMessage}</p>
                    <CountBadge count={room.unreadCount} />
                  </div>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
