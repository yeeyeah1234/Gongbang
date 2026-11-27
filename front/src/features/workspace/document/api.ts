import { apiClient } from '../../../api/client'
import type { ApiResponse } from '../../../api/types'
import type {
  DocumentIdResponse,
  DocumentListItem,
  DocumentRenameRequest,
  DocumentTitleResponse,
} from './types'

// DOC-01 문서 목록 (최근 수정순)
export async function fetchDocuments(teamId: number) {
  return (await apiClient.get<ApiResponse<DocumentListItem[]>>(`/api/teams/${teamId}/documents`))
    .data.data
}

// DOC-02 새 문서 생성. 50개 초과 시 409 DOCUMENT_LIMIT
export async function createDocument(teamId: number) {
  return (
    await apiClient.post<ApiResponse<DocumentTitleResponse>>(`/api/teams/${teamId}/documents`)
  ).data.data
}

// DOC-03 문서 이름 바꾸기
export async function renameDocument(documentId: number, body: DocumentRenameRequest) {
  return (
    await apiClient.patch<ApiResponse<DocumentIdResponse>>(`/api/documents/${documentId}`, body)
  ).data.data.documentId
}

// DOC-04 문서 삭제 (작성자·팀장만, 아니면 403 NOT_DOCUMENT_AUTHOR)
export async function deleteDocument(documentId: number) {
  await apiClient.delete(`/api/documents/${documentId}`)
}
