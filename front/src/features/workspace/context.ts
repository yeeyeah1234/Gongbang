import { useOutletContext } from 'react-router'
import type { WorkspaceRealtimeBridge } from '../chat/useWorkspaceRealtime'
import type { WorkspaceSummary } from './types'

// WorkspaceLayout이 탭 화면(<Outlet />)에 넘기는 값
export interface WorkspaceOutletContext {
  teamId: number
  summary: WorkspaceSummary
  // 고정 공지 배너와 공지 탭 배지를 다시 불러온다 (공지 고정·작성·삭제 뒤)
  refreshNoticeIndicators: () => Promise<void>
  realtime?: WorkspaceRealtimeBridge
}

// 탭 화면에서 쓴다: const { teamId, summary } = useWorkspace()
export function useWorkspace() {
  return useOutletContext<WorkspaceOutletContext>()
}
