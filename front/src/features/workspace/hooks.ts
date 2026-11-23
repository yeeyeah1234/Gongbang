import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import {
  fetchMyTeams,
  fetchNoticeUnreadCount,
  fetchPinnedNotice,
  fetchWorkspaceSummary,
  renameTeam,
} from './api'

// 공지 화면(NTC)에서 고정·작성·삭제 뒤에는 workspaceKeys.notice(teamId)를 invalidate하면
// 고정 공지 배너와 공지 탭 배지가 함께 다시 불러와진다 (useRefreshNoticeIndicators).
export const workspaceKeys = {
  // WS-08 워크스페이스 팀 목록
  myTeams: () => ['workspace', 'my-teams'] as const,
  all: (teamId: number) => ['workspace', teamId] as const,
  summary: (teamId: number) => ['workspace', teamId, 'summary'] as const,
  notice: (teamId: number) => ['workspace', teamId, 'notice'] as const,
  pinnedNotice: (teamId: number) => ['workspace', teamId, 'notice', 'pinned'] as const,
  noticeUnread: (teamId: number) => ['workspace', teamId, 'notice', 'unread-count'] as const,
}

export function useWorkspaceSummary(teamId: number) {
  return useQuery({
    queryKey: workspaceKeys.summary(teamId),
    queryFn: () => fetchWorkspaceSummary(teamId),
    enabled: Number.isFinite(teamId),
    // 403(권한 없음)은 다시 물어도 같으므로 바로 화면을 바꾼다
    retry: false,
  })
}

// 실패해도(공지 API 머지 전 404 등) 배너만 숨기고 화면은 정상 (enabled: 요약이 성공한 뒤)
export function usePinnedNotice(teamId: number, enabled: boolean) {
  return useQuery({
    queryKey: workspaceKeys.pinnedNotice(teamId),
    queryFn: () => fetchPinnedNotice(teamId),
    enabled,
    retry: false,
  })
}

export function useNoticeUnreadCount(teamId: number, enabled: boolean) {
  return useQuery({
    queryKey: workspaceKeys.noticeUnread(teamId),
    queryFn: () => fetchNoticeUnreadCount(teamId),
    enabled,
    retry: false,
  })
}

// 고정 공지 배너 + 공지 탭 배지를 다시 불러온다
export function useRefreshNoticeIndicators(teamId: number) {
  const queryClient = useQueryClient()
  return useCallback(
    () => queryClient.invalidateQueries({ queryKey: workspaceKeys.notice(teamId) }),
    [queryClient, teamId],
  )
}

// WS-08 워크스페이스 팀 목록
export function useMyTeams() {
  return useQuery({ queryKey: workspaceKeys.myTeams(), queryFn: fetchMyTeams })
}

// WS-08 ⑧ 팀 이름 변경: 저장하면 팀 목록과 그 팀 워크스페이스 헤더를 다시 불러온다
// 다른 팀원 화면은 TEAM_RENAMED 팀 이벤트(워크스페이스 안) 또는 목록을 다시 열 때 바뀐다
export function useRenameTeam() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ teamId, name }: { teamId: number; name: string }) => renameTeam(teamId, name),
    onSuccess: (_data, { teamId }) => {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.myTeams() })
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
    },
  })
}
