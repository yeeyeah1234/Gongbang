import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useMemo } from 'react'
import { useTeamMembers } from '../../chat/hooks'
import { useWorkspace } from '../context'
import { workspaceKeys } from '../hooks'
import type { WorkspaceSidebarItem } from '../sidebar/types'
import { createWhiteboard, deleteWhiteboard, fetchWhiteboards, renameWhiteboard } from './api'
import type { WhiteboardListItem } from './types'

export const boardKeys = {
  // ['workspace', teamId, 'boards']: useWorkspaceRealtime이 WHITEBOARD_* 이벤트에서 이 키를 invalidate한다
  all: (teamId: number) => [...workspaceKeys.all(teamId), 'boards'] as const,
}

export function useBoardList(teamId: number) {
  return useQuery({
    queryKey: boardKeys.all(teamId),
    queryFn: () => fetchWhiteboards(teamId),
    retry: false,
  })
}

/**
 * 공통 사이드바 항목 모양으로 바꾼다. 보드 API는 canDelete·마지막 수정자 닉네임을 주지 않으므로(API 명세서 WB-01) 여기서 채운다.
 * - canDelete: 팀장이거나 만든 사람 (화면 표시용, 서버가 다시 확인한다)
 * - updatedByNickname: lastEditor가 없으면(아직 그린 적 없음) 만든 사람 닉네임을 팀원 목록에서 찾는다
 */
export function useBoardSidebarItems(
  boards: WhiteboardListItem[] | undefined,
): WorkspaceSidebarItem[] | undefined {
  const { teamId, summary } = useWorkspace()
  const membersQuery = useTeamMembers(teamId)
  const members = membersQuery.data

  return useMemo(() => {
    if (!boards) return undefined
    const nicknames = new Map(members?.map((member) => [member.memberId, member.nickname]))
    return boards.map((board) => ({
      id: board.whiteboardId,
      title: board.title,
      // TODO(확인 필요): 만든 사람이 팀을 나갔으면 닉네임을 알 수 없어 「-」로 둔다 (명세서에 표시 규칙 없음)
      updatedByNickname: board.lastEditor?.nickname ?? nicknames.get(board.creatorId) ?? '-',
      updatedAt: board.updatedAt,
      canDelete: summary.isLeader || board.creatorId === summary.myMemberId,
    }))
  }, [boards, members, summary.isLeader, summary.myMemberId])
}

export function useRefreshBoards(teamId: number) {
  const client = useQueryClient()
  return useCallback(
    () => client.invalidateQueries({ queryKey: boardKeys.all(teamId) }),
    [client, teamId],
  )
}

// 쓰기 공통: networkMode always (오프라인이면 멈추지 않고 바로 실패해 토스트가 뜬다)
export function useCreateBoard() {
  const { teamId } = useWorkspace()
  const refresh = useRefreshBoards(teamId)
  return useMutation({
    mutationFn: () => createWhiteboard(teamId),
    networkMode: 'always',
    onSuccess: () => {
      void refresh()
    },
  })
}

export function useRenameBoard() {
  const { teamId } = useWorkspace()
  const client = useQueryClient()
  const refresh = useRefreshBoards(teamId)
  return useMutation({
    mutationFn: ({ boardId, title }: { boardId: number; title: string }) =>
      renameWhiteboard(boardId, { title }),
    networkMode: 'always',
    onSuccess: (data) => {
      client.setQueryData<WhiteboardListItem[]>(boardKeys.all(teamId), (prev) =>
        prev?.map((item) =>
          item.whiteboardId === data.whiteboardId ? { ...item, title: data.title } : item,
        ),
      )
      void refresh()
    },
  })
}

export function useDeleteBoard() {
  const { teamId } = useWorkspace()
  const client = useQueryClient()
  const refresh = useRefreshBoards(teamId)
  return useMutation({
    mutationFn: deleteWhiteboard,
    networkMode: 'always',
    onSuccess: (_data, boardId) => {
      // 먼저 목록 캐시에서 빼야 지운 보드로 다시 돌아가지 않는다
      client.setQueryData<WhiteboardListItem[]>(boardKeys.all(teamId), (prev) =>
        prev?.filter((item) => item.whiteboardId !== boardId),
      )
      void refresh()
    },
  })
}
