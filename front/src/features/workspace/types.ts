import type { MemberColorIndex } from '../../components/ui/Avatar'

// 워크스페이스 탭. URL 마지막 조각과 같다: /workspaces/:teamId/{key}
export type WorkspaceTabKey = 'chat' | 'docs' | 'board' | 'calendar' | 'notice'

export const WORKSPACE_TABS: ReadonlyArray<{ key: WorkspaceTabKey; label: string }> = [
  { key: 'chat', label: '채팅' },
  { key: 'docs', label: '문서' },
  { key: 'board', label: '화이트보드' },
  { key: 'calendar', label: '캘린더' },
  { key: 'notice', label: '공지' },
]

// GET /api/teams/{teamId} (WS-02 헤더)
export interface WorkspaceSummary {
  teamId: number
  teamName: string
  // 모집글이나 값이 없으면 null
  contestTitle: string | null
  // 'YYYY-MM-DD'. 없으면 null
  deadline: string | null
  isLeader: boolean
  myMemberId: number
}

// GET /api/teams/{teamId}/notices/pinned (NTC-05). 고정 공지가 없으면 data null
export interface PinnedNotice {
  noticeId: number
  teamId: number
  title: string
  content: string
  author: { memberId: number | null; nickname: string }
  createdAt: string
  editedAt: string | null
  isPinned: boolean
  isEdited: boolean
}

// 헤더 「접속 중」 아바타. TODO(확인 필요): 실시간 접속자 API(채팅 WebSocket, 김건우)가 생기면 그 응답에 맞춘다
export interface WorkspaceOnlineMember {
  memberId: number
  nickname: string
  colorIndex: MemberColorIndex
  isLeader: boolean
}
