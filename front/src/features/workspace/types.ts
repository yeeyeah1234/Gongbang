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
  // 캘린더 자동 일정의 「공모전 상세 보기」 링크용. 외부 공모전을 직접 입력했거나 모집글이 없으면 null
  contestId: number | null
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

// WS-03 헤더 「접속 중」 아바타 (sync-server team:{teamId} awareness 기반).
// 나는 헤더가 따로 맨 앞에 붙이므로 목록에 있어도 memberId로 중복 제거된다 (onlineMembers.ts).
export interface WorkspaceOnlineMember {
  memberId: number
  nickname: string
  colorIndex: MemberColorIndex
  isLeader: boolean
  tab?: WorkspaceTabKey | null
  docTitle?: string | null
}

// GET /api/teams (WS-08 워크스페이스 팀 목록 카드)
// TODO(확인 필요): API 명세서의 lastMessage·unreadCount는 채팅(김건우) 데이터라 아직 내려오지 않는다
export interface MyTeam {
  teamId: number
  name: string
  // 모집글이나 값이 없으면 null
  contestTitle: string | null
  // 'YYYY-MM-DD'. 없으면 null
  deadline: string | null
  // 마감일 - 오늘(KST). 마감일이 없으면 null, 지나면 음수
  dDay: number | null
  isEnded: boolean
  isLeader: boolean
  memberCount: number
  members: Array<{
    memberId: number
    nickname: string
    profileImageUrl: string | null
    colorIndex: MemberColorIndex
  }>
  lastActivityAt: string
}
