import { useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { getApiError, getErrorMessage } from '../api/errors'
import { Toast } from '../components/ui/Toast'
import { BoardCanvasPane } from '../features/workspace/board/components/BoardCanvasPane'
import { BoardEmptyState } from '../features/workspace/board/components/BoardEmptyState'
import {
  boardKeys,
  useBoardList,
  useBoardSidebarItems,
  useCreateBoard,
  useDeleteBoard,
  useRefreshBoards,
  useRenameBoard,
} from '../features/workspace/board/hooks'
import { readLastBoardId, writeLastBoardId } from '../features/workspace/board/lastOpened'
import type { WhiteboardListItem } from '../features/workspace/board/types'
import { useWorkspace } from '../features/workspace/context'
import { workspaceKeys } from '../features/workspace/hooks'
import { NoticeConfirmModal } from '../features/workspace/notice/components/NoticeConfirmModal'
import { NoticeError, NoticeSkeleton } from '../features/workspace/notice/components/NoticeStates'
import { nowKstDateTime } from '../features/workspace/sidebar/relativeTime'
import type {
  WorkspaceSidebarItem,
  WorkspaceSidebarLabels,
} from '../features/workspace/sidebar/types'
import { WorkspaceItemSidebar } from '../features/workspace/sidebar/WorkspaceItemSidebar'
import { useTimedToast } from '../hooks/useTimedToast'

const BOARD_LIMIT_MESSAGE = '보드는 30개까지 만들 수 있어요'
const CREATE_FAILED_MESSAGE = '보드를 만들지 못했어요. 잠시 후 다시 시도해 주세요'
const GONE_MESSAGE = '이 보드는 삭제되었어요'
const DELETE_FORBIDDEN_MESSAGE = '작성자나 팀장만 지울 수 있어요'
const RENAME_FAILED_MESSAGE = '보드 이름을 바꾸지 못했어요. 잠시 후 다시 시도해 주세요'

const BOARD_LABELS: WorkspaceSidebarLabels = {
  heading: '화이트보드',
  navAriaLabel: '보드 목록',
  createButton: '새 보드',
  defaultTitle: '제목 없는 보드',
  renameInputLabel: '보드 이름',
  menuButtonLabel: '보드 메뉴',
  renameAction: '이름 바꾸기',
  deleteAction: '삭제',
}

interface WorkspaceBoardPageProps {
  // 개발용 미리보기만 넘긴다. 기본은 /workspaces/:teamId/board
  basePath?: string
  // 개발용 미리보기에서는 sync-server 없이 로컬 캔버스로 연다
  offlinePreview?: boolean
}

function parseBoardId(param: string | undefined): number | null {
  const id = Number(param)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

// WB-01~10 화이트보드 탭: /workspaces/:teamId/board/:boardId? (문서 탭 WorkspaceDocumentPage와 같은 동작)
export default function WorkspaceBoardPage({
  basePath,
  offlinePreview = false,
}: WorkspaceBoardPageProps) {
  const { teamId } = useWorkspace()
  // 팀이 바뀌면 토스트·편집 상태까지 처음부터
  return (
    <BoardTab
      key={teamId}
      basePath={basePath ?? `/workspaces/${teamId}/board`}
      offlinePreview={offlinePreview}
    />
  )
}

function BoardTab({ basePath, offlinePreview }: { basePath: string; offlinePreview: boolean }) {
  const { teamId, summary, realtime, setActiveDocTitle } = useWorkspace()
  const { boardId: param } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useTimedToast()

  const listQuery = useBoardList(teamId)
  const boards = useBoardSidebarItems(listQuery.data)
  const refreshBoards = useRefreshBoards(teamId)
  const createMutation = useCreateBoard()
  const renameMutation = useRenameBoard()
  const deleteMutation = useDeleteBoard()

  const [renamingId, setRenamingId] = useState<number | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<WorkspaceSidebarItem | null>(null)

  const creatingRef = useRef(false)
  const deletingRef = useRef(false)
  // 내가 방금 삭제한 보드는 「이 보드는 삭제되었어요」 타인 삭제 토스트를 띄우지 않는다
  const selfDeletedIdsRef = useRef<Set<number>>(new Set())
  // WS WHITEBOARD_DELETED와 이어지는 목록 재조회 사이에서 토스트가 두 번 뜨지 않게 막는다
  const notifiedDeletedIdsRef = useRef<Set<number>>(new Set())

  const selectedId = parseBoardId(param)
  const selectedBoard =
    selectedId !== null ? boards?.find((item) => item.id === selectedId) : undefined

  // 팀에서 빠졌으면 WorkspaceLayout이 권한 없음 화면으로 바꾼다
  const listNotMember = listQuery.isError && getApiError(listQuery.error).code === 'NOT_TEAM_MEMBER'
  useEffect(() => {
    if (listNotMember) {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
    }
  }, [listNotMember, teamId, queryClient])

  // WS-02 ⑤ 열려 있는 보드를 브라우저에 기억해 둔다 (다시 들어왔을 때 같은 보드로 열기)
  useEffect(() => {
    if (selectedBoard) writeLastBoardId(teamId, selectedBoard.id)
  }, [teamId, selectedBoard])

  // WS-03 ④ 열린 보드 제목이 바뀔 때만 접속자 awareness 툴팁(화이트보드 > 보드명) 갱신
  const selectedBoardTitle = selectedBoard?.title ?? null
  useEffect(() => {
    setActiveDocTitle?.(selectedBoardTitle)
  }, [setActiveDocTitle, selectedBoardTitle])

  useEffect(() => {
    return () => {
      setActiveDocTitle?.(null)
    }
  }, [setActiveDocTitle])

  const openFirstOrEmpty = (excludeId?: number) => {
    const nextBoard = (boards ?? []).find((item) => item.id !== excludeId)
    if (nextBoard) {
      navigate(`${basePath}/${nextBoard.id}`, { replace: true })
    } else {
      writeLastBoardId(teamId, null)
      navigate(basePath, { replace: true })
    }
  }

  const handleInvalidId = useEffectEvent((excludeId?: number) => {
    openFirstOrEmpty(excludeId)
  })

  const notifyExternalDelete = (deletedId: number) => {
    if (selfDeletedIdsRef.current.has(deletedId) || notifiedDeletedIdsRef.current.has(deletedId)) {
      return
    }
    notifiedDeletedIdsRef.current.add(deletedId)
    if (renamingId === deletedId) setRenamingId(null)
    if (deleteTarget?.id === deletedId) setDeleteTarget(null)
    toast.show(GONE_MESSAGE)
    openFirstOrEmpty(deletedId)
  }

  const handleExternalDelete = useEffectEvent((deletedId: number) => {
    notifyExternalDelete(deletedId)
  })

  // 실시간: 내가 보고 있던 보드가 다른 사람에게 삭제되면 토스트 후 첫 보드로 (DOC-04 ④와 같음)
  useEffect(() => {
    if (!realtime) return
    return realtime.subscribeTeamEvent((event) => {
      if (
        event.type === 'WHITEBOARD_DELETED' &&
        event.targetId !== null &&
        event.targetId === selectedId
      ) {
        handleExternalDelete(event.targetId)
      }
    })
  }, [realtime, selectedId])

  // /board (ID 없음) 진입 시 마지막으로 연 보드 → 없으면 목록 첫 보드
  const noId = param === undefined
  useEffect(() => {
    if (!noId || !boards || boards.length === 0) return
    const lastId = readLastBoardId(teamId)
    const target = boards.find((item) => item.id === lastId) ?? boards[0]
    navigate(`${basePath}/${target.id}`, { replace: true })
  }, [noId, boards, teamId, basePath, navigate])

  // URL에 ID가 있는데 목록에서 사라졌거나(타인 삭제·없는 ID) 잘못된 ID인 경우 처리
  useEffect(() => {
    if (noId || boards === undefined) return
    if (selectedId === null) {
      handleInvalidId()
      return
    }
    if (!boards.some((item) => item.id === selectedId)) {
      if (selfDeletedIdsRef.current.has(selectedId)) {
        handleInvalidId(selectedId)
      } else {
        handleExternalDelete(selectedId)
      }
    }
  }, [noId, selectedId, boards])

  const handleCreate = () => {
    if (creatingRef.current || createMutation.isPending) return
    creatingRef.current = true
    createMutation.mutate(undefined, {
      onSuccess: ({ whiteboardId, title }) => {
        creatingRef.current = false
        selfDeletedIdsRef.current.delete(whiteboardId)
        notifiedDeletedIdsRef.current.delete(whiteboardId)
        const created: WhiteboardListItem = {
          whiteboardId,
          title,
          creatorId: summary.myMemberId,
          lastEditor: null,
          updatedAt: nowKstDateTime(),
        }
        // 새 보드를 즉시 목록 맨 위에 띄워 놓고 이동한다 (뒤이어 invalidate로 서버 값 동기화)
        queryClient.setQueryData<WhiteboardListItem[]>(boardKeys.all(teamId), (prev) => [
          created,
          ...(prev ?? []).filter((item) => item.whiteboardId !== whiteboardId),
        ])
        writeLastBoardId(teamId, whiteboardId)
        // WB-01 ② 새 항목은 이름 바꾸기 입력 상태
        setRenamingId(whiteboardId)
        navigate(`${basePath}/${whiteboardId}`)
      },
      onError: (error) => {
        creatingRef.current = false
        const { code } = getApiError(error)
        const status = axios.isAxiosError(error) ? error.response?.status : undefined
        if (code === 'NOT_TEAM_MEMBER') {
          void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
          return
        }
        if (code === 'WHITEBOARD_LIMIT' || status === 409) {
          toast.show(BOARD_LIMIT_MESSAGE)
          return
        }
        toast.show(CREATE_FAILED_MESSAGE)
      },
    })
  }

  const handleSubmitRename = (boardId: number, title: string) => {
    const current = boards?.find((item) => item.id === boardId)
    if (!current || current.title === title) {
      setRenamingId(null)
      return
    }
    renameMutation.mutate(
      { boardId, title },
      {
        onSuccess: () => {
          setRenamingId(null)
        },
        onError: (error) => {
          setRenamingId(null)
          const { code } = getApiError(error)
          const status = axios.isAxiosError(error) ? error.response?.status : undefined
          if (code === 'NOT_TEAM_MEMBER') {
            void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
            return
          }
          if (code === 'RESOURCE_NOT_FOUND' || status === 404) {
            notifiedDeletedIdsRef.current.add(boardId)
            toast.show(GONE_MESSAGE)
            void refreshBoards()
            if (boardId === selectedId) openFirstOrEmpty(boardId)
            return
          }
          toast.show(getErrorMessage(error, RENAME_FAILED_MESSAGE))
        },
      },
    )
  }

  const handleConfirmDelete = () => {
    if (!deleteTarget || deletingRef.current || deleteMutation.isPending) return
    deletingRef.current = true
    const target = deleteTarget
    selfDeletedIdsRef.current.add(target.id)

    deleteMutation.mutate(target.id, {
      onSuccess: () => {
        deletingRef.current = false
        setDeleteTarget(null)
        if (renamingId === target.id) setRenamingId(null)
        // 지금 보고 있던 보드를 지웠으면 목록 첫 보드(없으면 빈 상태), 다른 보드를 지웠으면 현재 보드 유지
        if (target.id === selectedId) openFirstOrEmpty(target.id)
      },
      onError: (error) => {
        deletingRef.current = false
        const { code } = getApiError(error)
        const status = axios.isAxiosError(error) ? error.response?.status : undefined
        if (code === 'NOT_TEAM_MEMBER') {
          selfDeletedIdsRef.current.delete(target.id)
          setDeleteTarget(null)
          void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
          return
        }
        if (code === 'RESOURCE_NOT_FOUND' || status === 404) {
          notifiedDeletedIdsRef.current.add(target.id)
          toast.show(GONE_MESSAGE)
          setDeleteTarget(null)
          void refreshBoards()
          if (target.id === selectedId) openFirstOrEmpty(target.id)
          return
        }
        selfDeletedIdsRef.current.delete(target.id)
        if (code === 'NOT_WHITEBOARD_AUTHOR' || status === 403) {
          toast.show(getErrorMessage(error, DELETE_FORBIDDEN_MESSAGE))
          setDeleteTarget(null)
          // 팀장 위임 등으로 권한이 바뀌었을 수 있어 팀 요약(isLeader)과 목록을 다시 읽는다
          void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
          void refreshBoards()
          return
        }
        toast.show(getErrorMessage(error))
      },
    })
  }

  const handleBoardGone = (deletedId: number) => {
    notifyExternalDelete(deletedId)
    void refreshBoards()
  }

  const handleBoardForbidden = () => {
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
  }

  const empty = boards !== undefined && boards.length === 0
  const listFailed = listQuery.isError && !boards
  const modalOpen = deleteTarget !== null

  return (
    <>
      {/* inert: 공통 Modal에 포커스 가두기가 없어 Tab으로 뒤쪽 버튼을 누르지 못하게 */}
      <div inert={modalOpen} className="flex flex-1 flex-col lg:min-h-0">
        {empty ? (
          <BoardEmptyState creating={createMutation.isPending} onCreate={handleCreate} />
        ) : listFailed ? (
          <section className="flex flex-1 items-center justify-center rounded-card border border-line bg-surface">
            <NoticeError onRetry={() => listQuery.refetch()} />
          </section>
        ) : (
          <section className="flex flex-1 flex-col rounded-card border border-line bg-surface lg:min-h-0 lg:flex-row lg:overflow-hidden">
            <WorkspaceItemSidebar
              items={boards}
              isError={listQuery.isError}
              basePath={basePath}
              selectedId={selectedId}
              renamingId={renamingId}
              creating={createMutation.isPending}
              renaming={renameMutation.isPending}
              labels={BOARD_LABELS}
              onRetry={() => listQuery.refetch()}
              onCreate={handleCreate}
              onStartRename={(id) => setRenamingId(id)}
              onSubmitRename={handleSubmitRename}
              onCancelRename={() => setRenamingId(null)}
              onRequestDelete={(item) => setDeleteTarget(item)}
            />
            <div
              key={selectedBoard?.id ?? 'loading'}
              className="flex min-h-0 min-w-0 flex-1 flex-col lg:overflow-hidden"
            >
              {selectedBoard ? (
                <BoardCanvasPane
                  teamId={teamId}
                  boardId={selectedBoard.id}
                  offlinePreview={offlinePreview}
                  onBoardGone={handleBoardGone}
                  onForbidden={handleBoardForbidden}
                />
              ) : (
                <NoticeSkeleton />
              )}
            </div>
          </section>
        )}
      </div>

      {deleteTarget && (
        <NoticeConfirmModal
          title="보드 삭제"
          question={`‘${deleteTarget.title}’ 보드를 삭제할까요?`}
          note="삭제하면 되돌릴 수 없어요."
          confirmLabel="삭제"
          danger
          busy={deleteMutation.isPending}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      )}

      {/* Modal과 z-index가 같아 뒤에 그려야 위에 보인다 */}
      <Toast message={toast.message} />
    </>
  )
}
