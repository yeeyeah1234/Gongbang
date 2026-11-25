import type { HocuspocusProviderWebsocket } from '@hocuspocus/provider'
import { useOutletContext } from 'react-router'
import type { MemberColorIndex } from '../../components/ui/Avatar'
import type { WorkspaceRealtimeBridge } from '../chat/useWorkspaceRealtime'
import type { WorkspaceOnlineMember, WorkspaceSummary } from './types'

// WorkspaceLayout이 탭 화면(<Outlet />)에 넘기는 값
export interface WorkspaceOutletContext {
  teamId: number
  summary: WorkspaceSummary
  // 고정 공지 배너와 공지 탭 배지를 다시 불러온다 (공지 고정·작성·삭제 뒤)
  refreshNoticeIndicators: () => Promise<void>
  realtime?: WorkspaceRealtimeBridge
  // WS-03·DOC-08: 팀 단위 공유 Hocuspocus WebSocket (team:{teamId}과 doc:{id}가 공유)
  syncSocket?: HocuspocusProviderWebsocket | null
  // WS-03: 현재 워크스페이스 접속자 목록과 내 고유 색(1~8)
  onlineMembers?: WorkspaceOnlineMember[]
  myColorIndex?: MemberColorIndex
  // 문서 탭에서 열린 문서 제목이 바뀔 때만 접속자 툴팁(문서 > 제목)을 갱신한다
  setActiveDocTitle?: (title: string | null) => void
}

// 탭 화면에서 쓴다: const { teamId, summary } = useWorkspace()
export function useWorkspace() {
  return useOutletContext<WorkspaceOutletContext>()
}
