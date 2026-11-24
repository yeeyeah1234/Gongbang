const DEFAULT_DEV_SYNC_URL = 'ws://127.0.0.1:1234'

/**
 * 문서 동기화 서버(Hocuspocus) WebSocket 주소.
 * - VITE_SYNC_URL이 있으면 그 값
 * - 없고 개발 모드면 ws://127.0.0.1:1234
 * - 없고 배포 모드면 현재 페이지 주소 기준(wss/ws + location.host + /sync/)
 */
export function resolveSyncServerUrl(): string {
  const configured = import.meta.env.VITE_SYNC_URL?.trim()
  if (configured) {
    return configured.replace(/\/+$/, '')
  }
  if (import.meta.env.DEV) {
    return DEFAULT_DEV_SYNC_URL
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/sync/`
}

/**
 * sync-server/src/documentName.ts 규칙("doc:{documentId}")에 맞춘 문서 이름.
 */
export function buildSyncDocumentName(documentId: number): string {
  return `doc:${documentId}`
}

/**
 * sync-server/src/documentName.ts 규칙("team:{teamId}")에 맞춘 팀 접속자 방 이름 (WS-03).
 */
export function buildSyncTeamName(teamId: number): string {
  return `team:${teamId}`
}
