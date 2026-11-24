import { useEffect, useRef, useState } from 'react'
import { ChevronDownIcon, MessageSquareIcon } from '../../../components/icons/Icons'
import { Badge, CountBadge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import type { ChatRoom, RoomSortOrder } from '../types'

interface ChatRoomListProps {
  rooms: ChatRoom[]
  selectedRoomId: number | null
  sortOrder: RoomSortOrder
  isLoading?: boolean
  isError?: boolean
  onRetry?: () => void
  onChangeSortOrder: (sort: RoomSortOrder) => void
  onSelectRoom: (roomId: number) => void
}

const SORT_LABELS: Record<RoomSortOrder, string> = {
  latest: '최신순 정렬',
  unread: '안 읽은 순',
}

/**
 * STYLE_GUIDE.md 4장·5-5·5-7 좌측 참여 중인 채팅방 목록 패널 (너비 340~400px).
 * 1024px 미만은 메신저 앱 목록처럼 테두리 없이 화면 끝까지 붙이고, 방은 구분선으로 나눈 줄로 보여 준다.
 */
export function ChatRoomList({
  rooms,
  selectedRoomId,
  sortOrder,
  isLoading = false,
  isError = false,
  onRetry,
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
      className="flex w-full flex-1 flex-col bg-surface lg:w-[360px] lg:flex-none lg:shrink-0 lg:rounded-card lg:border lg:border-line lg:p-5"
    >
      <div className="flex items-center justify-between gap-2 border-b border-divider px-4 py-3 lg:mb-4 lg:border-b-0 lg:p-0">
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
      ) : isError && rooms.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-12 text-center">
          <MessageSquareIcon className="h-12 w-12 text-text-disabled" />
          <p className="text-body text-text-sub">채팅방 목록을 불러오지 못했어요</p>
          {onRetry && (
            <Button variant="outline" size="sm" onClick={onRetry}>
              다시 시도
            </Button>
          )}
        </div>
      ) : rooms.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-12 text-center">
          <MessageSquareIcon className="h-12 w-12 text-text-disabled" />
          <p className="text-body text-text-sub">참여 중인 채팅방이 없습니다</p>
        </div>
      ) : (
        <ul className="flex flex-1 flex-col overflow-y-auto lg:gap-3">
          {rooms.map((room) => {
            const isSelected = room.id === selectedRoomId
            return (
              <li key={room.id}>
                <button
                  type="button"
                  onClick={() => onSelectRoom(room.id)}
                  className={[
                    'flex w-full cursor-pointer flex-col gap-1.5 border-b border-divider bg-surface px-4 py-3 text-left transition-colors hover:bg-bg lg:gap-2 lg:rounded-popover lg:px-5 lg:py-4',
                    // 선택 표시는 목록과 방이 나란히 보이는 1024px 이상에서만
                    isSelected ? 'lg:border-b-0 lg:bg-primary-soft' : 'lg:border lg:border-line',
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
