import { HocuspocusProviderWebsocket, WebSocketStatus } from '@hocuspocus/provider'
import { useEffect, useState, useSyncExternalStore } from 'react'
import { resolveSyncServerUrl } from './document/syncUrl'

// WS-06: sync-server POST /internal/disconnect 팀원 강퇴·나가기 종료 코드
export const CLOSE_CODE_TEAM_MEMBER_REMOVED = 4403

// 재연결 지수 백오프: 1초 → 2초 → 4초 → 8초 → 16초 → 최대 30초 (jitter 비활성으로 짧은 간격 회귀 방지)
const RECONNECT_INITIAL_DELAY_MS = 1_000
const RECONNECT_BACKOFF_FACTOR = 2
const RECONNECT_MAX_DELAY_MS = 30_000

const stoppedSockets = new WeakSet<HocuspocusProviderWebsocket>()

/**
 * 공유 Hocuspocus WebSocket의 자동 재연결 예약만 멈춘다 (열려 있는 소켓은 닫지 않음).
 * 토큰 재발급(401) 대기 중 서버가 소켓을 닫더라도 onClose가 1초 뒤 미갱신 토큰으로
 * 재연결하지 않도록 막을 때 사용한다.
 */
export function suppressSyncSocketReconnect(socket: HocuspocusProviderWebsocket): void {
  socket.shouldConnect = false
  socket.cancelWebsocketRetry?.()
  socket.cancelWebsocketRetry = undefined
  socket.clearReconnectTimeout()
}

/**
 * 공유 Hocuspocus WebSocket을 영구 중단한다 (403·4403·인증 실패·언마운트).
 * 한 번 영구 중단된 소켓은 다른 provider가 connectSyncSocket을 불러도 다시 연결하지 않는다.
 */
export function stopSyncSocket(socket: HocuspocusProviderWebsocket): void {
  stoppedSockets.add(socket)
  suppressSyncSocketReconnect(socket)
  socket.disconnect()
}

export function isSyncSocketStopped(socket: HocuspocusProviderWebsocket): boolean {
  return stoppedSockets.has(socket)
}

export function isSyncSocketOpen(socket: HocuspocusProviderWebsocket): boolean {
  return (
    socket.status === WebSocketStatus.Connected && socket.webSocket?.readyState === WebSocket.OPEN
  )
}

/**
 * 공유 Hocuspocus WebSocket이 연결되지 않았고 영구 중단 상태가 아닐 때만 연결을 시작한다.
 * Connecting/Connected 상태에서 중복 connect() 호출로 기존 시도가 취소되는 것을 막는다.
 */
export function connectSyncSocket(socket: HocuspocusProviderWebsocket): void {
  if (stoppedSockets.has(socket)) return
  const readyState = socket.webSocket?.readyState
  if (socket.status === WebSocketStatus.Connected && readyState === WebSocket.OPEN) {
    return
  }
  if (socket.status === WebSocketStatus.Connecting && readyState === WebSocket.CONNECTING) {
    return
  }
  void socket.connect()
}

interface SyncSocketSnapshot {
  teamId: number | null
  socket: HocuspocusProviderWebsocket | null
}

interface SyncSocketStore {
  subscribe: (listener: () => void) => () => void
  getSnapshot: () => SyncSocketSnapshot
  set: (next: SyncSocketSnapshot) => void
}

function createSyncSocketStore(): SyncSocketStore {
  let snapshot: SyncSocketSnapshot = { teamId: null, socket: null }
  const listeners = new Set<() => void>()

  return {
    subscribe: (listener) => {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    getSnapshot: () => snapshot,
    set: (next) => {
      if (snapshot.teamId === next.teamId && snapshot.socket === next.socket) return
      snapshot = next
      listeners.forEach((listener) => listener())
    },
  }
}

interface UseWorkspaceSyncSocketOptions {
  teamId: number
  enabled: boolean
}

/**
 * WS-03·DOC-08: 워크스페이스(팀)당 HocuspocusProviderWebsocket 1개를 생성해
 * team:{teamId}(접속자)과 doc:{documentId}(문서 동기화)가 같은 /sync/ 소켓을 공유하게 한다.
 *
 * - useEffect 안에서 생성·destroy()하므로 StrictMode 이중 렌더/마운트에서도 소켓·타이머가 남지 않는다.
 * - 워크스페이스 이탈·팀 변경 시 즉시 disconnect() + destroy()로 정리한다.
 */
export function useWorkspaceSyncSocket({
  teamId,
  enabled,
}: UseWorkspaceSyncSocketOptions): HocuspocusProviderWebsocket | null {
  const [store] = useState(createSyncSocketStore)
  const snapshot = useSyncExternalStore(store.subscribe, store.getSnapshot)

  useEffect(() => {
    if (!enabled || !Number.isSafeInteger(teamId) || teamId <= 0) {
      store.set({ teamId: null, socket: null })
      return
    }

    // autoConnect: false로 생성하고 provider.attach() 뒤 명시적으로 연결한다.
    // preserveTrailingSlash: true로 배포 기본값(/sync/) 끝 슬래시가 잘려 301이 나지 않게 유지한다.
    const socket = new HocuspocusProviderWebsocket({
      url: resolveSyncServerUrl(),
      autoConnect: false,
      preserveTrailingSlash: true,
      delay: RECONNECT_INITIAL_DELAY_MS,
      minDelay: RECONNECT_INITIAL_DELAY_MS,
      maxDelay: RECONNECT_MAX_DELAY_MS,
      factor: RECONNECT_BACKOFF_FACTOR,
      jitter: false,
      maxAttempts: 0,
      onClose: ({ event }) => {
        // 4403은 라이브러리 내부 onClose 재연결 예약보다 먼저 영구 중단한다
        if (event?.code === CLOSE_CODE_TEAM_MEMBER_REMOVED) {
          stopSyncSocket(socket)
        }
      },
    })

    store.set({ teamId, socket })

    return () => {
      store.set({ teamId: null, socket: null })
      stopSyncSocket(socket)
      socket.destroy()
    }
  }, [store, teamId, enabled])

  if (!enabled || snapshot.teamId !== teamId) {
    return null
  }
  return snapshot.socket
}
