import { roleLabel } from '../member/constants'
import type {
  ChatMessage,
  ChatMessageApiItem,
  ChatRoom,
  ChatRoomApiItem,
  TeamMember,
  TeamMemberApiItem,
} from './types'

function parseDate(iso: string | null | undefined): Date | null {
  if (!iso) return null
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDesiredRolesSummary(desiredRoles: string[]): string {
  if (!desiredRoles || desiredRoles.length === 0) return '역할 미지정'
  return desiredRoles.slice(0, 2).map(roleLabel).join(' · ')
}

export function formatMessageTimeLabel(iso: string | null | undefined): string {
  const date = parseDate(iso)
  if (!date) return ''
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

export function formatMessageDateLabel(iso: string | null | undefined): string {
  const date = parseDate(iso)
  if (!date) return ''
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(date)
}

export function formatRoomTimeLabel(iso: string | null | undefined): string {
  const date = parseDate(iso)
  if (!date) return ''
  const now = new Date()
  const isSameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (isSameDay) {
    return formatMessageTimeLabel(iso)
  }
  return `${date.getMonth() + 1}월 ${date.getDate()}일`
}

export function mapTeamMember(item: TeamMemberApiItem): TeamMember {
  return {
    id: item.memberId,
    name: item.nickname,
    profileImageUrl: item.profileImageUrl,
    role: item.role,
    department: formatDesiredRolesSummary(item.desiredRoles),
    colorIndex: item.colorIndex,
    desiredRoles: item.desiredRoles,
  }
}

export function mapChatRoom(
  item: ChatRoomApiItem,
  options: { teamName: string; isEnded: boolean },
): ChatRoom {
  const isTeam = item.type === 'TEAM'
  const lastPreview = item.lastMessage?.content ?? '아직 나눈 대화가 없습니다'
  const lastAt = item.lastMessage?.createdAt ?? ''

  return {
    id: item.roomId,
    type: item.type,
    name: isTeam ? options.teamName || item.name : item.name,
    partnerId: item.partner?.memberId ?? null,
    partnerProfileImageUrl: item.partner?.profileImageUrl ?? null,
    lastMessage: lastPreview,
    lastMessageAt: formatRoomTimeLabel(lastAt),
    lastActivityAt: lastAt,
    unreadCount: item.unreadCount,
    isEnded: isTeam ? options.isEnded : false,
  }
}

export function mapChatMessage(item: ChatMessageApiItem): ChatMessage {
  return {
    id: item.messageId,
    type: item.type,
    senderId: item.sender?.memberId ?? null,
    senderNickname: item.sender?.nickname ?? '탈퇴한 사용자',
    senderProfileImageUrl: item.sender?.profileImageUrl ?? null,
    content: item.content ?? '',
    imageUrl: item.imageUrl,
    dateLabel: formatMessageDateLabel(item.createdAt),
    timeLabel: formatMessageTimeLabel(item.createdAt),
    createdAt: item.createdAt,
  }
}
