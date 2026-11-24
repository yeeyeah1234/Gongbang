import { DocumentIcon } from '../../components/WorkspaceIcons'

interface DocumentEditorPlaceholderProps {
  teamId: number
  documentId: number
  title: string
}

// 다음 작업에서 documentId를 받아 Tiptap + Yjs 에디터를 붙일 본문 영역 경계
export function DocumentEditorPlaceholder({
  teamId,
  documentId,
  title,
}: DocumentEditorPlaceholderProps) {
  return (
    <div
      data-team-id={teamId}
      data-document-id={documentId}
      className="flex h-full min-h-[320px] flex-col p-6 lg:p-8"
    >
      <header className="border-b border-divider pb-4">
        <h1 className="text-heading font-bold wrap-anywhere text-text">{title}</h1>
      </header>
      <div className="flex flex-1 flex-col items-center justify-center gap-3 py-12 text-center">
        <DocumentIcon className="h-12 w-12 text-text-disabled" />
        <p className="text-body text-text-sub">에디터는 다음 작업에서 연결될 예정이에요.</p>
      </div>
    </div>
  )
}
