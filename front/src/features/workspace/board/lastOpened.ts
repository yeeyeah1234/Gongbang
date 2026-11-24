// WS-02 ⑤ 마지막으로 연 보드 ID를 팀별로 브라우저에 기억한다 (문서의 lastOpened.ts와 같은 방식).
// localStorage를 못 쓰는 환경(사생활 보호 모드 등)에서는 예외를 삼키고 목록 첫 보드를 연다.
function lastBoardKey(teamId: number) {
  return `workspace:lastBoard:${teamId}`
}

export function readLastBoardId(teamId: number): number | null {
  try {
    const raw = window.localStorage.getItem(lastBoardKey(teamId))
    if (!raw) return null
    const id = Number(raw)
    return Number.isSafeInteger(id) && id > 0 ? id : null
  } catch {
    return null
  }
}

export function writeLastBoardId(teamId: number, boardId: number | null) {
  try {
    const key = lastBoardKey(teamId)
    if (boardId === null) window.localStorage.removeItem(key)
    else window.localStorage.setItem(key, String(boardId))
  } catch {
    // 저장하지 못해도 현재 화면 동작에는 영향이 없다
  }
}
