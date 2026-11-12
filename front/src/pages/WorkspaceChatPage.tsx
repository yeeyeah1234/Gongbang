import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import type { MemberColorIndex } from '../components/ui/Avatar'
import { Button } from '../components/ui/Button'
import { Toast } from '../components/ui/Toast'
import {
  DeleteRoomModal,
  InviteMemberModal,
  KickMemberModal,
} from '../features/chat/components/ChatModals'
import { ChatRoomList } from '../features/chat/components/ChatRoomList'
import { ChatTimeline } from '../features/chat/components/ChatTimeline'
import { PinnedNoticeBanner } from '../features/chat/components/PinnedNoticeBanner'
import { RoomSettingsDrawer } from '../features/chat/components/RoomSettingsDrawer'
import { WorkspaceHeader } from '../features/chat/components/WorkspaceHeader'
import { WorkspaceTabs } from '../features/chat/components/WorkspaceTabs'
import {
  CURRENT_USER_ID,
  INITIAL_MEMBERS,
  INITIAL_MESSAGES,
  INITIAL_ROOMS,
  INITIAL_WORKSPACE_INFO,
} from '../features/chat/mockData'
import type {
  ChatMessage,
  ChatRoom,
  RoomSortOrder,
  TeamMember,
  WorkspaceTabKey,
} from '../features/chat/types'

const TAB_LABELS: Record<WorkspaceTabKey, string> = {
  chat: '채팅',
  document: '문서',
  whiteboard: '화이트보드',
  calendar: '캘린더',
  notice: '공지',
}

function formatCurrentTimeLabel(): string {
  const now = new Date()
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

function formatCurrentDateLabel(): string {
  const now = new Date()
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(now)
}

/**
 * 워크스페이스 채팅 화면 (F-NET-06 / WS-02)
 * TODO(확인 필요): 백엔드 REST API 및 실시간 WebSocket 연동 전까지 목업 데이터와 로컬 상태로 동작
 */
export default function WorkspaceChatPage() {
  const navigate = useNavigate()
  const { teamId } = useParams<{ teamId: string }>()

  const workspace = useMemo(
    () => ({
      ...INITIAL_WORKSPACE_INFO,
      teamId: teamId ?? INITIAL_WORKSPACE_INFO.teamId,
    }),
    [teamId],
  )

  const [activeTab, setActiveTab] = useState<WorkspaceTabKey>('chat')
  const [noticeCollapsed, setNoticeCollapsed] = useState(false)
  const [showAutoAlert, setShowAutoAlert] = useState(true)

  const [members, setMembers] = useState<TeamMember[]>(INITIAL_MEMBERS)
  const [rooms, setRooms] = useState<ChatRoom[]>(INITIAL_ROOMS)
  const [messagesByRoom, setMessagesByRoom] =
    useState<Record<string, ChatMessage[]>>(INITIAL_MESSAGES)
  const [selectedRoomId, setSelectedRoomId] = useState<string>(INITIAL_ROOMS[0]?.id ?? '')
  const [sortOrder, setSortOrder] = useState<RoomSortOrder>('latest')

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [kickTarget, setKickTarget] = useState<TeamMember | null>(null)
  const [deleteRoomOpen, setDeleteRoomOpen] = useState(false)

  const [toastMessage, setToastMessage] = useState<string | null>(null)
  const toastTimerRef = useRef<number | null>(null)

  const showToast = (message: string) => {
    setToastMessage(message)
    if (toastTimerRef.current !== null) {
      window.clearTimeout(toastTimerRef.current)
    }
    toastTimerRef.current = window.setTimeout(() => {
      setToastMessage(null)
    }, 2500)
  }

  useEffect(() => {
    document.title = `${TAB_LABELS[activeTab]} | 공방`
  }, [activeTab])

  useEffect(() => {
    return () => {
      if (toastTimerRef.current !== null) {
        window.clearTimeout(toastTimerRef.current)
      }
    }
  }, [])

  const sortedRooms = useMemo(() => {
    const copy = [...rooms]
    if (sortOrder === 'unread') {
      copy.sort((a, b) => {
        if (b.unreadCount !== a.unreadCount) return b.unreadCount - a.unreadCount
        return b.lastActivityOrder - a.lastActivityOrder
      })
    } else {
      copy.sort((a, b) => b.lastActivityOrder - a.lastActivityOrder)
    }
    return copy
  }, [rooms, sortOrder])

  const selectedRoom = rooms.find((room) => room.id === selectedRoomId) ?? sortedRooms[0]
  const currentMessages = selectedRoom ? (messagesByRoom[selectedRoom.id] ?? []) : []
  const totalUnreadChat = rooms.reduce((sum, room) => sum + room.unreadCount, 0)

  const handleSelectRoom = (roomId: string) => {
    setSelectedRoomId(roomId)
    setRooms((prev) =>
      prev.map((room) => (room.id === roomId ? { ...room, unreadCount: 0 } : room)),
    )
  }

  const handleSendMessage = (payload: {
    content: string
    imageUrl?: string
    imageName?: string
  }) => {
    if (!selectedRoom) return
    const timeLabel = formatCurrentTimeLabel()
    const dateLabel = formatCurrentDateLabel()
    const newMessage: ChatMessage = {
      id: `msg-${Date.now()}`,
      roomId: selectedRoom.id,
      senderId: CURRENT_USER_ID,
      content: payload.content,
      imageUrl: payload.imageUrl,
      imageName: payload.imageName,
      dateLabel,
      timeLabel,
      isRead: true,
    }

    setMessagesByRoom((prev) => ({
      ...prev,
      [selectedRoom.id]: [...(prev[selectedRoom.id] ?? []), newMessage],
    }))

    const previewText = payload.content || '[이미지]'
    setRooms((prev) => {
      const maxOrder = prev.reduce((max, item) => Math.max(max, item.lastActivityOrder), 0)
      return prev.map((room) =>
        room.id === selectedRoom.id
          ? {
              ...room,
              lastMessage: previewText,
              lastMessageAt: timeLabel,
              lastActivityOrder: maxOrder + 1,
              unreadCount: 0,
            }
          : room,
      )
    })
  }

  const handleStartDirectChat = (targetMember: TeamMember) => {
    const minId = Math.min(CURRENT_USER_ID, targetMember.id)
    const maxId = Math.max(CURRENT_USER_ID, targetMember.id)
    const roomKey = `D:${workspace.teamId}:${minId}:${maxId}`
    const existing = rooms.find((room) => room.roomKey === roomKey)

    if (existing) {
      handleSelectRoom(existing.id)
      setDrawerOpen(false)
      return
    }

    const newRoomId = `room-dm-${minId}-${maxId}-${Date.now()}`
    const maxOrder = rooms.reduce((max, item) => Math.max(max, item.lastActivityOrder), 0)
    const newRoom: ChatRoom = {
      id: newRoomId,
      roomKey,
      type: 'DIRECT',
      name: targetMember.name,
      lastMessage: '새 1:1 대화가 시작되었습니다.',
      lastMessageAt: '방금',
      lastActivityOrder: maxOrder + 1,
      unreadCount: 0,
      memberIds: [CURRENT_USER_ID, targetMember.id],
    }

    setRooms((prev) => [newRoom, ...prev])
    setMessagesByRoom((prev) => ({ ...prev, [newRoomId]: [] }))
    setSelectedRoomId(newRoomId)
    setDrawerOpen(false)
    showToast(`${targetMember.name}님과의 1:1 채팅방이 열렸습니다`)
  }

  const handleInviteMember = (payload: { name: string; department: string }) => {
    const nextId = members.reduce((max, item) => Math.max(max, item.id), 0) + 1
    const colorIndex = (((nextId - 1) % 8) + 1) as MemberColorIndex
    const newMember: TeamMember = {
      id: nextId,
      name: payload.name,
      department: payload.department,
      role: 'MEMBER',
      colorIndex,
      isOnline: true,
    }

    setMembers((prev) => [...prev, newMember])
    setRooms((prev) =>
      prev.map((room) =>
        room.type === 'TEAM' ? { ...room, memberIds: [...room.memberIds, nextId] } : room,
      ),
    )
    showToast(`${payload.name}님을 팀 채팅에 초대했습니다`)
  }

  const handleConfirmKick = (target: TeamMember) => {
    setMembers((prev) => prev.filter((member) => member.id !== target.id))
    setRooms((prev) =>
      prev.map((room) => ({
        ...room,
        memberIds: room.memberIds.filter((id) => id !== target.id),
      })),
    )
    setKickTarget(null)
    showToast(`${target.name}님을 채팅방에서 내보냈습니다`)
  }

  const handleConfirmDeleteRoom = () => {
    if (!selectedRoom) return
    const targetId = selectedRoom.id
    const targetName = selectedRoom.name
    const remaining = rooms.filter((room) => room.id !== targetId)
    setRooms(remaining)
    setSelectedRoomId(remaining[0]?.id ?? '')
    setDeleteRoomOpen(false)
    setDrawerOpen(false)
    showToast(`「${targetName}」 채팅방이 삭제되었습니다`)
  }

  return (
    <div className="flex min-h-screen flex-col bg-bg lg:h-screen lg:overflow-hidden">
      <WorkspaceHeader workspace={workspace} members={members} onBack={() => navigate('/')} />

      <WorkspaceTabs
        activeTab={activeTab}
        chatUnreadCount={totalUnreadChat}
        noticeUnreadCount={workspace.noticeUnreadCount}
        onSelectTab={setActiveTab}
      />

      <PinnedNoticeBanner
        message={workspace.pinnedNotice}
        collapsed={noticeCollapsed}
        onToggleCollapse={() => setNoticeCollapsed((prev) => !prev)}
      />

      <main className="mx-auto flex min-h-0 w-full max-w-[1680px] flex-1 flex-col gap-6 px-6 py-6 lg:flex-row lg:px-12 xl:px-[120px]">
        {activeTab === 'chat' ? (
          <>
            <ChatRoomList
              rooms={sortedRooms}
              selectedRoomId={selectedRoom?.id ?? ''}
              sortOrder={sortOrder}
              onChangeSortOrder={setSortOrder}
              onSelectRoom={handleSelectRoom}
            />

            <ChatTimeline
              key={selectedRoom?.id ?? 'empty'}
              room={selectedRoom}
              messages={currentMessages}
              members={members}
              currentUserId={CURRENT_USER_ID}
              autoAlertMessage={workspace.autoAlertMessage}
              showAutoAlert={showAutoAlert}
              onDismissAutoAlert={() => setShowAutoAlert(false)}
              onOpenSettings={() => setDrawerOpen(true)}
              onSendMessage={handleSendMessage}
            />
          </>
        ) : (
          // TODO(확인 필요): 문서·화이트보드·캘린더·공지 탭은 각 담당자 화면 구현 후 라우트로 연결
          <section className="flex flex-1 flex-col items-center justify-center gap-4 rounded-card border border-line bg-surface p-12 text-center">
            <h2 className="text-heading font-bold text-text">{TAB_LABELS[activeTab]}</h2>
            <p className="text-body text-text-sub">
              {TAB_LABELS[activeTab]} 화면은 담당자 구현 후 이 워크스페이스 탭에 연결될 예정입니다.
            </p>
            <Button variant="primary" size="md" onClick={() => setActiveTab('chat')}>
              채팅으로 돌아가기
            </Button>
          </section>
        )}
      </main>

      <RoomSettingsDrawer
        open={drawerOpen}
        room={selectedRoom}
        members={members}
        currentUserId={CURRENT_USER_ID}
        onClose={() => setDrawerOpen(false)}
        onStartDirectChat={handleStartDirectChat}
        onRequestKickMember={(member) => setKickTarget(member)}
        onRequestInviteMember={() => setInviteOpen(true)}
        onRequestDeleteRoom={() => setDeleteRoomOpen(true)}
      />

      <InviteMemberModal
        open={inviteOpen}
        onClose={() => setInviteOpen(false)}
        onInvite={handleInviteMember}
      />

      <KickMemberModal
        member={kickTarget}
        onClose={() => setKickTarget(null)}
        onConfirmKick={handleConfirmKick}
      />

      <DeleteRoomModal
        open={deleteRoomOpen}
        roomName={selectedRoom?.name ?? ''}
        onClose={() => setDeleteRoomOpen(false)}
        onConfirmDelete={handleConfirmDeleteRoom}
      />

      <Toast message={toastMessage} />
    </div>
  )
}

