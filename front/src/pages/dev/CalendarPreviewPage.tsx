import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useLayoutEffect, useState } from 'react'
import { Navigate, NavLink, Outlet, useParams } from 'react-router'
import { WorkspaceTabs } from '../../features/workspace/components/WorkspaceTabs'
import type { WorkspaceOutletContext } from '../../features/workspace/context'
import { useRefreshNoticeIndicators, useWorkspaceSummary } from '../../features/workspace/hooks'
import { installPreviewApi, PREVIEW_TEAM_ID, type PreviewView } from './calendarPreviewApi'

// 개발 전용 (router.tsx가 lazy로 불러온다. 배포 빌드에는 들어가지 않음)
// 백엔드·로그인 없이 실제 캘린더 화면·훅·모달을 본다: /dev/calendar-preview/{view}/calendar
// - leader: 팀장. 모든 일반 일정을 고칠 수 있고 쓰기가 메모리에 반영된다. 「공모전 상세 보기」 링크 있음
// - member: 팀원. 내 일정만 고칠 수 있다. 외부 공모전 팀이라 링크 없음
// - empty: 일정 0개 / error: 일정 API 500
// 두 view 공통: 21일의 「권한이 바뀐 일정」은 쓰면 403, 「지워진 일정」은 쓰면 404
// 날짜는 이번 달 기준이다 (2026-11이면 11/28~12/03 여러 날 일정, 16일 자동 일정, 21일 「+2개」).
// 새로고침하면 처음 데이터로 돌아간다.
const BASE_PATH = '/dev/calendar-preview'
const VIEWS: PreviewView[] = ['leader', 'member', 'empty', 'error']

export default function CalendarPreviewPage() {
  const param = useParams().view
  const view = VIEWS.find((item) => item === param)
  if (!view) return <Navigate to={`${BASE_PATH}/leader`} replace />
  return <Preview key={view} view={view} />
}

// 미리보기에는 캘린더 탭만 있어 첫 진입과 다른 탭은 캘린더로 보낸다
export function CalendarPreviewRedirect() {
  return <Navigate to={`${BASE_PATH}/${useParams().view}/calendar`} replace />
}

function Preview({ view }: { view: PreviewView }) {
  const [client] = useState(() => new QueryClient())
  // 조회(useEffect)보다 먼저 가짜 API를 건다
  useLayoutEffect(() => installPreviewApi(view), [view])

  return (
    <QueryClientProvider client={client}>
      <PreviewFrame view={view} />
    </QueryClientProvider>
  )
}

// WorkspaceLayout과 같은 높이 규칙. 탭과 팀 요약은 실제 컴포넌트와 훅
function PreviewFrame({ view }: { view: PreviewView }) {
  const summary = useWorkspaceSummary(PREVIEW_TEAM_ID).data
  const refreshNoticeIndicators = useRefreshNoticeIndicators(PREVIEW_TEAM_ID)

  const context: WorkspaceOutletContext | null = summary
    ? { teamId: PREVIEW_TEAM_ID, summary, refreshNoticeIndicators }
    : null

  return (
    <div className="flex min-h-screen flex-col bg-bg lg:h-screen lg:overflow-hidden">
      {/* 헤더(96) 자리. Tailwind가 이 파일도 훑으므로 배포 화면에 이미 있는 class만 쓴다 */}
      <nav className="flex h-24 shrink-0 flex-wrap items-center gap-4 border-b border-divider bg-surface px-6">
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
      </nav>
      <WorkspaceTabs basePath={`${BASE_PATH}/${view}`} chatUnreadCount={0} noticeUnreadCount={0} />
      <main className="mx-auto flex min-h-0 w-full max-w-[1680px] flex-1 flex-col px-6 py-6 lg:px-12 xl:px-[120px]">
        {context && <Outlet context={context} />}
      </main>
    </div>
  )
}
