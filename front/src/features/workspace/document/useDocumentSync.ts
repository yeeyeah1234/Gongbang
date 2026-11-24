import {
  HocuspocusProvider,
  HocuspocusProviderWebsocket,
  WebSocketStatus,
} from '@hocuspocus/provider'
import axios from 'axios'
import { useEffect, useRef, useState } from 'react'
import * as Y from 'yjs'
import { refreshAccessToken } from '../../../api/client'
import { getApiError } from '../../../api/errors'
import { useAuthStore } from '../../auth/store'
import { fetchDocuments } from './api'
import { buildSyncDocumentName, resolveSyncServerUrl } from './syncUrl'

export type DocumentSaveState = 'saving' | 'saved' | 'offline' | 'failed'

// WS-04: 재연결 후에도 30초 동안 보내지 못하면 저장 실패로 전환
const SEND_FAIL_TIMEOUT_MS = 30_000
// WS-06: sync-server POST /internal/disconnect 팀원 강퇴·나가기 종료 코드
const CLOSE_CODE_TEAM_MEMBER_REMOVED = 4403

interface UseDocumentSyncOptions {
  teamId: number
  documentId: number
  /** 개발 미리보기(/dev/document-preview)에서는 sync-server 없이 로컬 Y.Doc만 쓴다 */
  offlinePreview?: boolean
  onDocumentGone: (documentId: number) => void
  onForbidden: () => void
}

export interface SyncSessionHandle {
  instanceId: number
  ydoc: Y.Doc
  websocketProvider: HocuspocusProviderWebsocket | null
  provider: HocuspocusProvider | null
}

export interface DocumentSyncResult {
  session: SyncSessionHandle
  saveState: DocumentSaveState
  hasUnsyncedChanges: boolean
  retrySync: () => void
}

let nextSessionInstanceId = 1

function createSyncSession(documentId: number, offlinePreview: boolean): SyncSessionHandle {
  const instanceId = nextSessionInstanceId
  nextSessionInstanceId += 1
  const ydoc = new Y.Doc()

  if (offlinePreview) {
    return { instanceId, ydoc, websocketProvider: null, provider: null }
  }

  // autoConnect: false로 만들어 React StrictMode의 마운트·클린업·재마운트에서도
  // useEffect 안에서 안전하게 connect()/disconnect() 생명주기를 제어한다.
  const websocketProvider = new HocuspocusProviderWebsocket({
    url: resolveSyncServerUrl(),
    autoConnect: false,
  })
  const provider = new HocuspocusProvider({
    websocketProvider,
    name: buildSyncDocumentName(documentId),
    document: ydoc,
    token: async () => {
      const current = useAuthStore.getState().accessToken
      if (current) return current
      return (await refreshAccessToken()) ?? ''
    },
  })

  return { instanceId, ydoc, websocketProvider, provider }
}

export function useDocumentSync({
  teamId,
  documentId,
  offlinePreview = false,
  onDocumentGone,
  onForbidden,
}: UseDocumentSyncOptions): DocumentSyncResult {
  const onDocumentGoneRef = useRef(onDocumentGone)
  const onForbiddenRef = useRef(onForbidden)
  useEffect(() => {
    onDocumentGoneRef.current = onDocumentGone
    onForbiddenRef.current = onForbidden
  }, [onDocumentGone, onForbidden])

  // 부모(WorkspaceDocumentPage)가 key={selectedDoc.id}로 문서를 전환하므로 문서마다 새 세션이 생성된다
  const [session] = useState<SyncSessionHandle>(() => createSyncSession(documentId, offlinePreview))

  const [wsStatus, setWsStatus] = useState<WebSocketStatus>(() =>
    offlinePreview ? WebSocketStatus.Connected : WebSocketStatus.Connecting,
  )
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

  // WebSocket 연결 및 Hocuspocus 이벤트 구독
  useEffect(() => {
    const { ydoc, websocketProvider, provider } = session
    if (!websocketProvider || !provider) return

    let connected = false
    let stopped = false
    let refreshedOnce = false
    let authHandling = false
    stoppedRef.current = false

    const stopPermanently = () => {
      stopped = true
      stoppedRef.current = true
      websocketProvider.disconnect()
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
      connected = status === WebSocketStatus.Connected
      setWsStatus(status)
      if (status === WebSocketStatus.Disconnected) {
        setIsSynced(false)
        setSendFailed(false)
      }
    }

    const handleAuthenticated = () => {
      refreshedOnce = false
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
      if (event?.code === CLOSE_CODE_TEAM_MEMBER_REMOVED) {
        stopPermanently()
        onForbiddenRef.current()
      }
    }

    const handleAuthFailed = ({ reason }: { reason: string }) => {
      if (stopped || authHandling) return
      authHandling = true

      void (async () => {
        try {
          const upperReason = (reason ?? '').toUpperCase()
          const isExplicit401 = upperReason.includes('HTTP 401') || upperReason.includes('401')
          const isExplicit403 = upperReason.includes('HTTP 403') || upperReason.includes('403')
          const isExplicit404 = upperReason.includes('HTTP 404') || upperReason.includes('404')

          if (isExplicit404) {
            stopPermanently()
            onDocumentGoneRef.current(documentId)
            return
          }

          if (isExplicit403) {
            stopPermanently()
            onForbiddenRef.current()
            return
          }

          // 토큰 만료 가능성이 있으면 refresh 쿠키로 1회 갱신 시도
          if (!refreshedOnce) {
            refreshedOnce = true
            const newToken = await refreshAccessToken()
            if (newToken && !stopped && isExplicit401) {
              websocketProvider.disconnect()
              void websocketProvider.connect()
              return
            }
          }

          // 거절 사유에 상태 코드가 없는 경우(Hocuspocus 마스킹 등) REST 목록 조회로 403 vs 문서 삭제 판별
          try {
            const list = await fetchDocuments(teamId)
            if (stopped) return
            if (!list.some((item) => item.id === documentId)) {
              stopPermanently()
              onDocumentGoneRef.current(documentId)
              return
            }
            if (useAuthStore.getState().accessToken) {
              websocketProvider.disconnect()
              void websocketProvider.connect()
              return
            }
            stopPermanently()
            onDocumentGoneRef.current(documentId)
          } catch (error) {
            if (stopped) return
            const { code } = getApiError(error)
            const status = axios.isAxiosError(error) ? error.response?.status : undefined
            if (code === 'NOT_TEAM_MEMBER' || status === 403) {
              stopPermanently()
              onForbiddenRef.current()
              return
            }
            stopPermanently()
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

    if (!provider.isAttached) {
      provider.attach()
    }
    void websocketProvider.connect()

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
      // 문서 전환·탭 이탈 시 원격 화면에서 내 커서가 바로 사라지게 정리한다
      provider.awareness?.setLocalState(null)
      websocketProvider.disconnect()
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
    const { websocketProvider, provider } = session
    if (!websocketProvider || !provider || stoppedRef.current) return
    setSendFailed(false)
    setRetrySeq((seq) => seq + 1)
    if (websocketProvider.status === WebSocketStatus.Connected) {
      provider.forceSync()
    } else {
      void websocketProvider.connect()
    }
  }

  // TODO(확인 필요): HocuspocusProvider는 Spring DB 저장(debounce 3초/최대 10초) 완료 ACK를 받지 않으므로
  // "동기화 서버에 모든 변경 전송 완료(unsyncedChanges === 0 && isSynced)"를 「● 저장됨」으로 표시한다.
  let saveState: DocumentSaveState
  if (offlinePreview) {
    saveState = 'saved'
  } else if (isOffline) {
    saveState = 'offline'
  } else if (sendFailed) {
    saveState = 'failed'
  } else if (wsStatus === WebSocketStatus.Connected && isSynced && unsyncedCount === 0) {
    saveState = 'saved'
  } else {
    saveState = 'saving'
  }

  return {
    session,
    saveState,
    hasUnsyncedChanges,
    retrySync,
  }
}
