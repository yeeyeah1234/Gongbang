// GET /api/teams/{teamId}/documents 응답 한 줄 (DOC-01)
export interface DocumentListItem {
  id: number
  title: string
  updatedByNickname: string
  updatedAt: string
  createdBy: number | null
  canDelete: boolean
}

// PATCH /api/documents/{documentId} 요청 (DOC-03)
export interface DocumentRenameRequest {
  title: string
}

// POST /api/teams/{teamId}/documents 응답 (DOC-02)
export interface DocumentTitleResponse {
  documentId: number
  title: string
}

// PATCH /api/documents/{documentId} 응답 (DOC-03)
export interface DocumentIdResponse {
  documentId: number
}
