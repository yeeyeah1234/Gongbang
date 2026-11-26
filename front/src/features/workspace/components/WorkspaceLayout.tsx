/**
 * WS-02 워크스페이스 공통 틀: 헤더(96) + 탭 바(72) + 고정 공지 배너(84, NTC-04) + 탭 화면(<Outlet />).
 * 사이트 GNB는 붙지 않는다 (router.tsx에서 SiteLayout 밖에 둔다). 팀원이 아니면(403) 권한 없음 화면만 보여 준다 (WS-01 ②).
 *
 * ── 탭 화면을 붙이는 방법 (김건우 님 채팅 등) ──
 * 1. router.tsx의 `/workspaces/:teamId` children에서 해당 탭 element를 자기 화면으로 바꾼다.
 *      { path: 'chat', element: <WorkspaceChatPage /> }
 * 2. 헤더·탭·고정 공지 배너는 이 레이아웃이 그리므로 탭 화면에서는 빼고 본문만 남긴다
 *    (지금 features/chat/components의 WorkspaceHeader·WorkspaceTabs·PinnedNoticeBanner는 필요 없어진다).
 * 3. 탭 화면에서 쓸 수 있는 값: `const { teamId, summary, refreshNoticeIndicators } = useWorkspace()`
 *      (features/workspace/context.ts)
 *    - summary: teamName, contestTitle, deadline('YYYY-MM-DD' | null), isLeader, myMemberId
 *    - refreshNoticeIndicators(): 고정 공지 배너와 공지 탭 배지를 다시 불러온다 (공지 고정·작성·삭제 뒤)
 * 4. 접속자 목록과 채팅 안 읽은 개수는 아직 API가 없어 이 컴포넌트의 props로 받는다
 *    (onlineMembers 기본 [], chatUnreadCount 기본 0). 실시간 연결이 생기면 이 안에서 훅으로 불러오게 바꾼다.
 *    헤더는 로그인한 나(features/auth/store.ts의 useLoginMember + summary)를 항상 맨 앞에 그린다.
 *    onlineMembers에 나를 넣어도 빼도 된다 (memberId로 중복 제거, features/workspace/onlineMembers.ts).
 * 5. 본문 영역은 flex 세로 칸이고 남은 높이를 채운다(flex-1, min-h-0). 탭 화면 맨 바깥은 flex-1로 두면 된다.
 */
import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate, useParams } from 'react-router'
import { getApiError } from '../../../api/errors'
import type { MemberColorIndex } from '../../../components/ui/Avatar'
import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import { useLoginMember } from '../../auth/store'
import { useChatRooms, useTeamMembers } from '../../chat/hooks'
import { useWorkspaceRealtime } from '../../chat/useWorkspaceRealtime'
import type { WorkspaceOutletContext } from '../context'
import {
  useNoticeUnreadCount,
  usePinnedNotice,
  useRefreshNoticeIndicators,
  useWorkspaceSummary,
  workspaceKeys,
} from '../hooks'
import { memberColorIndex } from '../onlineMembers'
import { usePinnedCollapsed } from '../pinnedCollapse'
import { WORKSPACE_TABS, type WorkspaceOnlineMember, type WorkspaceTabKey } from '../types'
import { useWorkspacePresence } from '../useWorkspacePresence'
import { useWorkspaceSyncSocket } from '../useWorkspaceSyncSocket'
import { PinnedNoticeBanner } from './PinnedNoticeBanner'
import { WorkspaceHeader } from './WorkspaceHeader'
import { WorkspaceNoAccess } from './WorkspaceNoAccess'
import { WorkspaceTabs } from './WorkspaceTabs'

// 「←」·권한 없음 화면이 돌아가는 워크스페이스 목록 (GNB와 같은 경로, WS-08)
export const WORKSPACE_LIST_PATH = '/workspaces'

const EMPTY_MEMBERS: WorkspaceOnlineMember[] = []

interface WorkspaceLayoutProps {
  onlineMembers?: WorkspaceOnlineMember[]
  chatUnreadCount?: number
}

export function WorkspaceLayout({
  onlineMembers = EMPTY_MEMBERS,
  chatUnreadCount = 0,
}: WorkspaceLayoutProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { pathname } = useLocation()
  const teamIdParam = useParams().teamId ?? ''
  const teamId = Number(teamIdParam)
  const basePath = `${WORKSPACE_LIST_PATH}/${teamIdParam}`
  const loginMember = useLoginMember()

  const [removedFromTeamId, setRemovedFromTeamId] = useState<number | null>(null)
  const removed = removedFromTeamId === teamId

  const markRemoved = useCallback(() => {
    setRemovedFromTeamId(teamId)
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.myTeams() })
  }, [teamId, queryClient])

  const summaryQuery = useWorkspaceSummary(teamId)
  const summary = summaryQuery.data
  const ready = summary !== undefined
  const pinned = usePinnedNotice(teamId, ready && !removed)
  const noticeUnread = useNoticeUnreadCount(teamId, ready && !removed)
  const refreshNoticeIndicators = useRefreshNoticeIndicators(teamId)
  const chatRoomsQuery = useChatRooms(teamId, ready && !removed)
  const teamMembersQuery = useTeamMembers(teamId, ready && !removed)
  const totalChatUnread = useMemo(() => {
    if (!chatRoomsQuery.data) return chatUnreadCount
    return chatRoomsQuery.data.reduce((sum, room) => sum + room.unreadCount, 0)
  }, [chatRoomsQuery.data, chatUnreadCount])

  const realtime = useWorkspaceRealtime({
    teamId,
    myMemberId: summary?.myMemberId ?? null,
    enabled: ready && !removed,
    onRefreshNotice: refreshNoticeIndicators,
    onKicked: markRemoved,
  })

  // WS-06 ④ 워크스페이스를 보고 있던 중(다른 탭에서 나가기·강퇴) 권한을 잃으면 즉시 안내 모달을 띄운다
  useEffect(() => {
    if (!ready || removed || summary?.myMemberId === undefined) return
    return realtime.subscribeTeamEvent((event) => {
      if (
        (event.type === 'MEMBER_LEFT' || event.type === 'MEMBER_KICKED') &&
        event.targetId === summary.myMemberId
      ) {
        markRemoved()
      }
    })
  }, [ready, removed, summary?.myMemberId, realtime, markRemoved])
  // 조회 실패(공지 API 머지 전 등)면 data가 없어 배너도 펼치기 버튼도 숨긴다
  const pinnedNotice = pinned.data ?? null
  const [pinnedCollapsed, setPinnedCollapsed] = usePinnedCollapsed(
    teamId,
    pinnedNotice?.noticeId ?? null,
  )

  const tabKey = pathname.slice(basePath.length + 1).split('/')[0]
  const currentTab = WORKSPACE_TABS.find((tab) => tab.key === tabKey)
  const activeTab: WorkspaceTabKey | null = currentTab?.key ?? null
  const tabLabel = currentTab?.label

  const [docTitleState, setDocTitleState] = useState<{
    teamId: number
    title: string | null
  }>({ teamId, title: null })
  const activeDocTitle =
    (activeTab === 'docs' || activeTab === 'board') && docTitleState.teamId === teamId
      ? docTitleState.title
      : null
  const setActiveDocTitle = useCallback(
    (title: string | null) => {
      setDocTitleState((prev) =>
        prev.teamId === teamId && prev.title === title ? prev : { teamId, title },
      )
    },
    [teamId],
  )

  useEffect(() => {
    if (tabLabel) document.title = `${tabLabel} | 공방`
  }, [tabLabel])

  const goList = () => navigate(WORKSPACE_LIST_PATH)

  // 숫자가 아닌 teamId나 403(팀원 아님·없는 팀)은 팀 정보 없이 권한 없음 화면
  const forbidden =
    !Number.isFinite(teamId) ||
    (summaryQuery.isError && getApiError(summaryQuery.error).code === 'NOT_TEAM_MEMBER')

  const myColorIndex: MemberColorIndex = useMemo(() => {
    const myMemberId = summary?.myMemberId
    if (myMemberId === undefined) return 1
    const matched = teamMembersQuery.data?.find((member) => member.memberId === myMemberId)
    return matched?.colorIndex ?? memberColorIndex(myMemberId)
  }, [summary?.myMemberId, teamMembersQuery.data])

  const handlePresenceForbidden = useCallback(() => {
    markRemoved()
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
  }, [markRemoved, queryClient, teamId])

  const syncEnabled = ready && !forbidden && !removed && loginMember !== null
  const syncSocket = useWorkspaceSyncSocket({
    teamId,
    enabled: syncEnabled,
  })

  const presenceMembers = useWorkspacePresence({
    teamId,
    enabled: syncEnabled,
    syncSocket,
    memberId: summary?.myMemberId ?? null,
    nickname: loginMember?.nickname ?? null,
    colorIndex: ready ? myColorIndex : null,
    leader: summary?.isLeader ?? false,
    tab: activeTab,
    docTitle: activeDocTitle,
    onForbidden: handlePresenceForbidden,
  })
  const resolvedOnlineMembers = onlineMembers.length > 0 ? onlineMembers : presenceMembers

  // 403·이탈 뒤 재조회가 500이면 오류만 바뀌고 예전 data는 남는다. 비워 두어야 빠진 팀이 다시 보이지 않는다
  useEffect(() => {
    if ((!forbidden && !removed) || !ready) return
    const queryKey = workspaceKeys.summary(teamId)
    queryClient.getQueryCache().find({ queryKey })?.setState({ data: undefined })
  }, [forbidden, removed, ready, teamId, queryClient])

  if (removed) {
    const goListAfterRemoved = () => navigate(WORKSPACE_LIST_PATH, { replace: true })
    return (
      <main className="min-h-screen bg-bg">
        <Modal
          open
          size="sm"
          title="워크스페이스를 볼 수 없어요"
          description="팀에서 나가 이 워크스페이스를 볼 수 없어요"
          onClose={goListAfterRemoved}
          footer={
            <Button variant="primary" size="md" onClick={goListAfterRemoved}>
              워크스페이스 목록으로
            </Button>
          }
        />
      </main>
    )
  }

  if (forbidden) return <WorkspaceNoAccess onGoList={goList} />

  // 처음 불러오기 실패만 오류 화면. 다시 불러오다 실패하면 탭 화면(작성 중인 내용)을 그대로 둔다
  if (summaryQuery.isError && !summary) {
    // TODO(확인 필요): 403 이외 오류(500·네트워크) 화면은 명세에 없다
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-bg px-6 text-center">
        <p className="text-body text-text-sub">
          워크스페이스를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
        <Button variant="outline" size="md" onClick={() => summaryQuery.refetch()}>
          다시 시도
        </Button>
      </main>
    )
  }

  if (!summary) {
    // 불러오는 동안 헤더·탭 자리만 잡아 둔다 (팀 정보는 아직 보여 주지 않음)
    return (
      <div className="flex min-h-screen flex-col bg-bg" aria-busy="true">
        <div className="h-16 shrink-0 border-b border-divider bg-surface sm:h-24" />
        <div className="h-14 shrink-0 border-b border-divider bg-surface sm:h-[72px]" />
      </div>
    )
  }

  const context: WorkspaceOutletContext = {
    teamId,
    summary,
    refreshNoticeIndicators,
    realtime,
    syncSocket,
    onlineMembers: resolvedOnlineMembers,
    myColorIndex,
    setActiveDocTitle,
  }
  // 헤더 맨 앞의 나: 닉네임은 로그인 상태, memberId·팀장 여부는 팀 요약, 색은 team_member.colorIndex
  const self: WorkspaceOnlineMember | null = loginMember
    ? {
        memberId: summary.myMemberId,
        nickname: loginMember.nickname,
        colorIndex: myColorIndex,
        isLeader: summary.isLeader,
        tab: activeTab,
        docTitle: activeDocTitle,
      }
    : null

  return (
    <div className="flex min-h-screen flex-col bg-bg lg:h-screen lg:overflow-hidden">
      <WorkspaceHeader
        teamName={summary.teamName}
        contestTitle={summary.contestTitle}
        deadline={summary.deadline}
        self={self}
        onlineMembers={resolvedOnlineMembers}
        onBack={goList}
      />
      <WorkspaceTabs
        basePath={basePath}
        chatUnreadCount={totalChatUnread}
        noticeUnreadCount={noticeUnread.data ?? 0}
        onExpandPinned={
          pinnedNotice && pinnedCollapsed ? () => setPinnedCollapsed(false) : undefined
        }
      />
      <PinnedNoticeBanner
        notice={pinnedNotice}
        collapsed={pinnedCollapsed}
        onCollapse={() => setPinnedCollapsed(true)}
        to={pinnedNotice ? `${basePath}/notice/${pinnedNotice.noticeId}` : undefined}
      />
      <main className="mx-auto flex min-h-0 w-full max-w-[1680px] flex-1 flex-col px-6 py-6 lg:px-12 xl:px-[120px]">
        <Outlet context={context} />
      </main>
    </div>
  )
}
