import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { apiClient } from '../../api/client'
import type {
  ChatMessageApiItem,
  ChatRoomApiItem,
  TeamMemberApiItem,
} from '../../features/chat/types'
import { nowKstDateTime } from '../../features/workspace/sidebar/relativeTime'

// 개발 전용: /dev/chat-preview가 떠 있는 동안만 팀 요약·팀원·채팅방·메시지 API를 메모리 데이터로 답한다
// (documentPreviewApi와 같은 방식). 메시지 보내기(STOMP)는 미리보기에서 하지 않는다.
export const PREVIEW_TEAM_ID = 0
const ME = 1
const DELAY_MS = 200

const MEMBERS: TeamMemberApiItem[] = [
  {
    memberId: 1,
    nickname: '김하늘',
    profileImageUrl: null,
    role: 'LEADER',
    colorIndex: 1,
    desiredRoles: ['기획'],
  },
  {
    memberId: 2,
    nickname: '이바다',
    profileImageUrl: null,
    role: 'MEMBER',
    colorIndex: 2,
    desiredRoles: ['디자인'],
  },
  {
    memberId: 3,
    nickname: '박산들',
    profileImageUrl: null,
    role: 'MEMBER',
    colorIndex: 3,
    desiredRoles: ['개발'],
  },
  {
    memberId: 4,
    nickname: '최노을',
    profileImageUrl: null,
    role: 'MEMBER',
    colorIndex: 4,
    desiredRoles: [],
  },
]

function kstAgo(ms: number) {
  return nowKstDateTime(new Date(Date.now() - ms))
}

const MIN = 60 * 1000

const ROOMS: ChatRoomApiItem[] = [
  {
    roomId: 100,
    type: 'TEAM',
    name: '공방팀',
    partner: null,
    lastMessage: {
      content: '오늘 9시에 회의해요!',
      senderNickname: '이바다',
      createdAt: kstAgo(2 * MIN),
    },
    unreadCount: 3,
  },
  {
    roomId: 101,
    type: 'DIRECT',
    name: '이바다',
    partner: { memberId: 2, nickname: '이바다', profileImageUrl: null },
    lastMessage: {
      content: '시안 확인 부탁드려요',
      senderNickname: '이바다',
      createdAt: kstAgo(40 * MIN),
    },
    unreadCount: 1,
  },
  {
    roomId: 102,
    type: 'DIRECT',
    name: '박산들',
    partner: { memberId: 3, nickname: '박산들', profileImageUrl: null },
    lastMessage: {
      content: '넵 알겠습니다',
      senderNickname: '김하늘',
      createdAt: kstAgo(26 * 60 * MIN),
    },
    unreadCount: 0,
  },
]

const LINES = [
  '안녕하세요! 이번 공모전 같이 하게 돼서 반가워요',
  '기획서 초안은 문서 탭에 올려 둘게요',
  '디자인 시안은 화이트보드에 정리해 볼게요',
  '좋아요 👍',
  '마감이 얼마 안 남았으니 이번 주 안에 역할 나눠요',
  '저는 개발 맡을게요. 프로토타입은 금요일까지 가능해요',
  '오늘 9시에 회의해요!',
]

function messagesOf(roomId: number): ChatMessageApiItem[] {
  const senders = roomId === 100 ? [2, 1, 3, 1, 2, 3, 2] : [roomId === 101 ? 2 : 3, ME]
  return LINES.map((content, i) => {
    const senderId = senders[i % senders.length]
    const sender = MEMBERS.find((m) => m.memberId === senderId)!
    return {
      messageId: roomId * 1000 + i + 1,
      type: 'TEXT' as const,
      sender: { memberId: sender.memberId, nickname: sender.nickname, profileImageUrl: null },
      content,
      imageUrl: null,
      createdAt: kstAgo((LINES.length - i) * 7 * MIN),
    }
  })
}

type Result = { status: number; data?: unknown; code?: string }

export function installChatPreviewApi(): () => void {
  const handle = ({ url = '', method = 'get' }: InternalAxiosRequestConfig): Result => {
    if (url === `/api/teams/${PREVIEW_TEAM_ID}`) {
      return {
        status: 200,
        data: {
          teamId: PREVIEW_TEAM_ID,
          teamName: '공방팀',
          contestId: 1,
          contestTitle: '미리보기 공모전',
          deadline: null,
          isLeader: true,
          myMemberId: ME,
        },
      }
    }
    if (url === `/api/teams/${PREVIEW_TEAM_ID}/members`) return { status: 200, data: MEMBERS }
    if (url === `/api/teams/${PREVIEW_TEAM_ID}/chat-rooms`) {
      return { status: 200, data: structuredClone(ROOMS) }
    }
    const messages = url.match(/^\/api\/chat-rooms\/(\d+)\/messages/)
    if (messages) {
      return {
        status: 200,
        data: { items: messagesOf(Number(messages[1])), hasNext: false },
      }
    }
    const read = url.match(/^\/api\/chat-rooms\/(\d+)\/read/)
    if (read && method === 'patch') {
      const room = ROOMS.find((r) => r.roomId === Number(read[1]))
      if (room) room.unreadCount = 0
      return { status: 200 }
    }
    return { status: 404, code: 'RESOURCE_NOT_FOUND' }
  }

  const adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    await new Promise((resolve) => setTimeout(resolve, DELAY_MS))
    const { status, data = null, code } = handle(config)
    const error = code ? { code, message: code } : null
    const response: AxiosResponse = {
      status,
      statusText: String(status),
      headers: {},
      config,
      data: { success: !error, data, error },
    }
    if (error) throw new AxiosError(code, code, config, null, response)
    return response
  }

  const interceptorId = apiClient.interceptors.request.use((config) => {
    const isPreviewApi = /^\/api\/(teams\/0($|\/)|chat-rooms\/)/.test(config.url ?? '')
    return isPreviewApi ? Object.assign(config, { adapter }) : config
  })
  return () => apiClient.interceptors.request.eject(interceptorId)
}
