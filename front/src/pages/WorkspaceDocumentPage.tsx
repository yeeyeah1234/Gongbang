import { useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { getApiError, getErrorMessage } from '../api/errors'
import { Toast } from '../components/ui/Toast'
import { useLoginMember } from '../features/auth/store'
import { useWorkspace } from '../features/workspace/context'
import { DocumentEditorPane } from '../features/workspace/document/components/DocumentEditorPane'
import { DocumentEmptyState } from '../features/workspace/document/components/DocumentEmptyState'
import {
  documentKeys,
  useCreateDocument,
  useDeleteDocument,
  useDocumentList,
  useRefreshDocuments,
  useRenameDocument,
} from '../features/workspace/document/hooks'
import { readLastDocumentId, writeLastDocumentId } from '../features/workspace/document/lastOpened'
import type { DocumentListItem } from '../features/workspace/document/types'
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

const DOCUMENT_LIMIT_MESSAGE = '문서는 50개까지 만들 수 있어요'
const CREATE_FAILED_MESSAGE = '문서를 만들지 못했어요. 잠시 후 다시 시도해 주세요'
const GONE_MESSAGE = '이 문서는 삭제되었어요'
const DELETE_FORBIDDEN_MESSAGE = '작성자나 팀장만 지울 수 있어요'
const RENAME_FAILED_MESSAGE = '문서 이름을 바꾸지 못했어요. 잠시 후 다시 시도해 주세요'

const DOCUMENT_LABELS: WorkspaceSidebarLabels = {
  heading: '문서',
  navAriaLabel: '문서 목록',
  createButton: '새 문서',
  defaultTitle: '제목 없는 문서',
  renameInputLabel: '문서 이름',
  menuButtonLabel: '문서 메뉴',
  renameAction: '이름 바꾸기',
  deleteAction: '삭제',
}

interface WorkspaceDocumentPageProps {
  // 개발용 미리보기만 넘긴다. 기본은 /workspaces/:teamId/docs
  basePath?: string
  // 개발용 미리보기에서는 sync-server 없이 로컬 에디터로 연다
  offlinePreview?: boolean
}

function parseDocumentId(param: string | undefined): number | null {
  const id = Number(param)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

// DOC-01~10 문서 탭: /workspaces/:teamId/docs/:documentId?
export default function WorkspaceDocumentPage({
  basePath,
  offlinePreview = false,
}: WorkspaceDocumentPageProps) {
  const { teamId } = useWorkspace()
  // 팀이 바뀌면 토스트·편집 상태까지 처음부터
  return (
    <DocumentTab
      key={teamId}
      basePath={basePath ?? `/workspaces/${teamId}/docs`}
      offlinePreview={offlinePreview}
    />
  )
}

function DocumentTab({ basePath, offlinePreview }: { basePath: string; offlinePreview: boolean }) {
  const { teamId, summary, realtime, setActiveDocTitle } = useWorkspace()
  const { documentId: param } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const loginMember = useLoginMember()
  const toast = useTimedToast()

  const listQuery = useDocumentList(teamId)
  const documents = listQuery.data
  const refreshDocuments = useRefreshDocuments(teamId)
  const createMutation = useCreateDocument()
  const renameMutation = useRenameDocument()
  const deleteMutation = useDeleteDocument()

  const [renamingId, setRenamingId] = useState<number | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<WorkspaceSidebarItem | null>(null)

  const creatingRef = useRef(false)
  const deletingRef = useRef(false)
  // 내가 방금 삭제한 문서는 「이 문서는 삭제되었어요」 타인 삭제 토스트를 띄우지 않는다
  const selfDeletedIdsRef = useRef<Set<number>>(new Set())
  // WS DOCUMENT_DELETED와 이어지는 목록 재조회 사이에서 토스트가 두 번 뜨지 않게 막는다
  const notifiedDeletedIdsRef = useRef<Set<number>>(new Set())

  const selectedId = parseDocumentId(param)
  const selectedDoc =
    selectedId !== null ? documents?.find((item) => item.id === selectedId) : undefined

  // 팀에서 빠졌으면 WorkspaceLayout이 권한 없음 화면으로 바꾼다
  const listNotMember = listQuery.isError && getApiError(listQuery.error).code === 'NOT_TEAM_MEMBER'
  useEffect(() => {
    if (listNotMember) {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
    }
  }, [listNotMember, teamId, queryClient])

  // 열려 있는 문서를 브라우저에 기억해 둔다 (다시 들어왔을 때 같은 문서로 열기)
  useEffect(() => {
    if (selectedDoc) writeLastDocumentId(teamId, selectedDoc.id)
  }, [teamId, selectedDoc])

  // WS-03 ④ 열린 문서 제목이 바뀔 때만 접속자 awareness의 docTitle 갱신
  const selectedDocTitle = selectedDoc?.title ?? null
  useEffect(() => {
    setActiveDocTitle?.(selectedDocTitle)
  }, [setActiveDocTitle, selectedDocTitle])

  useEffect(() => {
    return () => {
      setActiveDocTitle?.(null)
    }
  }, [setActiveDocTitle])

  const openFirstOrEmpty = (excludeId?: number) => {
    const nextDoc = (documents ?? []).find((item) => item.id !== excludeId)
    if (nextDoc) {
      navigate(`${basePath}/${nextDoc.id}`, { replace: true })
    } else {
      writeLastDocumentId(teamId, null)
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

  // DOC-04 실시간: 내가 보고 있던 문서가 다른 사람에게 삭제되면 토스트 후 첫 문서로
  useEffect(() => {
    if (!realtime) return
    return realtime.subscribeTeamEvent((event) => {
      if (
        event.type === 'DOCUMENT_DELETED' &&
        event.targetId !== null &&
        event.targetId === selectedId
      ) {
        handleExternalDelete(event.targetId)
      }
    })
  }, [realtime, selectedId])

  // /docs (ID 없음) 진입 시 마지막으로 연 문서 → 없으면 목록 첫 문서
  const noId = param === undefined
  useEffect(() => {
    if (!noId || !documents || documents.length === 0) return
    const lastId = readLastDocumentId(teamId)
    const target = documents.find((item) => item.id === lastId) ?? documents[0]
    navigate(`${basePath}/${target.id}`, { replace: true })
  }, [noId, documents, teamId, basePath, navigate])

  // URL에 ID가 있는데 목록에서 사라졌거나(타인 삭제·없는 ID) 잘못된 ID인 경우 처리
  useEffect(() => {
    if (noId || documents === undefined) return
    if (selectedId === null) {
      handleInvalidId()
      return
    }
    if (!documents.some((item) => item.id === selectedId)) {
      if (selfDeletedIdsRef.current.has(selectedId)) {
        handleInvalidId(selectedId)
      } else {
        handleExternalDelete(selectedId)
      }
    }
  }, [noId, selectedId, documents])

  const handleCreate = () => {
    if (creatingRef.current || createMutation.isPending) return
    creatingRef.current = true
    createMutation.mutate(undefined, {
      onSuccess: (documentId) => {
        creatingRef.current = false
        selfDeletedIdsRef.current.delete(documentId)
        notifiedDeletedIdsRef.current.delete(documentId)
        const created: DocumentListItem = {
          id: documentId,
          title: DOCUMENT_LABELS.defaultTitle,
          updatedByNickname: loginMember?.nickname ?? '나',
          updatedAt: nowKstDateTime(),
          createdBy: summary.myMemberId,
          canDelete: true,
        }
        // 새 문서를 즉시 목록 맨 위에 띄워 놓고 이동한다 (뒤이어 invalidate로 서버 값 동기화)
        queryClient.setQueryData<DocumentListItem[]>(documentKeys.all(teamId), (prev) => [
          created,
          ...(prev ?? []).filter((item) => item.id !== documentId),
        ])
        writeLastDocumentId(teamId, documentId)
        setRenamingId(documentId)
        navigate(`${basePath}/${documentId}`)
      },
      onError: (error) => {
        creatingRef.current = false
        const { code } = getApiError(error)
        const status = axios.isAxiosError(error) ? error.response?.status : undefined
        if (code === 'NOT_TEAM_MEMBER') {
          void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
          return
        }
        if (code === 'DOCUMENT_LIMIT' || status === 409) {
          toast.show(DOCUMENT_LIMIT_MESSAGE)
          return
        }
        toast.show(CREATE_FAILED_MESSAGE)
      },
    })
  }

  const handleSubmitRename = (documentId: number, title: string) => {
    const current = documents?.find((item) => item.id === documentId)
    if (!current || current.title === title) {
      setRenamingId(null)
      return
    }
    renameMutation.mutate(
      { documentId, title },
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
            notifiedDeletedIdsRef.current.add(documentId)
            toast.show(GONE_MESSAGE)
            void refreshDocuments()
            if (documentId === selectedId) openFirstOrEmpty(documentId)
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
        // 지금 보고 있던 문서를 지웠으면 목록 첫 문서(없으면 빈 상태), 다른 문서를 지웠으면 현재 문서 유지
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
          void refreshDocuments()
          if (target.id === selectedId) openFirstOrEmpty(target.id)
          return
        }
        selfDeletedIdsRef.current.delete(target.id)
        if (code === 'NOT_DOCUMENT_AUTHOR' || status === 403) {
          toast.show(getErrorMessage(error, DELETE_FORBIDDEN_MESSAGE))
          setDeleteTarget(null)
          void refreshDocuments()
          return
        }
        toast.show(getErrorMessage(error))
      },
    })
  }

  const handleEditorGone = (deletedId: number) => {
    notifyExternalDelete(deletedId)
    void refreshDocuments()
  }

  const handleEditorForbidden = () => {
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
  }

  const empty = documents !== undefined && documents.length === 0
  const listFailed = listQuery.isError && !documents
  const modalOpen = deleteTarget !== null

  return (
    <>
      {/* inert: 공통 Modal에 포커스 가두기가 없어 Tab으로 뒤쪽 버튼을 누르지 못하게 */}
      <div inert={modalOpen} className="flex flex-1 flex-col lg:min-h-0">
        {empty ? (
          <DocumentEmptyState creating={createMutation.isPending} onCreate={handleCreate} />
        ) : listFailed ? (
          <section className="flex flex-1 items-center justify-center rounded-card border border-line bg-surface">
            <NoticeError onRetry={() => listQuery.refetch()} />
          </section>
        ) : (
          <section className="flex flex-1 flex-col rounded-card border border-line bg-surface lg:min-h-0 lg:flex-row lg:overflow-hidden">
            <WorkspaceItemSidebar
              items={documents}
              isError={listQuery.isError}
              basePath={basePath}
              selectedId={selectedId}
              renamingId={renamingId}
              creating={createMutation.isPending}
              renaming={renameMutation.isPending}
              labels={DOCUMENT_LABELS}
              onRetry={() => listQuery.refetch()}
              onCreate={handleCreate}
              onStartRename={(id) => setRenamingId(id)}
              onSubmitRename={handleSubmitRename}
              onCancelRename={() => setRenamingId(null)}
              onRequestDelete={(item) => setDeleteTarget(item)}
            />
            <div
              key={selectedDoc?.id ?? 'loading'}
              className="flex min-h-0 min-w-0 flex-1 flex-col lg:overflow-hidden"
            >
              {selectedDoc ? (
                <DocumentEditorPane
                  teamId={teamId}
                  documentId={selectedDoc.id}
                  title={selectedDoc.title}
                  offlinePreview={offlinePreview}
                  onDocumentGone={handleEditorGone}
                  onForbidden={handleEditorForbidden}
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
          title="문서 삭제"
          question={`‘${deleteTarget.title}’ 문서를 삭제할까요?`}
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
