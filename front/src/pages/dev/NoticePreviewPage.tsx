import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useLayoutEffect, useState } from 'react'
import { Navigate, NavLink, Outlet, useParams } from 'react-router'
import { PinnedNoticeBanner } from '../../features/workspace/components/PinnedNoticeBanner'
import { WorkspaceTabs } from '../../features/workspace/components/WorkspaceTabs'
import type { WorkspaceOutletContext } from '../../features/workspace/context'
import {
  useNoticeUnreadCount,
  usePinnedNotice,
  useRefreshNoticeIndicators,
} from '../../features/workspace/hooks'
import { usePinnedCollapsed } from '../../features/workspace/pinnedCollapse'
import WorkspaceNoticePage from '../WorkspaceNoticePage'
import {
  installPreviewApi,
  PREVIEW_LEADER_ID,
  PREVIEW_MEMBER_ID,
  PREVIEW_TEAM_ID,
  type PreviewView,
} from './noticePreviewApi'

// 개발 전용 (router.tsx가 lazy로 불러온다. 배포 빌드에는 들어가지 않음)
// 백엔드·로그인 없이 실제 공지 화면과 훅을 본다: /dev/notice-preview/{leader|member|empty|error}/notice/:noticeId?
// 22번은 404, 주소에 999를 넣으면 다른 팀 공지.
const BASE_PATH = '/dev/notice-preview'
const VIEWS: PreviewView[] = ['leader', 'member', 'empty', 'error']

export default function NoticePreviewPage() {
  const param = useParams().view
  const view = VIEWS.find((item) => item === param)
  if (!view) return <Navigate to={`${BASE_PATH}/leader`} replace />
  return <Preview key={view} view={view} />
}

export function NoticePreviewTab() {
  return <WorkspaceNoticePage basePath={`${BASE_PATH}/${useParams().view}/notice`} />
}

// 미리보기에는 공지 탭만 있어 첫 진입과 다른 탭은 공지로 보낸다
export function NoticePreviewRedirect() {
  return <Navigate to={`${BASE_PATH}/${useParams().view}/notice`} replace />
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

// WorkspaceLayout과 같은 높이 규칙. 탭·배너·접힘·배지는 실제 컴포넌트와 훅
function PreviewFrame({ view }: { view: PreviewView }) {
  const pinned = usePinnedNotice(PREVIEW_TEAM_ID, true).data ?? null
  const unread = useNoticeUnreadCount(PREVIEW_TEAM_ID, true).data ?? 0
  const [collapsed, setCollapsed] = usePinnedCollapsed(PREVIEW_TEAM_ID, pinned?.noticeId ?? null)
  const basePath = `${BASE_PATH}/${view}`

  const context: WorkspaceOutletContext = {
    teamId: PREVIEW_TEAM_ID,
    refreshNoticeIndicators: useRefreshNoticeIndicators(PREVIEW_TEAM_ID),
    summary: {
      teamId: PREVIEW_TEAM_ID,
      teamName: '공방팀',
      contestTitle: null,
      deadline: null,
      isLeader: view !== 'member',
      myMemberId: view === 'member' ? PREVIEW_MEMBER_ID : PREVIEW_LEADER_ID,
    },
  }

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
      <WorkspaceTabs
        basePath={basePath}
        chatUnreadCount={0}
        noticeUnreadCount={unread}
        onExpandPinned={pinned && collapsed ? () => setCollapsed(false) : undefined}
      />
      <PinnedNoticeBanner
        notice={pinned}
        collapsed={collapsed}
        onCollapse={() => setCollapsed(true)}
        to={pinned ? `${basePath}/notice/${pinned.noticeId}` : undefined}
      />
      <main className="mx-auto flex min-h-0 w-full max-w-[1680px] flex-1 flex-col px-6 py-6 lg:px-12 xl:px-[120px]">
        <Outlet context={context} />
      </main>
    </div>
  )
}
