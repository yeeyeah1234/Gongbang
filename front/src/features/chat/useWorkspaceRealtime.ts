import type { Client, StompSubscription } from '@stomp/stompjs'
import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { workspaceKeys } from '../workspace/hooks'
import { appendChatMessageToCache, applyRoomListUpdateToCache, chatKeys } from './hooks'
import { createStompClient, safeJsonParse, unsubscribeSafely } from './stomp'
import type {
  ChatMessageApiItem,
  ChatRoomListUpdateEvent,
  ChatSendPayload,
  TeamEventPayload,
  UserEventPayload,
} from './types'

export type RoomMessageListener = (message: ChatMessageApiItem) => void
export type TeamEventListener = (event: TeamEventPayload) => void

export interface WorkspaceRealtimeBridge {
  connected: boolean
  subscribeRoom: (roomId: number, listener: RoomMessageListener) => () => void
  subscribeTeamEvent: (listener: TeamEventListener) => () => void
  sendRoomMessage: (roomId: number, payload: ChatSendPayload) => boolean
}

interface UseWorkspaceRealtimeOptions {
  teamId: number
  myMemberId: number | null
  enabled: boolean
  onRefreshNotice: () => Promise<void> | void
  onKicked?: (teamName?: string) => void
}

export function useWorkspaceRealtime({
  teamId,
  myMemberId,
  enabled,
  onRefreshNotice,
  onKicked,
}: UseWorkspaceRealtimeOptions): WorkspaceRealtimeBridge {
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)

  const clientRef = useRef<Client | null>(null)
  const roomListenersRef = useRef<Map<number, Set<RoomMessageListener>>>(new Map())
  const roomSubscriptionsRef = useRef<Map<number, StompSubscription>>(new Map())
  const teamEventListenersRef = useRef<Set<TeamEventListener>>(new Set())

  const onRefreshNoticeRef = useRef(onRefreshNotice)
  const onKickedRef = useRef(onKicked)

  useEffect(() => {
    onRefreshNoticeRef.current = onRefreshNotice
  }, [onRefreshNotice])

  useEffect(() => {
    onKickedRef.current = onKicked
  }, [onKicked])

  const attachRoomSubscription = useCallback(
    (client: Client, roomId: number) => {
      if (!client.connected || roomSubscriptionsRef.current.has(roomId)) return
      const sub = client.subscribe(`/sub/chat-rooms/${roomId}`, (frame) => {
        const msg = safeJsonParse<ChatMessageApiItem>(frame)
        if (!msg) return
        appendChatMessageToCache(queryClient, roomId, msg)
        const listeners = roomListenersRef.current.get(roomId)
        listeners?.forEach((listener) => listener(msg))
      })
      roomSubscriptionsRef.current.set(roomId, sub)
    },
    [queryClient],
  )

  useEffect(() => {
    if (!enabled || !Number.isFinite(teamId) || myMemberId === null) {
      return
    }

    const baseSubs: StompSubscription[] = []
    const roomSubsMap = roomSubscriptionsRef.current

    const client = createStompClient({
      onConnect: (connectedClient) => {
        setConnected(true)

        baseSubs.push(
          connectedClient.subscribe(`/sub/teams/${teamId}/events`, (frame) => {
            const event = safeJsonParse<TeamEventPayload>(frame)
            if (!event) return

            teamEventListenersRef.current.forEach((listener) => listener(event))

            if (event.type.startsWith('DOCUMENT_')) {
              void queryClient.invalidateQueries({ queryKey: ['workspace', teamId, 'documents'] })
              return
            }

            if (event.type.startsWith('WHITEBOARD_')) {
              void queryClient.invalidateQueries({ queryKey: ['workspace', teamId, 'boards'] })
              return
            }

            if (event.type.startsWith('NOTICE_')) {
              void onRefreshNoticeRef.current()
              return
            }

            if (event.type.startsWith('EVENT_')) {
              void queryClient.invalidateQueries({ queryKey: ['workspace', teamId, 'calendar'] })
              return
            }

            if (event.type === 'TEAM_RENAMED') {
              void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
              void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(teamId) })
              return
            }

            if (
              event.type === 'MEMBER_JOINED' ||
              event.type === 'MEMBER_LEFT' ||
              event.type === 'MEMBER_KICKED'
            ) {
              void queryClient.invalidateQueries({ queryKey: chatKeys.members(teamId) })
              void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(teamId) })
              void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
              return
            }

            if (event.type === 'LEADER_CHANGED') {
              void queryClient.invalidateQueries({ queryKey: chatKeys.members(teamId) })
              void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
            }
          }),
        )

        baseSubs.push(
          connectedClient.subscribe(
            `/sub/teams/${teamId}/members/${myMemberId}/chat-rooms`,
            (frame) => {
              const event = safeJsonParse<ChatRoomListUpdateEvent>(frame)
              if (!event) return
              const updated = applyRoomListUpdateToCache(queryClient, teamId, event)
              if (!updated) {
                void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(teamId) })
              }
            },
          ),
        )

        baseSubs.push(
          connectedClient.subscribe(`/sub/members/${myMemberId}/events`, (frame) => {
            const event = safeJsonParse<UserEventPayload>(frame)
            if (!event) return
            if (event.type === 'KICKED' && event.payload?.teamId === teamId) {
              void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
              onKickedRef.current?.(event.payload?.teamName)
            }
          }),
        )

        // 이미 열려 있는 방 구독 복원
        for (const roomId of roomListenersRef.current.keys()) {
          attachRoomSubscription(connectedClient, roomId)
        }
      },
      onDisconnect: () => {
        setConnected(false)
        roomSubsMap.clear()
      },
    })

    clientRef.current = client
    client.activate()

    return () => {
      setConnected(false)
      baseSubs.forEach(unsubscribeSafely)
      roomSubsMap.forEach(unsubscribeSafely)
      roomSubsMap.clear()
      clientRef.current = null
      void client.deactivate()
    }
  }, [teamId, myMemberId, enabled, queryClient, attachRoomSubscription])

  const subscribeRoom = useCallback(
    (roomId: number, listener: RoomMessageListener) => {
      let set = roomListenersRef.current.get(roomId)
      if (!set) {
        set = new Set()
        roomListenersRef.current.set(roomId, set)
      }
      set.add(listener)

      const client = clientRef.current
      if (client?.connected) {
        attachRoomSubscription(client, roomId)
      }

      return () => {
        const currentSet = roomListenersRef.current.get(roomId)
        if (!currentSet) return
        currentSet.delete(listener)
        if (currentSet.size === 0) {
          roomListenersRef.current.delete(roomId)
          const sub = roomSubscriptionsRef.current.get(roomId) ?? null
          if (sub) {
            unsubscribeSafely(sub)
            roomSubscriptionsRef.current.delete(roomId)
          }
        }
      }
    },
    [attachRoomSubscription],
  )

  const subscribeTeamEvent = useCallback((listener: TeamEventListener) => {
    teamEventListenersRef.current.add(listener)
    return () => {
      teamEventListenersRef.current.delete(listener)
    }
  }, [])

  const sendRoomMessage = useCallback((roomId: number, payload: ChatSendPayload): boolean => {
    const client = clientRef.current
    if (!client?.connected) return false
    client.publish({
      destination: `/pub/chat-rooms/${roomId}/send`,
      body: JSON.stringify(payload),
    })
    return true
  }, [])

  return useMemo(
    () => ({
      connected,
      subscribeRoom,
      subscribeTeamEvent,
      sendRoomMessage,
    }),
    [connected, subscribeRoom, subscribeTeamEvent, sendRoomMessage],
  )
}
