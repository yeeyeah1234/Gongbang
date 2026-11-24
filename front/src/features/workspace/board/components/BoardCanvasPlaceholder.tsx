import { BoardIcon } from './BoardIcon'

interface BoardCanvasPlaceholderProps {
  boardId: number
}

// WB-02~10 캔버스 자리. boardId만 받는 경계로 두고, 다음 작업에서 이 컴포넌트를 Excalidraw + Yjs 캔버스로 바꾼다
// (동기화 서버 문서 이름 whiteboard:{boardId}는 캔버스 담당이 정한다)
export function BoardCanvasPlaceholder({ boardId }: BoardCanvasPlaceholderProps) {
  return (
    <div
      data-board-id={boardId}
      className="flex h-full min-h-[320px] flex-col items-center justify-center gap-3 p-6 text-center lg:p-8"
    >
      <BoardIcon className="h-12 w-12 text-text-disabled" />
      <p className="text-body text-text-sub">캔버스는 다음 작업에서 연결될 예정이에요.</p>
    </div>
  )
}
