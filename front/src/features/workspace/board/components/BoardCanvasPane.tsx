import { lazy, Suspense } from 'react'
import { NoticeSkeleton } from '../../notice/components/NoticeStates'
import type { BoardCanvasProps } from './BoardCanvas'

// Excalidraw + Yjs + Hocuspocus가 커서 보드를 처음 열 때만 내려받는다 (DocumentEditorPane과 같은 방식)
const BoardCanvas = lazy(() => import('./BoardCanvas'))

export function BoardCanvasPane(props: BoardCanvasProps) {
  return (
    <Suspense fallback={<NoticeSkeleton />}>
      <BoardCanvas {...props} />
    </Suspense>
  )
}
