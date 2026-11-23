import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { getErrorMessage } from '../api/errors'
import { Toast } from '../components/ui/Toast'
import {
  DelegateLeaderModal,
  KickMemberModal,
  ReportMemberModal,
} from '../features/chat/components/ChatModals'
import { ChatRoomList } from '../features/chat/components/ChatRoomList'
import { ChatTimeline } from '../features/chat/components/ChatTimeline'
import { RoomSettingsDrawer } from '../features/chat/components/RoomSettingsDrawer'
import { mapChatMessage, mapChatRoom, mapTeamMember } from '../features/chat/format'
import {
  useChatMessages,
  useChatRooms,
  useDelegateLeaderMutation,
  useKickMemberMutation,
  useMarkRoomReadMutation,
  useOpenDirectChatMutation,
  useTeamMembers,
} from '../features/chat/hooks'
import type { ChatMessage, RoomSortOrder, TeamMember } from '../features/chat/types'
import { useWorkspace } from '../features/workspace/context'
import { getDeadlineBadge } from '../features/workspace/deadline'

function buildDdayAlertMessage(deadline: string | null): string {
  const badge = getDeadlineBadge(deadline)
  if (!badge) return ''
  if (badge.label === '마감 D-7') {
    return '[D-day 자동 알림] 공모전 마감까지 7일 남았습니다.'
  }
  if (badge.label === '마감 D-1') {
    return '[D-day 자동 알림] 공모전 마감까지 1일 남았습니다.'
  }
  return ''
}

/** 워크스페이스 채팅 화면 (/workspaces/:teamId/chat, F-NET-06) */
export default function WorkspaceChatPage() {
  const { teamId, summary, realtime } = useWorkspace()

  const deadlineBadge = getDeadlineBadge(summary.deadline)
  const isTeamEnded = deadlineBadge?.tone === 'ended'
  const autoAlertMessage = buildDdayAlertMessage(summary.deadline)

  const [showAutoAlert, setShowAutoAlert] = useState(true)
  const [selectedRoomId, setSelectedRoomId] = useState<number | null>(null)
  const [sortOrder, setSortOrder] = useState<RoomSortOrder>('latest')

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [delegateTarget, setDelegateTarget] = useState<TeamMember | null>(null)
  const [kickTarget, setKickTarget] = useState<TeamMember | null>(null)
  const [reportTarget, setReportTarget] = useState<TeamMember | null>(null)

  const [toastMessage, setToastMessage] = useState<string | null>(null)
  const toastTimerRef = useRef<number | null>(null)
  const lastMarkedReadRef = useRef<Map<number, number>>(new Map())

  const showToast = useCallback((message: string) => {
    setToastMessage(message)
    if (toastTimerRef.current !== null) {
      window.clearTimeout(toastTimerRef.current)
    }
    toastTimerRef.current = window.setTimeout(() => {
      setToastMessage(null)
    }, 2500)
  }, [])

  useEffect(() => {
    return () => {
      if (toastTimerRef.current !== null) {
        window.clearTimeout(toastTimerRef.current)
      }
    }
  }, [])

  const membersQuery = useTeamMembers(teamId)
  const roomsQuery = useChatRooms(teamId)
  const openDirectChatMutation = useOpenDirectChatMutation(teamId)
  const delegateLeaderMutation = useDelegateLeaderMutation(teamId)
  const kickMemberMutation = useKickMemberMutation(teamId)
  const { mutate: markRoomAsReadMutate } = useMarkRoomReadMutation(teamId)

  const members = useMemo(() => (membersQuery.data ?? []).map(mapTeamMember), [membersQuery.data])

  const displayRooms = useMemo(
    () =>
      (roomsQuery.data ?? []).map((item) =>
        mapChatRoom(item, { teamName: summary.teamName, isEnded: isTeamEnded }),
      ),
    [roomsQuery.data, summary.teamName, isTeamEnded],
  )

  const sortedRooms = useMemo(() => {
    const copy = [...displayRooms]
    if (sortOrder === 'unread') {
      copy.sort((a, b) => {
        if (b.unreadCount !== a.unreadCount) return b.unreadCount - a.unreadCount
        return b.lastActivityAt.localeCompare(a.lastActivityAt)
      })
    } else {
      copy.sort((a, b) => b.lastActivityAt.localeCompare(a.lastActivityAt))
    }
    return copy
  }, [displayRooms, sortOrder])

  const selectedRoom = displayRooms.find((room) => room.id === selectedRoomId) ?? sortedRooms[0]
  const activeRoomId = selectedRoom?.id ?? null

  const messagesQuery = useChatMessages(activeRoomId)

  const currentMessages = useMemo<ChatMessage[]>(() => {
    const pages = messagesQuery.data?.pages
    if (!pages || pages.length === 0) return []

    // pages[0]이 최신, pages[1..]이 이전 페이지이므로 역순으로 펼쳐 시간 오름차순으로 맞춘다
    const seen = new Set<number>()
    const result: ChatMessage[] = []
    for (let i = pages.length - 1; i >= 0; i -= 1) {
      for (const item of pages[i].items) {
        if (seen.has(item.messageId)) continue
        seen.add(item.messageId)
        result.push(mapChatMessage(item))
      }
    }
    return result
  }, [messagesQuery.data])

  const latestMessageId = currentMessages[currentMessages.length - 1]?.id ?? null

  const markLatestAsRead = useCallback(
    (roomId: number, messageId: number) => {
      const prev = lastMarkedReadRef.current.get(roomId) ?? 0
      if (messageId <= prev) return
      lastMarkedReadRef.current.set(roomId, messageId)
      markRoomAsReadMutate(
        { roomId, lastReadMessageId: messageId },
        {
          onError: () => {
            lastMarkedReadRef.current.delete(roomId)
          },
        },
      )
    },
    [markRoomAsReadMutate],
  )

  // 활성 방의 실시간 메시지 스트림 구독
  useEffect(() => {
    if (activeRoomId === null || !realtime) return
    return realtime.subscribeRoom(activeRoomId, () => {
      // 메시지 캐시 적재는 useWorkspaceRealtime에서 처리하며 읽음 Sync는 ChatTimeline의 onReachBottom이 수행한다
    })
  }, [activeRoomId, realtime])

  const handleReachBottom = useCallback(() => {
    if (activeRoomId !== null && latestMessageId !== null) {
      markLatestAsRead(activeRoomId, latestMessageId)
    }
  }, [activeRoomId, latestMessageId, markLatestAsRead])

  const handleSelectRoom = (roomId: number) => {
    setSelectedRoomId(roomId)
  }

  const handleSendMessage = (content: string): boolean => {
    if (activeRoomId === null) return false
    const sent = realtime?.sendRoomMessage(activeRoomId, { type: 'TEXT', content }) ?? false
    if (!sent) {
      showToast('실시간 채팅 서버에 연결 중입니다. 잠시 후 다시 시도해 주세요')
      return false
    }
    return true
  }

  const handleStartDirectChat = (targetMember: TeamMember) => {
    openDirectChatMutation.mutate(targetMember.id, {
      onSuccess: (data) => {
        setSelectedRoomId(data.roomId)
        setDrawerOpen(false)
      },
      onError: (error) => {
        showToast(getErrorMessage(error, '1:1 채팅방을 열지 못했어요'))
      },
    })
  }

  const handleConfirmDelegateLeader = (target: TeamMember) => {
    delegateLeaderMutation.mutate(target.id, {
      onSuccess: () => {
        setDelegateTarget(null)
        showToast(`${target.name}님에게 팀장 권한을 위임했습니다`)
      },
      onError: (error) => {
        showToast(getErrorMessage(error, '팀장 권한을 위임하지 못했어요'))
      },
    })
  }

  const handleConfirmKick = (target: TeamMember) => {
    kickMemberMutation.mutate(target.id, {
      onSuccess: () => {
        setKickTarget(null)
        showToast(`${target.name}님을 팀에서 강제 퇴장했습니다`)
      },
      onError: (error) => {
        showToast(getErrorMessage(error, '팀원을 강제 퇴장하지 못했어요'))
      },
    })
  }

  const handleConfirmReport = (target: TeamMember, reason: string, detail: string) => {
    // TODO(확인 필요): 신고 도메인(report) API 연동 시 reason, detail 함께 전송
    void detail
    setReportTarget(null)
    showToast(`${target.name}님 신고(${reason}) 기능은 준비 중이에요`)
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-6 lg:flex-row">
      <ChatRoomList
        rooms={sortedRooms}
        selectedRoomId={activeRoomId}
        sortOrder={sortOrder}
        isLoading={roomsQuery.isLoading}
        onChangeSortOrder={setSortOrder}
        onSelectRoom={handleSelectRoom}
      />

      <ChatTimeline
        key={activeRoomId ?? 'empty'}
        room={selectedRoom}
        messages={currentMessages}
        members={members}
        currentUserId={summary.myMemberId}
        autoAlertMessage={autoAlertMessage}
        showAutoAlert={showAutoAlert}
        isLoadingMessages={messagesQuery.isLoading}
        hasNextPage={messagesQuery.hasNextPage}
        isFetchingNextPage={messagesQuery.isFetchingNextPage}
        isConnected={realtime?.connected ?? false}
        onLoadOlder={() => {
          void messagesQuery.fetchNextPage()
        }}
        onReachBottom={handleReachBottom}
        onDismissAutoAlert={() => setShowAutoAlert(false)}
        onOpenSettings={() => setDrawerOpen(true)}
        onSendMessage={handleSendMessage}
        onUnsupportedFeature={showToast}
      />

      <RoomSettingsDrawer
        open={drawerOpen}
        room={selectedRoom}
        members={members}
        currentUserId={summary.myMemberId}
        isLeader={summary.isLeader}
        onClose={() => setDrawerOpen(false)}
        onRequestDelegateLeader={(member) => setDelegateTarget(member)}
        onStartDirectChat={handleStartDirectChat}
        onRequestKickMember={(member) => setKickTarget(member)}
        onRequestReportMember={(member) => setReportTarget(member)}
      />

      <DelegateLeaderModal
        member={delegateTarget}
        isPending={delegateLeaderMutation.isPending}
        onClose={() => setDelegateTarget(null)}
        onConfirmDelegate={handleConfirmDelegateLeader}
      />

      <KickMemberModal
        member={kickTarget}
        isPending={kickMemberMutation.isPending}
        onClose={() => setKickTarget(null)}
        onConfirmKick={handleConfirmKick}
      />

      <ReportMemberModal
        member={reportTarget}
        onClose={() => setReportTarget(null)}
        onConfirmReport={(member, reason, detail) => handleConfirmReport(member, reason, detail)}
      />

      <Toast message={toastMessage} />
    </div>
  )
}
