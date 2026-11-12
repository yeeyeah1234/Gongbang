import type { ChatMessage, ChatRoom, TeamMember, WorkspaceInfo } from './types'

export const CURRENT_USER_ID = 1

export const INITIAL_WORKSPACE_INFO: WorkspaceInfo = {
  teamId: '1',
  teamName: '현대차 공모전 · 알파팀',
  dDay: 14,
  isEnded: false,
  pinnedNotice: '기획안 1차 마감 일정은 캘린더에서 확인해 주세요',
  autoAlertMessage: '[D-day 자동 알림] 공모전 마감까지 7일 남았습니다.',
  noticeUnreadCount: 1,
}

export const INITIAL_MEMBERS: TeamMember[] = [
  {
    id: 1,
    name: '이상현',
    department: '프론트엔드',
    role: 'LEADER',
    colorIndex: 1,
    isOnline: true,
  },
  {
    id: 2,
    name: '최규선',
    department: 'UI/UX 디자이너',
    role: 'MEMBER',
    colorIndex: 2,
    isOnline: true,
  },
  {
    id: 3,
    name: '김건우',
    department: '백엔드 개발',
    role: 'MEMBER',
    colorIndex: 3,
    isOnline: true,
  },
  {
    id: 4,
    name: '박기획',
    department: '서비스 기획',
    role: 'MEMBER',
    colorIndex: 4,
    isOnline: false,
  },
]

export const INITIAL_ROOMS: ChatRoom[] = [
  {
    id: 'room-team-1',
    roomKey: 'T:1',
    type: 'TEAM',
    name: '현대차 공모전 · 알파팀',
    lastMessage: '넵 감사합니다! 피그마 확인...',
    lastMessageAt: '14:51',
    lastActivityOrder: 2,
    unreadCount: 3,
    memberIds: [1, 2, 3, 4],
  },
  {
    id: 'room-dm-1-2',
    roomKey: 'D:1:1:2',
    type: 'DIRECT',
    name: '최규선',
    lastMessage: '내일까지 부탁드렸던 파일 올려주시기 바랍니다.',
    lastMessageAt: '어제',
    lastActivityOrder: 1,
    unreadCount: 0,
    memberIds: [1, 2],
  },
]

export const INITIAL_MESSAGES: Record<string, ChatMessage[]> = {
  'room-team-1': [
    {
      id: 'msg-0',
      roomId: 'room-team-1',
      senderId: 4,
      content: '이번 주 기획안 초안 공유드립니다. 다들 확인 후 의견 편하게 주세요!',
      dateLabel: '2026년 10월 20일 화요일',
      timeLabel: '18:20',
      isRead: true,
    },
    {
      id: 'msg-1',
      roomId: 'room-team-1',
      senderId: 2,
      content:
        '안녕하세요! 와이어프레임 초안 작업 완료해서 문서 탭에 올렸습니다.\n피드백 부탁드립니다!',
      dateLabel: '2026년 10월 21일 수요일',
      timeLabel: '14:45',
      isRead: true,
    },
    {
      id: 'msg-2',
      roomId: 'room-team-1',
      senderId: 3,
      content: '실시간 웹소켓 채팅 서버 연동 준비도 완료되었습니다!',
      dateLabel: '2026년 10월 21일 수요일',
      timeLabel: '14:48',
      isRead: true,
    },
    {
      id: 'msg-3',
      roomId: 'room-team-1',
      senderId: 1,
      content:
        '넵 감사합니다! 지금 바로 확인해보고 코멘트 남기겠습니다.\n오늘 저녁 8시 회의 때 뵙겠습니다!',
      dateLabel: '2026년 10월 21일 수요일',
      timeLabel: '14:51',
      isRead: true,
    },
  ],
  'room-dm-1-2': [
    {
      id: 'dm-1',
      roomId: 'room-dm-1-2',
      senderId: 2,
      content: '상현님, 메인 시안 컬러 토큰 정리본 문서 탭에 올려두었습니다.',
      dateLabel: '2026년 10월 20일 화요일',
      timeLabel: '17:10',
      isRead: true,
    },
    {
      id: 'dm-2',
      roomId: 'room-dm-1-2',
      senderId: 2,
      content: '내일까지 부탁드렸던 파일 올려주시기 바랍니다.',
      dateLabel: '2026년 10월 20일 화요일',
      timeLabel: '17:12',
      isRead: true,
    },
  ],
}
