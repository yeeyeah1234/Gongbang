import { CaptureUpdateAction, reconcileElements } from '@excalidraw/excalidraw'
import type { RemoteExcalidrawElement } from '@excalidraw/excalidraw/data/reconcile'
import type { OrderedExcalidrawElement } from '@excalidraw/excalidraw/element/types'
import type { ExcalidrawImperativeAPI } from '@excalidraw/excalidraw/types'
import type { HocuspocusProvider } from '@hocuspocus/provider'
import * as Y from 'yjs'

// WB-10: Y.Doc 내부에서 화이트보드 요소를 보관하는 Y.Map 키
export const WHITEBOARD_ELEMENTS_MAP_KEY = 'elements'

// 로컬 변경과 원격/초기 SyncStep2 변경을 구분하기 위한 Yjs 트랜잭션 origin
const LOCAL_SYNC_ORIGIN = Symbol('whiteboard-local-sync')

// Excalidraw initializeScene 완료(isLoading: false) 감지를 위한 폴백 폴링 주기·최대 대기 시간
const CANVAS_READY_POLL_INTERVAL_MS = 100
const CANVAS_READY_POLL_MAX_MS = 5_000

interface ElementVersionSnapshot {
  version: number
  versionNonce: number
  isDeleted: boolean
}

function isOrderedElement(value: unknown): value is OrderedExcalidrawElement {
  if (!value || typeof value !== 'object') return false
  const record = value as Record<string, unknown>
  return (
    typeof record.id === 'string' &&
    record.id.length > 0 &&
    typeof record.type === 'string' &&
    typeof record.version === 'number' &&
    typeof record.versionNonce === 'number'
  )
}

/**
 * Excalidraw reconcileElements와 동일한 충돌 정리 규칙:
 * version이 큰 쪽이 우선하며, version이 같으면 versionNonce가 작은 쪽을 채택한다.
 */
function isIncomingNewer(
  incoming: OrderedExcalidrawElement,
  existing: OrderedExcalidrawElement | undefined,
): boolean {
  if (!existing) return true
  if (incoming.version !== existing.version) {
    return incoming.version > existing.version
  }
  if (incoming.versionNonce !== existing.versionNonce) {
    return incoming.versionNonce < existing.versionNonce
  }
  return false
}

function isSameVersionSnapshot(
  prev: ElementVersionSnapshot | undefined,
  element: OrderedExcalidrawElement,
): boolean {
  return (
    prev !== undefined &&
    prev.version === element.version &&
    prev.versionNonce === element.versionNonce &&
    prev.isDeleted === element.isDeleted
  )
}

function cloneElement(element: OrderedExcalidrawElement): OrderedExcalidrawElement {
  return structuredClone(element)
}

export interface CreateExcalidrawYjsBindingOptions {
  /** null 또는 생략 시 offlinePreview 모드로 간주해 즉시 synced 상태로 처리한다. */
  provider?: HocuspocusProvider | null
  /** synced 직후 보류된 로컬 요소를 Y.Map에 올렸을 때 호출한다 (WS-04 첫 사용자 편집 감지). */
  onLocalWrite?: () => void
}

export interface ExcalidrawYjsBinding {
  /**
   * Excalidraw onChange에서 호출한다.
   * 실제로 Y.Map에 반영된 사용자 편집이 있으면 true를 돌려준다 (WS-04 첫 사용자 편집 감지).
   */
  handleLocalChange: (elements: readonly OrderedExcalidrawElement[]) => boolean
  /** 현재 Y.Map 상태를 캔버스에 1회 반영한다 (초기 SyncStep2·API 준비 직후). */
  applyRemoteState: () => void
  /** Y.Map observe·provider synced 구독·초기화 대기 타이머 및 내부 상태 정리 */
  destroy: () => void
}

/**
 * Excalidraw 0.18 요소 배열 ↔ Y.Map('elements') 양방향 경량 바인딩.
 *
 * - Excalidraw는 constructor에서 excalidrawAPI를 먼저 넘긴 뒤 비동기 initializeScene()으로
 *   빈 씬을 덮어쓰므로, appState.isLoading === false(캔버스 준비)와 provider synced가 모두
 *   끝난 시점에 Y.Map 전체를 캔버스에 반영한다.
 * - 요소 ID별로 Y.Map에 저장하며 삭제 요소도 isDeleted: true(tombstone) 그대로 보존한다(Ctrl+Z 복원·충돌 수렴).
 * - 원격 반영은 CaptureUpdateAction.NEVER로 적용해 다른 팀원 변경이 내 실행 취소 스택에 섞이지 않게 한다.
 * - LOCAL_SYNC_ORIGIN과 lastSyncedVersions(Y.Map 실제 버전만 기록)로 onChange 에코 루프를 차단한다.
 */
export function createExcalidrawYjsBinding(
  ydoc: Y.Doc,
  api: ExcalidrawImperativeAPI,
  options?: CreateExcalidrawYjsBindingOptions,
): ExcalidrawYjsBinding {
  const provider = options?.provider ?? null
  const onLocalWrite = options?.onLocalWrite
  const yElements = ydoc.getMap<OrderedExcalidrawElement>(WHITEBOARD_ELEMENTS_MAP_KEY)
  const lastSyncedVersions = new Map<string, ElementVersionSnapshot>()
  let isApplyingRemote = false
  let destroyed = false
  let canvasReady = !api.getAppState()?.isLoading
  let hasSyncedOnce = provider === null || provider.isSynced
  let unsubscribeCanvasReady: (() => void) | null = null
  let readyPollTimer: ReturnType<typeof setInterval> | null = null

  const recordSyncedVersions = (elements: readonly OrderedExcalidrawElement[]) => {
    for (const el of elements) {
      lastSyncedVersions.set(el.id, {
        version: el.version,
        versionNonce: el.versionNonce,
        isDeleted: el.isDeleted,
      })
    }
  }

  const cleanupCanvasReadyWatchers = () => {
    if (unsubscribeCanvasReady) {
      unsubscribeCanvasReady()
      unsubscribeCanvasReady = null
    }
    if (readyPollTimer !== null) {
      clearInterval(readyPollTimer)
      readyPollTimer = null
    }
  }

  const pushLocalElementsToYMap = (): boolean => {
    if (destroyed || !canvasReady || !hasSyncedOnce) return false

    const localElements = api.getSceneElementsIncludingDeleted()
    if (localElements.length === 0) return false

    let wroteAny = false
    ydoc.transact(() => {
      for (const el of localElements) {
        const currentInY = yElements.get(el.id)
        const existing = isOrderedElement(currentInY) ? currentInY : undefined
        if (isIncomingNewer(el, existing)) {
          yElements.set(el.id, cloneElement(el))
          lastSyncedVersions.set(el.id, {
            version: el.version,
            versionNonce: el.versionNonce,
            isDeleted: el.isDeleted,
          })
          wroteAny = true
        }
      }
    }, LOCAL_SYNC_ORIGIN)

    if (wroteAny) {
      onLocalWrite?.()
    }
    return wroteAny
  }

  const applyRemoteState = () => {
    if (destroyed || !canvasReady) return

    const remoteElements: RemoteExcalidrawElement[] = []
    yElements.forEach((value) => {
      if (isOrderedElement(value)) {
        remoteElements.push(cloneElement(value) as RemoteExcalidrawElement)
      }
    })

    if (remoteElements.length === 0) return

    const localElements = api.getSceneElementsIncludingDeleted()
    const localAppState = api.getAppState()
    const reconciled = reconcileElements(localElements, remoteElements, localAppState)

    // Y.Map에 실제로 들어 있는 원격 요소 버전만 기록한다.
    // reconciled 전체를 기록하면 아직 Y.Map에 없는 로컬 전용 요소가 동기화된 것으로 오인되어 업로드가 누락된다.
    recordSyncedVersions(remoteElements)

    isApplyingRemote = true
    try {
      api.updateScene({
        elements: reconciled,
        captureUpdate: CaptureUpdateAction.NEVER,
      })
    } finally {
      isApplyingRemote = false
    }
  }

  const syncWhenReady = () => {
    if (destroyed || !canvasReady || !hasSyncedOnce) return
    pushLocalElementsToYMap()
    applyRemoteState()
  }

  const markCanvasReady = () => {
    if (destroyed || canvasReady) return
    canvasReady = true
    cleanupCanvasReadyWatchers()
    syncWhenReady()
  }

  const handleYMapChange = (
    _event: Y.YMapEvent<OrderedExcalidrawElement>,
    transaction: Y.Transaction,
  ) => {
    if (destroyed || transaction.origin === LOCAL_SYNC_ORIGIN) {
      return
    }
    applyRemoteState()
  }

  const handleSynced = ({ state }: { state: boolean }) => {
    if (destroyed || !state) return
    hasSyncedOnce = true
    if (!canvasReady && !api.getAppState()?.isLoading) {
      markCanvasReady()
      return
    }
    syncWhenReady()
  }

  const handleLocalChange = (elements: readonly OrderedExcalidrawElement[]): boolean => {
    if (destroyed || isApplyingRemote) {
      return false
    }

    if (!canvasReady && !api.getAppState()?.isLoading) {
      markCanvasReady()
    }

    // 캔버스 초기화 완료 또는 최초 synced 전에는 Y.Map에 쓰지 않고 synced 시점에 병합 업로드한다
    if (!canvasReady || !hasSyncedOnce) {
      return false
    }

    const changed: OrderedExcalidrawElement[] = []
    for (const el of elements) {
      if (!isSameVersionSnapshot(lastSyncedVersions.get(el.id), el)) {
        changed.push(el)
      }
    }

    if (changed.length === 0) {
      return false
    }

    let wroteAny = false
    ydoc.transact(() => {
      for (const el of changed) {
        const currentInY = yElements.get(el.id)
        const existing = isOrderedElement(currentInY) ? currentInY : undefined
        if (isIncomingNewer(el, existing)) {
          yElements.set(el.id, cloneElement(el))
          lastSyncedVersions.set(el.id, {
            version: el.version,
            versionNonce: el.versionNonce,
            isDeleted: el.isDeleted,
          })
          wroteAny = true
        } else if (existing) {
          lastSyncedVersions.set(existing.id, {
            version: existing.version,
            versionNonce: existing.versionNonce,
            isDeleted: existing.isDeleted,
          })
        }
      }
    }, LOCAL_SYNC_ORIGIN)

    return wroteAny
  }

  yElements.observe(handleYMapChange)
  provider?.on('synced', handleSynced)

  if (canvasReady) {
    syncWhenReady()
  } else {
    // initializeScene()이 isLoading을 false로 바꿀 때 onChange가 호출되며,
    // 혹시 onChange가 오지 않더라도 최대 5초간 100ms 간격으로 isLoading을 재확인한다.
    unsubscribeCanvasReady = api.onChange((_elements, appState) => {
      if (!appState.isLoading) {
        markCanvasReady()
      }
    })
    const pollStartedAt = Date.now()
    readyPollTimer = setInterval(() => {
      if (destroyed || canvasReady) {
        cleanupCanvasReadyWatchers()
        return
      }
      if (!api.getAppState()?.isLoading) {
        markCanvasReady()
        return
      }
      if (Date.now() - pollStartedAt >= CANVAS_READY_POLL_MAX_MS) {
        if (readyPollTimer !== null) {
          clearInterval(readyPollTimer)
          readyPollTimer = null
        }
      }
    }, CANVAS_READY_POLL_INTERVAL_MS)
  }

  return {
    handleLocalChange,
    applyRemoteState,
    destroy: () => {
      if (destroyed) return
      destroyed = true
      cleanupCanvasReadyWatchers()
      yElements.unobserve(handleYMapChange)
      provider?.off('synced', handleSynced)
      lastSyncedVersions.clear()
    },
  }
}
