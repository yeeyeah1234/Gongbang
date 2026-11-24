// DOC-01 마지막으로 연 문서 ID를 팀별로 브라우저에 기억한다.
// localStorage를 못 쓰는 환경(사생활 보호 모드 등)에서는 예외를 삼키고 목록 첫 문서를 연다.
function lastDocumentKey(teamId: number) {
  return `workspace:lastDocument:${teamId}`
}

export function readLastDocumentId(teamId: number): number | null {
  try {
    const raw = window.localStorage.getItem(lastDocumentKey(teamId))
    if (!raw) return null
    const id = Number(raw)
    return Number.isSafeInteger(id) && id > 0 ? id : null
  } catch {
    return null
  }
}

export function writeLastDocumentId(teamId: number, documentId: number | null) {
  try {
    const key = lastDocumentKey(teamId)
    if (documentId === null) window.localStorage.removeItem(key)
    else window.localStorage.setItem(key, String(documentId))
  } catch {
    // 저장하지 못해도 현재 화면 동작에는 영향이 없다
  }
}
