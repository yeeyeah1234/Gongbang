import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { useWorkspace } from '../context'
import { workspaceKeys } from '../hooks'
import { createDocument, deleteDocument, fetchDocuments, renameDocument } from './api'
import type { DocumentListItem } from './types'

export const documentKeys = {
  // ['workspace', teamId, 'documents']: useWorkspaceRealtime이 DOCUMENT_* 이벤트에서 이 키를 invalidate한다
  all: (teamId: number) => [...workspaceKeys.all(teamId), 'documents'] as const,
}

export function useDocumentList(teamId: number) {
  return useQuery({
    queryKey: documentKeys.all(teamId),
    queryFn: () => fetchDocuments(teamId),
    retry: false,
  })
}

export function useRefreshDocuments(teamId: number) {
  const client = useQueryClient()
  return useCallback(
    () => client.invalidateQueries({ queryKey: documentKeys.all(teamId) }),
    [client, teamId],
  )
}

// 쓰기 공통: networkMode always (오프라인이면 멈추지 않고 바로 실패해 토스트가 뜬다)
export function useCreateDocument() {
  const { teamId } = useWorkspace()
  const refresh = useRefreshDocuments(teamId)
  return useMutation({
    mutationFn: () => createDocument(teamId),
    networkMode: 'always',
    onSuccess: () => {
      void refresh()
    },
  })
}

export function useRenameDocument() {
  const { teamId } = useWorkspace()
  const client = useQueryClient()
  const refresh = useRefreshDocuments(teamId)
  return useMutation({
    mutationFn: ({ documentId, title }: { documentId: number; title: string }) =>
      renameDocument(documentId, { title }),
    networkMode: 'always',
    onSuccess: (_data, { documentId, title }) => {
      client.setQueryData<DocumentListItem[]>(documentKeys.all(teamId), (prev) =>
        prev?.map((item) => (item.id === documentId ? { ...item, title } : item)),
      )
      void refresh()
    },
  })
}

export function useDeleteDocument() {
  const { teamId } = useWorkspace()
  const client = useQueryClient()
  const refresh = useRefreshDocuments(teamId)
  return useMutation({
    mutationFn: deleteDocument,
    networkMode: 'always',
    onSuccess: (_data, documentId) => {
      // 먼저 목록 캐시에서 빼야 지운 문서로 다시 돌아가지 않는다
      client.setQueryData<DocumentListItem[]>(documentKeys.all(teamId), (prev) =>
        prev?.filter((item) => item.id !== documentId),
      )
      void refresh()
    },
  })
}
