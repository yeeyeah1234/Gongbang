import type { MemberColorIndex } from '../../components/ui/Avatar'

export type RoomType = 'TEAM' | 'DIRECT'

export type MessageType = 'TEXT' | 'IMAGE' | 'SYSTEM'

export type MemberRole = 'LEADER' | 'MEMBER'

export type RoomSortOrder = 'latest' | 'unread'

// GET /api/teams/{teamId}/members (NET-06)
export interface TeamMemberApiItem {
  memberId: number
  nickname: string
  profileImageUrl: string | null
  role: MemberRole
  colorIndex: MemberColorIndex
  desiredRoles: string[]
}

// GET /api/teams/{teamId}/chat-rooms (NET-04)
export interface ChatRoomApiItem {
  roomId: number
  type: RoomType
  name: string
  partner: {
    memberId: number
    nickname: string
    profileImageUrl: string | null
  } | null
  lastMessage: {
    content: string | null
    senderNickname: string | null
    createdAt: string
  } | null
  unreadCount: number
}

// GET /api/chat-rooms/{roomId}/messages · WS /sub/chat-rooms/{roomId} (NET-05)
export interface ChatMessageApiItem {
  messageId: number
  type: MessageType
  sender: {
    memberId: number
    nickname: string
    profileImageUrl: string | null
  } | null
  content: string | null
  imageUrl: string | null
  createdAt: string
}

export interface ChatMessagePageApiResponse {
  items: ChatMessageApiItem[]
  hasNext: boolean
}

// WS /sub/teams/{teamId}/members/{memberId}/chat-rooms
export interface ChatRoomListUpdateEvent {
  teamId: number
  roomId: number
  lastMessage: {
    content: string | null
    senderNickname: string | null
    createdAt: string
  } | null
  unreadCount: number
  updatedAt: string
}

// WS /sub/teams/{teamId}/events
export interface TeamEventPayload {
  type: string
  teamId: number
  targetId: number | null
  actorId: number | null
  at: string
}

// WS /sub/members/{memberId}/events
export interface UserEventPayload {
  type: string
  payload: {
    teamId?: number
    teamName?: string
  } | null
  at: string
}

// WS /pub/chat-rooms/{roomId}/send
export interface ChatSendPayload {
  type: 'TEXT' | 'IMAGE'
  content?: string | null
  fileId?: number | null
}

// 화면 표시용 팀원 모델
export interface TeamMember {
  id: number
  name: string
  profileImageUrl: string | null
  role: MemberRole
  department: string
  colorIndex: MemberColorIndex
  desiredRoles: string[]
}

// 화면 표시용 채팅방 모델
export interface ChatRoom {
  id: number
  type: RoomType
  name: string
  partnerId: number | null
  partnerProfileImageUrl: string | null
  lastMessage: string
  lastMessageAt: string
  lastActivityAt: string
  unreadCount: number
  isEnded?: boolean
}

// 화면 표시용 메시지 모델
export interface ChatMessage {
  id: number
  type: MessageType
  senderId: number | null
  senderNickname: string
  senderProfileImageUrl: string | null
  content: string
  imageUrl: string | null
  dateLabel: string
  timeLabel: string
  createdAt: string
}
