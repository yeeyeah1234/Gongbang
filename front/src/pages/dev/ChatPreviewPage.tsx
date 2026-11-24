import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useLayoutEffect, useMemo, useState } from 'react'
import type { WorkspaceRealtimeBridge } from '../../features/chat/useWorkspaceRealtime'
import { WorkspaceHeader } from '../../features/workspace/components/WorkspaceHeader'
import { WorkspaceTabs } from '../../features/workspace/components/WorkspaceTabs'
import type { WorkspaceOutletContext } from '../../features/workspace/context'
import { useRefreshNoticeIndicators, useWorkspaceSummary } from '../../features/workspace/hooks'
import WorkspaceChatPage from '../WorkspaceChatPage'
import { installChatPreviewApi, PREVIEW_TEAM_ID } from './chatPreviewApi'
import { Outlet } from 'react-router'

// 개발 전용 (router.tsx가 lazy로 불러온다. 배포 빌드에는 들어가지 않음)
// 백엔드·로그인 없이 NET-04·05·06 채팅 탭을 본다: /dev/chat-preview
// 1024px 미만에서 메신저형(목록 → 방 화면, ← 돌아가기, ≡ 방 설정/팀원 관리) 동작을 확인하는 용도.
// 메시지 보내기는 실시간 서버가 없어 「연결 중」 안내만 뜬다.
export default function ChatPreviewPage() {
  const [client] = useState(() => new QueryClient())

  // 조회(useEffect)보다 먼저 가짜 API를 건다 (자식의 useEffect는 부모의 useLayoutEffect 뒤에 돈다)
  useLayoutEffect(() => installChatPreviewApi(), [])

  return (
    <QueryClientProvider client={client}>
      <PreviewFrame />
    </QueryClientProvider>
  )
}

export function ChatPreviewTab() {
  return <WorkspaceChatPage />
}

// WorkspaceLayout과 같은 틀(헤더·탭·본문 여백). 팀 요약은 실제 훅으로 가짜 API에서 읽는다
function PreviewFrame() {
  const summary = useWorkspaceSummary(PREVIEW_TEAM_ID).data
  const refreshNoticeIndicators = useRefreshNoticeIndicators(PREVIEW_TEAM_ID)
  const realtime = useMemo<WorkspaceRealtimeBridge>(
    () => ({
      connected: false,
      subscribeRoom: () => () => {},
      subscribeTeamEvent: () => () => {},
      sendRoomMessage: () => false,
    }),
    [],
  )

  if (!summary) return null
  const context: WorkspaceOutletContext = {
    teamId: PREVIEW_TEAM_ID,
    summary,
    refreshNoticeIndicators,
    realtime,
  }

  return (
    <div className="flex min-h-screen flex-col bg-bg lg:h-screen lg:overflow-hidden">
      <WorkspaceHeader
        teamName={summary.teamName}
        contestTitle={summary.contestTitle}
        deadline={summary.deadline}
        self={{ memberId: 1, nickname: '김하늘', colorIndex: 1, isLeader: true }}
        onlineMembers={[]}
        onBack={() => {}}
      />
      <WorkspaceTabs basePath="/dev/chat-preview" chatUnreadCount={4} noticeUnreadCount={0} />
      <main className="mx-auto flex min-h-0 w-full max-w-[1680px] flex-1 flex-col px-6 py-6 lg:px-12 xl:px-[120px]">
        <Outlet context={context} />
      </main>
    </div>
  )
}
