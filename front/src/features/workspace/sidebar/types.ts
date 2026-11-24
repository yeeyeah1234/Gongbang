// 문서·화이트보드 사이드바 공통 항목 형태 (DOC-01, WB-01)
export interface WorkspaceSidebarItem {
  id: number
  title: string
  updatedByNickname: string
  updatedAt: string
  canDelete: boolean
}

export interface WorkspaceSidebarLabels {
  heading: string
  navAriaLabel: string
  createButton: string
  defaultTitle: string
  renameInputLabel: string
  menuButtonLabel: string
  renameAction: string
  deleteAction: string
}
