import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useCallback, useLayoutEffect, useMemo, useRef, useState, type RefObject } from 'react'
import { Navigate, NavLink, Outlet, useParams } from 'react-router'
import { Button } from '../../components/ui/Button'
import type { TeamEventPayload } from '../../features/chat/types'
import type {
  TeamEventListener,
  WorkspaceRealtimeBridge,
} from '../../features/chat/useWorkspaceRealtime'
import { WorkspaceTabs } from '../../features/workspace/components/WorkspaceTabs'
import type { WorkspaceOutletContext } from '../../features/workspace/context'
import { boardKeys } from '../../features/workspace/board/hooks'
import { useRefreshNoticeIndicators, useWorkspaceSummary } from '../../features/workspace/hooks'
import { nowKstDateTime } from '../../features/workspace/sidebar/relativeTime'
import WorkspaceBoardPage from '../WorkspaceBoardPage'
import {
  installBoardPreviewApi,
  PREVIEW_TEAM_ID,
  type BoardPreviewController,
  type PreviewView,
} from './boardPreviewApi'

// 개발 전용 (router.tsx가 lazy로 불러온다. 배포 빌드에는 들어가지 않음)
// 백엔드·로그인 없이 WB-01 화이트보드 사이드바를 본다: /dev/board-preview/{view}/board/:boardId?
// (문서 미리보기를 옮긴 것)
// - normal: 팀장. 모든 보드 삭제 가능, 상대 시각·긴 제목·아직 안 그린 보드(만든 사람 표시)·403·404·30개 제한·타인 삭제 확인
// - member: 일반 팀원. 본인이 만든 보드만 ⋮ 메뉴에 「삭제」 노출
// - empty: 보드 0개 빈 상태 → 「+ 새 보드」로 첫 보드 생성
// - error: 목록 API 500 → 「불러오지 못했어요」 + 다시 시도
const BASE_PATH = '/dev/board-preview'
const VIEWS: PreviewView[] = ['normal', 'empty', 'error', 'member']

export default function BoardPreviewPage() {
  const param = useParams().view
  const view = VIEWS.find((item) => item === param)
  if (!view) return <Navigate to={`${BASE_PATH}/normal`} replace />
  return <Preview key={view} view={view} />
}

export function BoardPreviewTab() {
  return <WorkspaceBoardPage basePath={`${BASE_PATH}/${useParams().view}/board`} offlinePreview />
}

// 미리보기에는 화이트보드 탭만 있어 첫 진입과 다른 탭은 화이트보드로 보낸다
export function BoardPreviewRedirect() {
  return <Navigate to={`${BASE_PATH}/${useParams().view}/board`} replace />
}

function Preview({ view }: { view: PreviewView }) {
  const [client] = useState(() => new QueryClient())
  const controllerRef = useRef<BoardPreviewController | null>(null)
  const listenersRef = useRef<Set<TeamEventListener>>(new Set())

  // 조회(useEffect)보다 먼저 가짜 API를 건다
  useLayoutEffect(() => {
    const controller = installBoardPreviewApi(view)
    controllerRef.current = controller
    return () => {
      controller.cleanup()
      controllerRef.current = null
    }
  }, [view])

  const emitTeamEvent = useCallback((event: TeamEventPayload) => {
    listenersRef.current.forEach((listener) => listener(event))
  }, [])

  const realtime = useMemo<WorkspaceRealtimeBridge>(
    () => ({
      connected: true,
      subscribeRoom: () => () => {},
      subscribeTeamEvent: (listener) => {
        listenersRef.current.add(listener)
        return () => {
          listenersRef.current.delete(listener)
        }
      },
      sendRoomMessage: () => false,
    }),
    [],
  )

  return (
    <QueryClientProvider client={client}>
      <PreviewFrame
        view={view}
        client={client}
        controllerRef={controllerRef}
        realtime={realtime}
        emitTeamEvent={emitTeamEvent}
      />
    </QueryClientProvider>
  )
}

interface PreviewFrameProps {
  view: PreviewView
  client: QueryClient
  controllerRef: RefObject<BoardPreviewController | null>
  realtime: WorkspaceRealtimeBridge
  emitTeamEvent: (event: TeamEventPayload) => void
}

// WorkspaceLayout과 같은 높이 규칙. 탭과 팀 요약은 실제 컴포넌트와 훅
function PreviewFrame({ view, client, controllerRef, realtime, emitTeamEvent }: PreviewFrameProps) {
  const { boardId: boardIdParam } = useParams()
  const summary = useWorkspaceSummary(PREVIEW_TEAM_ID).data
  const refreshNoticeIndicators = useRefreshNoticeIndicators(PREVIEW_TEAM_ID)

  const context: WorkspaceOutletContext | null = summary
    ? { teamId: PREVIEW_TEAM_ID, summary, refreshNoticeIndicators, realtime }
    : null

  const handleFillLimit = () => {
    controllerRef.current?.fillToLimit()
    void client.invalidateQueries({ queryKey: boardKeys.all(PREVIEW_TEAM_ID) })
  }

  const handleSimulateExternalDelete = () => {
    const id = Number(boardIdParam)
    if (!Number.isSafeInteger(id) || id <= 0) return
    const removed = controllerRef.current?.removeExternally(id)
    if (!removed) return
    emitTeamEvent({
      type: 'WHITEBOARD_DELETED',
      teamId: PREVIEW_TEAM_ID,
      targetId: id,
      actorId: null,
      at: nowKstDateTime(),
    })
    void client.invalidateQueries({ queryKey: boardKeys.all(PREVIEW_TEAM_ID) })
  }

  return (
    <div className="flex min-h-screen flex-col bg-bg lg:h-screen lg:overflow-hidden">
      {/* 헤더(96) 자리. Tailwind가 이 파일도 훑으므로 배포 화면에 이미 있는 class만 쓴다 */}
      <nav className="flex h-24 shrink-0 flex-wrap items-center justify-between gap-4 border-b border-divider bg-surface px-6">
        <div className="flex flex-wrap items-center gap-4">
          {VIEWS.map((item) => (
            <NavLink
              key={item}
              to={`${BASE_PATH}/${item}`}
              className={({ isActive }) =>
                isActive ? 'text-body font-bold text-text' : 'text-body text-text-sub'
              }
            >
              {item}
            </NavLink>
          ))}
        </div>
        {(view === 'normal' || view === 'member') && (
          <div className="flex flex-wrap items-center gap-2">
            <Button size="sm" variant="outline" onClick={handleFillLimit}>
              30개 채우기 (409 테스트)
            </Button>
            <Button size="sm" variant="danger" onClick={handleSimulateExternalDelete}>
              현재 보드 타인 삭제
            </Button>
          </div>
        )}
      </nav>
      <WorkspaceTabs basePath={`${BASE_PATH}/${view}`} chatUnreadCount={0} noticeUnreadCount={0} />
      <main className="mx-auto flex min-h-0 w-full max-w-[1680px] flex-1 flex-col px-6 py-6 lg:px-12 xl:px-[120px]">
        {context && <Outlet context={context} />}
      </main>
    </div>
  )
}
