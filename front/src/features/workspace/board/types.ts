// GET /api/teams/{teamId}/whiteboards 응답 한 줄 (WB-01, API 명세서)
// lastEditor: 동기화 서버가 본문을 저장한 적이 없으면 null
export interface WhiteboardListItem {
  whiteboardId: number
  title: string
  creatorId: number
  lastEditor: { memberId: number; nickname: string } | null
  updatedAt: string
}

// PATCH /api/whiteboards/{whiteboardId} 요청 (WB-01)
export interface WhiteboardRenameRequest {
  title: string
}

// POST /api/teams/{teamId}/whiteboards · PATCH /api/whiteboards/{whiteboardId} 응답
export interface WhiteboardTitleResponse {
  whiteboardId: number
  title: string
}
