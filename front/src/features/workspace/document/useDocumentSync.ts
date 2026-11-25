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
import {
  CLOSE_CODE_TEAM_MEMBER_REMOVED,
  connectSyncSocket,
  isSyncSocketOpen,
  isSyncSocketStopped,
  stopSyncSocket,
} from '../useWorkspaceSyncSocket'
import { fetchDocuments } from './api'
import { buildSyncDocumentName } from './syncUrl'

export type DocumentSaveState = 'idle' | 'saving' | 'saved' | 'offline' | 'failed'

// WS-04: 재연결 후에도 30초 동안 보내지 못하면 저장 실패로 전환
const SEND_FAIL_TIMEOUT_MS = 30_000

interface UseDocumentSyncOptions {
  teamId: number
  documentId: number
  syncSocket?: HocuspocusProviderWebsocket | null
  /** 개발 미리보기(/dev/document-preview)에서는 sync-server 없이 로컬 Y.Doc만 쓴다 */
  offlinePreview?: boolean
  onDocumentGone: (documentId: number) => void
  onForbidden: () => void
}

export interface SyncSessionHandle {
  instanceId: number
  documentId: number
  offlinePreview: boolean
  ydoc: Y.Doc
  websocketProvider: HocuspocusProviderWebsocket | null
  provider: HocuspocusProvider | null
  // WS-04: 이 문서에서 사용자가 직접 편집한 적이 있는지 세션 단위로 추적한다
  getHasUserEdited: () => boolean
  markUserEdited: () => void
  subscribeUserEdited: (listener: () => void) => () => void
}

interface UseDocumentSyncConnectionOptions extends UseDocumentSyncOptions {
  session: SyncSessionHandle | null
}

export interface DocumentSyncStatusResult {
  saveState: DocumentSaveState
  hasUnsyncedChanges: boolean
  retrySync: () => void
}

export interface DocumentSyncResult extends DocumentSyncStatusResult {
  session: SyncSessionHandle | null
}

let nextSessionInstanceId = 1

function createSyncSession(
  documentId: number,
  syncSocket: HocuspocusProviderWebsocket | null,
  offlinePreview: boolean,
): SyncSessionHandle {
  const instanceId = nextSessionInstanceId
  nextSessionInstanceId += 1
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
      documentId,
      offlinePreview,
      ydoc,
      websocketProvider: null,
      provider: null,
      ...userEditedTracker,
    }
  }

  // 워크스페이스(팀) 단위 공유 HocuspocusProviderWebsocket에 doc:{documentId} provider만 붙인다.
  // websocketProvider를 외부에서 넘기므로 provider.destroy()는 공유 소켓을 닫지 않고 이 문서만 정리한다.
  const provider = new HocuspocusProvider({
    websocketProvider: syncSocket,
    name: buildSyncDocumentName(documentId),
    document: ydoc,
    token: async () => {
      const current = useAuthStore.getState().accessToken
      if (current) return current
      return (await refreshAccessToken()) ?? ''
    },
  })

  return {
    instanceId,
    documentId,
    offlinePreview,
    ydoc,
    websocketProvider: syncSocket,
    provider,
    ...userEditedTracker,
  }
}

interface SyncSessionStore {
  subscribe: (listener: () => void) => () => void
  getSnapshot: () => SyncSessionHandle | null
  set: (next: SyncSessionHandle | null) => void
}

function createSyncSessionStore(): SyncSessionStore {
  let snapshot: SyncSessionHandle | null = null
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
 * 문서별 Y.Doc + HocuspocusProvider 세션을 useEffect에서 생성·정리한다.
 * - StrictMode 이중 렌더·문서 전환·문서 탭 이탈 시 provider.destroy() + ydoc.destroy()가 반드시 실행된다.
 * - 공유 syncSocket은 건드리지 않으므로 문서 전환 시 /sync/ 소켓은 그대로 유지된다.
 * - 저장 상태 변경으로 에디터 본체가 리렌더링되지 않도록 상태 구독은 useDocumentSyncConnection에 분리한다.
 */
export function useDocumentSyncSession(
  documentId: number,
  syncSocket: HocuspocusProviderWebsocket | null,
  offlinePreview = false,
): SyncSessionHandle | null {
  const [store] = useState(createSyncSessionStore)
  const snapshot = useSyncExternalStore(store.subscribe, store.getSnapshot)

  useEffect(() => {
    if (!offlinePreview && !syncSocket) {
      store.set(null)
      return
    }

    const session = createSyncSession(documentId, syncSocket, offlinePreview)
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
  }, [store, documentId, syncSocket, offlinePreview])

  const expectedSocket = offlinePreview ? null : syncSocket
  if (
    !snapshot ||
    snapshot.documentId !== documentId ||
    snapshot.offlinePreview !== offlinePreview ||
    snapshot.websocketProvider !== expectedSocket
  ) {
    return null
  }
  return snapshot
}

export function useDocumentSyncConnection({
  session,
  teamId,
  documentId,
  offlinePreview = false,
  onDocumentGone,
  onForbidden,
}: UseDocumentSyncConnectionOptions): DocumentSyncStatusResult {
  const onDocumentGoneRef = useRef(onDocumentGone)
  const onForbiddenRef = useRef(onForbidden)
  // StrictMode 재마운트 시 cleanup에서 비운 awareness user 정보를 복원한다
  const savedLocalUserRef = useRef<unknown>(null)
  useEffect(() => {
    onDocumentGoneRef.current = onDocumentGone
    onForbiddenRef.current = onForbidden
  }, [onDocumentGone, onForbidden])

  // 공유 소켓이 이미 Connected인 상태로 문서를 열어도 즉시 실제 상태를 반영한다
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

  // 브라우저 online/offline 감지
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

  // 공유 WebSocket에 문서 provider 연결 및 Hocuspocus 이벤트 구독
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

    // 이 문서 provider만 영구 중단한다 (404 문서 삭제 등에서는 team:{teamId} 공유 소켓을 유지)
    const stopDocOnlyPermanently = () => {
      stopped = true
      stoppedRef.current = true
      if (provider.awareness && provider.awareness.getLocalState() !== null) {
        provider.awareness.setLocalState(null)
      }
      if (provider.isAttached) {
        provider.detach()
      }
    }

    // 팀원 권한 상실·인증 복구 불가 시 문서와 공유 소켓 모두 영구 중단한다
    const stopAllPermanently = () => {
      stopDocOnlyPermanently()
      stopSyncSocket(websocketProvider)
    }

    const reauthenticateDoc = () => {
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

    // 오프라인 중 입력 여부를 기억해 둔다 (startSync가 unsyncedChanges를 0으로 초기화하기 전 보호)
    const handleDocUpdate = (_update: Uint8Array, origin: unknown) => {
      if (stopped || origin === provider) return
      if (!connected) {
        setDirtyWhileDisconnected(true)
      }
    }

    const handleStatus = ({ status }: { status: WebSocketStatus }) => {
      if (stopped) return
      console.info(`[doc-sync] doc:${documentId} status=${status}`)
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
        `[doc-sync] doc:${documentId} close code=${event?.code ?? 'unknown'} reason=${event?.reason || '-'}`,
      )
      if (event?.code === CLOSE_CODE_TEAM_MEMBER_REMOVED) {
        stopAllPermanently()
        onForbiddenRef.current()
      }
    }

    const handleAuthFailed = ({ reason }: { reason: string }) => {
      if (stopped || authHandling) return
      console.info(
        `[doc-sync] doc:${documentId} authenticationFailed reason=${reason || 'unknown'}`,
      )
      authHandling = true
      // 비동기 판별·토큰 갱신 중 자동 재연결로 같은 문서 인증이 반복되지 않게 먼저 분리한다
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
            stopDocOnlyPermanently()
            onDocumentGoneRef.current(documentId)
            return
          }

          if (isExplicit403) {
            stopAllPermanently()
            onForbiddenRef.current()
            return
          }

          // 토큰 만료 가능성이 있으면 refresh 쿠키로 1회 갱신 시도
          if (!refreshedOnce) {
            refreshedOnce = true
            const newToken = await refreshAccessToken()
            if (stopped) return
            if (newToken && isExplicit401) {
              reauthenticateDoc()
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

          // 거절 사유에 상태 코드가 없는 경우(Hocuspocus 마스킹 등) REST 목록 조회로 403 vs 문서 삭제 판별
          try {
            const list = await fetchDocuments(teamId)
            if (stopped) return
            if (!list.some((item) => item.id === documentId)) {
              stopDocOnlyPermanently()
              onDocumentGoneRef.current(documentId)
              return
            }
            if (useAuthStore.getState().accessToken && !retriedAuthOnce) {
              retriedAuthOnce = true
              reauthenticateDoc()
              return
            }
            stopDocOnlyPermanently()
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
            stopDocOnlyPermanently()
            onDocumentGoneRef.current(documentId)
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
      // 문서 전환·탭 이탈 시 원격 화면에서 내 커서가 바로 사라지게 정리하고, 공유 소켓은 유지한다
      if (provider.awareness && provider.awareness.getLocalState() !== null) {
        provider.awareness.setLocalState(null)
      }
      if (provider.isAttached) {
        provider.detach()
      }
    }
  }, [session, teamId, documentId])

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

export function useDocumentSync(options: UseDocumentSyncOptions): DocumentSyncResult {
  const session = useDocumentSyncSession(
    options.documentId,
    options.syncSocket ?? null,
    options.offlinePreview,
  )
  const status = useDocumentSyncConnection({ ...options, session })
  return {
    session,
    ...status,
  }
}
