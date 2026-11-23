import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
  type QueryClient,
} from '@tanstack/react-query'
import { workspaceKeys } from '../workspace/hooks'
import {
  delegateTeamLeader,
  fetchChatMessages,
  fetchChatRooms,
  fetchTeamMembers,
  kickTeamMember,
  markChatRoomAsRead,
  openOrCreateDirectChatRoom,
} from './api'
import type {
  ChatMessageApiItem,
  ChatMessagePageApiResponse,
  ChatRoomApiItem,
  ChatRoomListUpdateEvent,
} from './types'

export const chatKeys = {
  all: (teamId: number) => ['workspace', teamId, 'chat'] as const,
  members: (teamId: number) => [...chatKeys.all(teamId), 'members'] as const,
  rooms: (teamId: number) => [...chatKeys.all(teamId), 'rooms'] as const,
  messages: (roomId: number | null) => ['chat-room', roomId, 'messages'] as const,
}

export function useTeamMembers(teamId: number, enabled = true) {
  return useQuery({
    queryKey: chatKeys.members(teamId),
    queryFn: () => fetchTeamMembers(teamId),
    enabled: Number.isFinite(teamId) && enabled,
    retry: false,
  })
}

export function useChatRooms(teamId: number, enabled = true) {
  return useQuery({
    queryKey: chatKeys.rooms(teamId),
    queryFn: () => fetchChatRooms(teamId),
    enabled: Number.isFinite(teamId) && enabled,
    retry: false,
  })
}

export function useChatMessages(roomId: number | null) {
  return useInfiniteQuery({
    queryKey: chatKeys.messages(roomId),
    queryFn: ({ pageParam }) => fetchChatMessages(roomId!, pageParam, 30),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext && lastPage.items.length > 0 ? lastPage.items[0].messageId : undefined,
    enabled: roomId !== null && Number.isFinite(roomId),
    retry: false,
  })
}

/**
 * 실시간으로 도착한 STOMP 메시지를 채팅방 메시지 InfiniteData 캐시에 추가한다.
 * pages[0]이 가장 최신 페이지이며, 각 page.items는 오래된 순(오름차순)으로 정렬되어 있다.
 */
export function appendChatMessageToCache(
  queryClient: QueryClient,
  roomId: number,
  incoming: ChatMessageApiItem,
) {
  queryClient.setQueryData<InfiniteData<ChatMessagePageApiResponse>>(
    chatKeys.messages(roomId),
    (old) => {
      if (!old || old.pages.length === 0) return old
      const exists = old.pages.some((page) =>
        page.items.some((item) => item.messageId === incoming.messageId),
      )
      if (exists) return old

      const firstPage = old.pages[0]
      const updatedFirstPage: ChatMessagePageApiResponse = {
        ...firstPage,
        items: [...firstPage.items, incoming],
      }
      return {
        ...old,
        pages: [updatedFirstPage, ...old.pages.slice(1)],
      }
    },
  )
}

/**
 * 채팅방 목록 실시간 이벤트를 캐시에 반영한다. 캐시에 없는 방이면 false를 반환해 재조회를 유도한다.
 */
export function applyRoomListUpdateToCache(
  queryClient: QueryClient,
  teamId: number,
  event: ChatRoomListUpdateEvent,
): boolean {
  const existing = queryClient.getQueryData<ChatRoomApiItem[]>(chatKeys.rooms(teamId))
  if (!existing) return false

  let matched = false
  const updated = existing.map((room) => {
    if (room.roomId !== event.roomId) return room
    matched = true
    return {
      ...room,
      lastMessage: event.lastMessage ?? room.lastMessage,
      unreadCount: event.unreadCount,
    }
  })

  if (!matched) return false

  updated.sort((a, b) => {
    const aTime = a.lastMessage?.createdAt ?? ''
    const bTime = b.lastMessage?.createdAt ?? ''
    return bTime.localeCompare(aTime)
  })

  queryClient.setQueryData<ChatRoomApiItem[]>(chatKeys.rooms(teamId), updated)
  return true
}

export function useOpenDirectChatMutation(teamId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (memberId: number) => openOrCreateDirectChatRoom(teamId, memberId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: chatKeys.rooms(teamId) }),
  })
}

export function useDelegateLeaderMutation(teamId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (memberId: number) => delegateTeamLeader(teamId, memberId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: chatKeys.members(teamId) }),
        queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) }),
      ])
    },
  })
}

export function useKickMemberMutation(teamId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (memberId: number) => kickTeamMember(teamId, memberId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: chatKeys.members(teamId) }),
        queryClient.invalidateQueries({ queryKey: chatKeys.rooms(teamId) }),
        queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) }),
      ])
    },
  })
}

export function useMarkRoomReadMutation(teamId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ roomId, lastReadMessageId }: { roomId: number; lastReadMessageId: number }) =>
      markChatRoomAsRead(roomId, lastReadMessageId),
    onSuccess: (_, { roomId }) => {
      queryClient.setQueryData<ChatRoomApiItem[]>(chatKeys.rooms(teamId), (old) =>
        old?.map((room) => (room.roomId === roomId ? { ...room, unreadCount: 0 } : room)),
      )
    },
  })
}
