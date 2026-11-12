import type { MemberColorIndex } from '../../components/ui/Avatar'

export type WorkspaceTabKey = 'chat' | 'document' | 'whiteboard' | 'calendar' | 'notice'

export type RoomType = 'TEAM' | 'DIRECT'

export type MemberRole = 'LEADER' | 'MEMBER'

export type RoomSortOrder = 'latest' | 'unread'

export interface TeamMember {
  id: number
  name: string
  department: string
  role: MemberRole
  colorIndex: MemberColorIndex
  isOnline: boolean
}

export interface ChatRoom {
  id: string
  roomKey: string
  type: RoomType
  name: string
  lastMessage: string
  lastMessageAt: string
  lastActivityOrder: number
  unreadCount: number
  memberIds: number[]
  isEnded?: boolean
}

export interface ChatMessage {
  id: string
  roomId: string
  senderId: number
  content: string
  imageUrl?: string
  imageName?: string
  dateLabel: string
  timeLabel: string
  isRead: boolean
}

export interface WorkspaceInfo {
  teamId: string
  teamName: string
  dDay: number
  isEnded: boolean
  pinnedNotice: string
  autoAlertMessage: string
  noticeUnreadCount: number
}

