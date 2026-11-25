import {
  HocuspocusProvider,
  WebSocketStatus,
  type HocuspocusProviderWebsocket,
} from '@hocuspocus/provider'
import axios from 'axios'
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react'
import * as Y from 'yjs'
import { refreshAccessToken } from '../../../api/client'
import { getApiError } from '../../../api/errors'
import { useAuthStore } from '../../auth/store'
import type { DocumentSaveState } from '../document/useDocumentSync'
import {
  CLOSE_CODE_TEAM_MEMBER_REMOVED,
  connectSyncSocket,
  isSyncSocketOpen,
  isSyncSocketStopped,
  stopSyncSocket,
} from '../useWorkspaceSyncSocket'
import { fetchWhiteboards } from './api'

// WS-04: 재연결 후에도 30초 동안 보내지 못하면 저장 실패로 전환
const SEND_FAIL_TIMEOUT_MS = 30_000

/**
 * sync-server/src/documentName.ts 규칙("whiteboard:{boardId}")에 맞춘 화이트보드 이름.
 */
export function buildSyncWhiteboardName(boardId: number): string {
  return `whiteboard:${boardId}`
}

export interface WhiteboardSyncSessionHandle {
  instanceId: number
  boardId: number
  offlinePreview: boolean
  ydoc: Y.Doc
  websocketProvider: HocuspocusProviderWebsocket | null
  provider: HocuspocusProvider | null
  // WS-04: 이 보드에서 사용자가 직접 편집한 적이 있는지 세션 단위로 추적한다
  getHasUserEdited: () => boolean
  markUserEdited: () => void
  subscribeUserEdited: (listener: () => void) => () => void
}

interface UseWhiteboardSyncConnectionOptions {
  session: WhiteboardSyncSessionHandle | null
  teamId: number
  boardId: number
  offlinePreview?: boolean
  onBoardGone: (boardId: number) => void
  onForbidden: () => void
}

export interface WhiteboardSyncStatusResult {
  saveState: DocumentSaveState
  hasUnsyncedChanges: boolean
  retrySync: () => void
}

let nextBoardSessionInstanceId = 1

function createWhiteboardSyncSession(
  boardId: number,
  syncSocket: HocuspocusProviderWebsocket | null,
  offlinePreview: boolean,
): WhiteboardSyncSessionHandle {
  const instanceId = nextBoardSessionInstanceId
  nextBoardSessionInstanceId += 1
  const ydoc = new Y.Doc()

  let hasUserEdited = false
  const userEditedListeners = new Set<() => void>()
  const userEditedTracker = {
    getHasUserEdited: () => hasUserEdited,
    markUserEdited: () => {
      if (hasUserEdited) return
      hasUserEdited = true
      userEditedListeners.forEach((listener) => listener())
    },
    subscribeUserEdited: (listener: () => void) => {
      userEditedListeners.add(listener)
      return () => {
        userEditedListeners.delete(listener)
      }
    },
  }

  if (offlinePreview || !syncSocket) {
    return {
      instanceId,
      boardId,
      offlinePreview,
      ydoc,
      websocketProvider: null,
      provider: null,
      ...userEditedTracker,
    }
  }

  // 워크스페이스(팀) 단위 공유 HocuspocusProviderWebsocket에 whiteboard:{boardId} provider만 붙인다.
  // websocketProvider를 외부에서 넘기므로 provider.destroy()는 공유 소켓을 닫지 않고 이 보드만 정리한다.
  const provider = new HocuspocusProvider({
    websocketProvider: syncSocket,
    name: buildSyncWhiteboardName(boardId),
    document: ydoc,
    token: async () => {
      const current = useAuthStore.getState().accessToken
      if (current) return current
      return (await refreshAccessToken()) ?? ''
    },
  })

  return {
    instanceId,
    boardId,
    offlinePreview,
    ydoc,
    websocketProvider: syncSocket,
    provider,
    ...userEditedTracker,
  }
}

interface SyncSessionStore {
  subscribe: (listener: () => void) => () => void
  getSnapshot: () => WhiteboardSyncSessionHandle | null
  set: (next: WhiteboardSyncSessionHandle | null) => void
}

function createSyncSessionStore(): SyncSessionStore {
  let snapshot: WhiteboardSyncSessionHandle | null = null
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
      if (snapshot === next) return
      snapshot = next
      listeners.forEach((listener) => listener())
    },
  }
}

/**
 * 보드별 Y.Doc + HocuspocusProvider 세션을 useEffect에서 생성·정리한다.
 * - StrictMode 이중 렌더·보드 전환·화이트보드 탭 이탈 시 provider.destroy() + ydoc.destroy()를 실행한다.
 * - 공유 syncSocket은 건드리지 않으므로 보드 전환 시 /sync/ 소켓은 그대로 유지된다.
 */
export function useWhiteboardSyncSession(
  boardId: number,
  syncSocket: HocuspocusProviderWebsocket | null,
  offlinePreview = false,
): WhiteboardSyncSessionHandle | null {
  const [store] = useState(createSyncSessionStore)
  const snapshot = useSyncExternalStore(store.subscribe, store.getSnapshot)

  useEffect(() => {
    if (!offlinePreview && !syncSocket) {
      store.set(null)
      return
    }

    const session = createWhiteboardSyncSession(boardId, syncSocket, offlinePreview)
    store.set(session)

    return () => {
      store.set(null)
      if (session.provider) {
        if (session.provider.awareness && session.provider.awareness.getLocalState() !== null) {
          session.provider.awareness.setLocalState(null)
        }
        session.provider.destroy()
      }
      session.ydoc.destroy()
    }
  }, [store, boardId, syncSocket, offlinePreview])

  const expectedSocket = offlinePreview ? null : syncSocket
  if (
    !snapshot ||
    snapshot.boardId !== boardId ||
    snapshot.offlinePreview !== offlinePreview ||
    snapshot.websocketProvider !== expectedSocket
  ) {
    return null
  }
  return snapshot
}

export function useWhiteboardSyncConnection({
  session,
  teamId,
  boardId,
  offlinePreview = false,
  onBoardGone,
  onForbidden,
}: UseWhiteboardSyncConnectionOptions): WhiteboardSyncStatusResult {
  const onBoardGoneRef = useRef(onBoardGone)
  const onForbiddenRef = useRef(onForbidden)
  // StrictMode 재마운트 시 cleanup에서 비운 awareness user 정보를 복원한다
  const savedLocalUserRef = useRef<unknown>(null)
  useEffect(() => {
    onBoardGoneRef.current = onBoardGone
    onForbiddenRef.current = onForbidden
  }, [onBoardGone, onForbidden])

  const socket = session?.websocketProvider ?? null
  const subscribeWsStatus = useCallback(
    (onStoreChange: () => void) => {
      if (offlinePreview || !socket) return () => {}
      socket.on('status', onStoreChange)
      return () => {
        socket.off('status', onStoreChange)
      }
    },
    [socket, offlinePreview],
  )
  const getWsStatusSnapshot = useCallback(() => {
    if (offlinePreview) return WebSocketStatus.Connected
    return socket?.status ?? WebSocketStatus.Connecting
  }, [socket, offlinePreview])
  const wsStatus = useSyncExternalStore(subscribeWsStatus, getWsStatusSnapshot)

  const subscribeUserEdited = useCallback(
    (onStoreChange: () => void) => {
      if (!session) return () => {}
      return session.subscribeUserEdited(onStoreChange)
    },
    [session],
  )
  const getUserEditedSnapshot = useCallback(() => {
    return session?.getHasUserEdited() ?? false
  }, [session])
  const hasUserEdited = useSyncExternalStore(subscribeUserEdited, getUserEditedSnapshot)

  const [isSynced, setIsSynced] = useState(offlinePreview)
  const [unsyncedCount, setUnsyncedCount] = useState(0)
  const [browserOnline, setBrowserOnline] = useState(() =>
    typeof navigator === 'undefined' ? true : navigator.onLine,
  )
  const [dirtyWhileDisconnected, setDirtyWhileDisconnected] = useState(false)
  const [sendFailed, setSendFailed] = useState(false)
  const [retrySeq, setRetrySeq] = useState(0)

  const stoppedRef = useRef(false)

  useEffect(() => {
    if (offlinePreview) return
    const handleOnline = () => setBrowserOnline(true)
    const handleOffline = () => setBrowserOnline(false)
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [offlinePreview])

  useEffect(() => {
    if (!session) return
    const { ydoc, websocketProvider, provider } = session
    if (!websocketProvider || !provider) return

    let connected = websocketProvider.status === WebSocketStatus.Connected
    let stopped = false
    let refreshedOnce = false
    let retriedAuthOnce = false
    let authHandling = false
    stoppedRef.current = false

    // 이 보드 provider만 영구 중단한다 (404 보드 삭제 등에서는 team:{teamId} 공유 소켓을 유지)
    const stopBoardOnlyPermanently = () => {
      stopped = true
      stoppedRef.current = true
      if (provider.awareness && provider.awareness.getLocalState() !== null) {
        provider.awareness.setLocalState(null)
      }
      if (provider.isAttached) {
        provider.detach()
      }
    }

    // 팀원 권한 상실·인증 복구 불가 시 보드와 공유 소켓 모두 영구 중단한다
    const stopAllPermanently = () => {
      stopBoardOnlyPermanently()
      stopSyncSocket(websocketProvider)
    }

    const reauthenticateBoard = () => {
      if (stopped || isSyncSocketStopped(websocketProvider)) return
      if (isSyncSocketOpen(websocketProvider)) {
        if (provider.isAttached) {
          provider.detach()
        }
        provider.attach()
        return
      }
      if (!provider.isAttached) {
        provider.attach()
      }
      connectSyncSocket(websocketProvider)
    }

    const handleDocUpdate = (_update: Uint8Array, origin: unknown) => {
      if (stopped || origin === provider) return
      if (!connected) {
        setDirtyWhileDisconnected(true)
      }
    }

    const handleStatus = ({ status }: { status: WebSocketStatus }) => {
      if (stopped) return
      console.info(`[board-sync] whiteboard:${boardId} status=${status}`)
      connected = status === WebSocketStatus.Connected
      if (status === WebSocketStatus.Disconnected) {
        setIsSynced(false)
        setSendFailed(false)
      }
    }

    const handleAuthenticated = () => {
      refreshedOnce = false
      retriedAuthOnce = false
    }

    const handleSynced = ({ state }: { state: boolean }) => {
      if (stopped) return
      setIsSynced(state)
      if (state) {
        setDirtyWhileDisconnected(false)
        if (provider.unsyncedChanges === 0) {
          setSendFailed(false)
        }
      }
    }

    const handleUnsyncedChanges = ({ number }: { number: number }) => {
      if (stopped) return
      setUnsyncedCount(number)
      if (number === 0) {
        setSendFailed(false)
      }
    }

    const handleClose = ({ event }: { event: { code?: number; reason?: string } }) => {
      if (stopped) return
      console.info(
        `[board-sync] whiteboard:${boardId} close code=${event?.code ?? 'unknown'} reason=${event?.reason || '-'}`,
      )
      if (event?.code === CLOSE_CODE_TEAM_MEMBER_REMOVED) {
        stopAllPermanently()
        onForbiddenRef.current()
      }
    }

    const handleAuthFailed = ({ reason }: { reason: string }) => {
      if (stopped || authHandling) return
      console.info(
        `[board-sync] whiteboard:${boardId} authenticationFailed reason=${reason || 'unknown'}`,
      )
      authHandling = true
      if (provider.isAttached) {
        provider.detach()
      }

      void (async () => {
        try {
          const upperReason = (reason ?? '').toUpperCase()
          const isExplicit401 = upperReason.includes('HTTP 401') || upperReason.includes('401')
          const isExplicit403 = upperReason.includes('HTTP 403') || upperReason.includes('403')
          const isExplicit404 = upperReason.includes('HTTP 404') || upperReason.includes('404')

          if (isExplicit404) {
            stopBoardOnlyPermanently()
            onBoardGoneRef.current(boardId)
            return
          }

          if (isExplicit403) {
            stopAllPermanently()
            onForbiddenRef.current()
            return
          }

          if (!refreshedOnce) {
            refreshedOnce = true
            const newToken = await refreshAccessToken()
            if (stopped) return
            if (newToken && isExplicit401) {
              reauthenticateBoard()
              return
            }
            if (!newToken && isExplicit401) {
              stopAllPermanently()
              return
            }
          } else if (isExplicit401) {
            stopAllPermanently()
            return
          }

          // 거절 사유에 상태 코드가 없는 경우(Hocuspocus 마스킹 등) REST 목록 조회로 403 vs 보드 삭제 판별
          try {
            const list = await fetchWhiteboards(teamId)
            if (stopped) return
            if (!list.some((item) => item.whiteboardId === boardId)) {
              stopBoardOnlyPermanently()
              onBoardGoneRef.current(boardId)
              return
            }
            if (useAuthStore.getState().accessToken && !retriedAuthOnce) {
              retriedAuthOnce = true
              reauthenticateBoard()
              return
            }
            stopBoardOnlyPermanently()
          } catch (error) {
            if (stopped) return
            const { code } = getApiError(error)
            const status = axios.isAxiosError(error) ? error.response?.status : undefined
            if (code === 'NOT_TEAM_MEMBER' || status === 403) {
              stopAllPermanently()
              onForbiddenRef.current()
              return
            }
            if (status === 401) {
              stopAllPermanently()
              return
            }
            stopBoardOnlyPermanently()
            onBoardGoneRef.current(boardId)
          }
        } finally {
          authHandling = false
        }
      })()
    }

    ydoc.on('update', handleDocUpdate)
    provider.on('status', handleStatus)
    provider.on('authenticated', handleAuthenticated)
    provider.on('synced', handleSynced)
    provider.on('unsyncedChanges', handleUnsyncedChanges)
    provider.on('close', handleClose)
    provider.on('authenticationFailed', handleAuthFailed)

    if (provider.awareness && provider.awareness.getLocalState() === null) {
      provider.awareness.setLocalState(
        savedLocalUserRef.current ? { user: savedLocalUserRef.current } : {},
      )
    }

    if (!isSyncSocketStopped(websocketProvider)) {
      if (!provider.isAttached) {
        provider.attach()
      }
      connectSyncSocket(websocketProvider)
    }

    return () => {
      stopped = true
      stoppedRef.current = true
      ydoc.off('update', handleDocUpdate)
      provider.off('status', handleStatus)
      provider.off('authenticated', handleAuthenticated)
      provider.off('synced', handleSynced)
      provider.off('unsyncedChanges', handleUnsyncedChanges)
      provider.off('close', handleClose)
      provider.off('authenticationFailed', handleAuthFailed)
      savedLocalUserRef.current = provider.awareness?.getLocalState()?.user ?? null
      // 보드 전환·탭 이탈 시 원격 화면에서 내 커서가 바로 사라지게 정리하고, 공유 소켓은 유지한다
      if (provider.awareness && provider.awareness.getLocalState() !== null) {
        provider.awareness.setLocalState(null)
      }
      if (provider.isAttached) {
        provider.detach()
      }
    }
  }, [session, teamId, boardId])

  const isOffline = !offlinePreview && (!browserOnline || wsStatus === WebSocketStatus.Disconnected)
  const hasUnsyncedChanges =
    !offlinePreview && (unsyncedCount > 0 || (dirtyWhileDisconnected && !isSynced))
  const isConnectedPending =
    !offlinePreview &&
    !isOffline &&
    wsStatus === WebSocketStatus.Connected &&
    (unsyncedCount > 0 || !isSynced)

  // WS-04: 재연결(connected) 후에도 30초 동안 보내지 못하면 저장 실패로 전환
  useEffect(() => {
    if (!isConnectedPending || sendFailed) return
    const timer = window.setTimeout(() => {
      setSendFailed(true)
    }, SEND_FAIL_TIMEOUT_MS)
    return () => window.clearTimeout(timer)
  }, [isConnectedPending, sendFailed, retrySeq])

  // 미전송 변경이 있을 때 페이지를 닫으면 브라우저 기본 확인창 표시
  useEffect(() => {
    if (!hasUnsyncedChanges) return
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [hasUnsyncedChanges])

  const retrySync = () => {
    if (!session) return
    const { websocketProvider, provider } = session
    if (!websocketProvider || !provider || stoppedRef.current) return
    if (isSyncSocketStopped(websocketProvider)) return
    setSendFailed(false)
    setRetrySeq((seq) => seq + 1)
    if (websocketProvider.status === WebSocketStatus.Connected) {
      if (!provider.isAttached) {
        provider.attach()
      } else {
        provider.forceSync()
      }
    } else {
      connectSyncSocket(websocketProvider)
    }
  }

  // TODO(확인 필요): HocuspocusProvider는 Spring DB 저장(debounce 3초/최대 10초) 완료 ACK를 받지 않으므로
  // "동기화 서버에 모든 변경 전송 완료(unsyncedChanges === 0 && isSynced)"를 「● 저장됨」으로 표시한다.
  let saveState: DocumentSaveState
  if (offlinePreview) {
    saveState = hasUserEdited ? 'saved' : 'idle'
  } else if (isOffline) {
    saveState = 'offline'
  } else if (sendFailed) {
    saveState = 'failed'
  } else if (!hasUserEdited) {
    saveState = 'idle'
  } else if (wsStatus === WebSocketStatus.Connected && isSynced && unsyncedCount === 0) {
    saveState = 'saved'
  } else {
    saveState = 'saving'
  }

  return {
    saveState,
    hasUnsyncedChanges,
    retrySync,
  }
}
