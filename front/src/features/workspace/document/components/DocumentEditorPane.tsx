import { lazy, Suspense } from 'react'
import { NoticeSkeleton } from '../../notice/components/NoticeStates'
import type { DocumentEditorProps } from './DocumentEditor'

// Tiptap + Yjs + Hocuspocus가 커서 문서를 처음 열 때만 내려받는다 (WorkspaceCalendarRoute와 같은 방식)
const DocumentEditor = lazy(() => import('./DocumentEditor'))

export function DocumentEditorPane(props: DocumentEditorProps) {
  return (
    <Suspense fallback={<NoticeSkeleton />}>
      <DocumentEditor {...props} />
    </Suspense>
  )
}
