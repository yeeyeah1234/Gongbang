import { CaptureUpdateAction, reconcileElements } from '@excalidraw/excalidraw'
import type { RemoteExcalidrawElement } from '@excalidraw/excalidraw/data/reconcile'
import type { OrderedExcalidrawElement } from '@excalidraw/excalidraw/element/types'
import type { ExcalidrawImperativeAPI } from '@excalidraw/excalidraw/types'
import * as Y from 'yjs'

// WB-10: Y.Doc 내부에서 화이트보드 요소를 보관하는 Y.Map 키
export const WHITEBOARD_ELEMENTS_MAP_KEY = 'elements'

// 로컬 변경과 원격/초기 SyncStep2 변경을 구분하기 위한 Yjs 트랜잭션 origin
const LOCAL_SYNC_ORIGIN = Symbol('whiteboard-local-sync')

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

export interface ExcalidrawYjsBinding {
  /**
   * Excalidraw onChange에서 호출한다.
   * 실제로 Y.Map에 반영된 사용자 편집이 있으면 true를 돌려준다 (WS-04 첫 사용자 편집 감지).
   */
  handleLocalChange: (elements: readonly OrderedExcalidrawElement[]) => boolean
  /** 현재 Y.Map 상태를 캔버스에 1회 반영한다 (초기 SyncStep2·API 준비 직후). */
  applyRemoteState: () => void
  /** Y.Map observe 해제 및 내부 상태 정리 */
  destroy: () => void
}

/**
 * Excalidraw 0.18 요소 배열 ↔ Y.Map('elements') 양방향 경량 바인딩.
 *
 * - 요소 ID별로 Y.Map에 저장하며 삭제 요소도 isDeleted: true(tombstone) 그대로 보존한다(Ctrl+Z 복원·충돌 수렴).
 * - 원격 반영은 CaptureUpdateAction.NEVER로 적용해 다른 팀원 변경이 내 실행 취소 스택에 섞이지 않게 한다.
 * - LOCAL_SYNC_ORIGIN과 lastSyncedVersions로 원격 반영 직후 onChange 에코 루프를 차단한다.
 */
export function createExcalidrawYjsBinding(
  ydoc: Y.Doc,
  api: ExcalidrawImperativeAPI,
): ExcalidrawYjsBinding {
  const yElements = ydoc.getMap<OrderedExcalidrawElement>(WHITEBOARD_ELEMENTS_MAP_KEY)
  const lastSyncedVersions = new Map<string, ElementVersionSnapshot>()
  let isApplyingRemote = false
  let destroyed = false

  const recordSyncedVersions = (elements: readonly OrderedExcalidrawElement[]) => {
    for (const el of elements) {
      lastSyncedVersions.set(el.id, {
        version: el.version,
        versionNonce: el.versionNonce,
        isDeleted: el.isDeleted,
      })
    }
  }

  const applyRemoteState = () => {
    if (destroyed) return

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

    // updateScene 직후 동기/비동기로 호출되는 onChange가 원격 요소를 재전송하지 않도록 미리 기록한다
    recordSyncedVersions(reconciled)

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

  const handleYMapChange = (
    _event: Y.YMapEvent<OrderedExcalidrawElement>,
    transaction: Y.Transaction,
  ) => {
    if (destroyed || transaction.origin === LOCAL_SYNC_ORIGIN) {
      return
    }
    applyRemoteState()
  }

  const handleLocalChange = (elements: readonly OrderedExcalidrawElement[]): boolean => {
    if (destroyed || isApplyingRemote) {
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
          wroteAny = true
        }
        lastSyncedVersions.set(el.id, {
          version: el.version,
          versionNonce: el.versionNonce,
          isDeleted: el.isDeleted,
        })
      }
    }, LOCAL_SYNC_ORIGIN)

    return wroteAny
  }

  yElements.observe(handleYMapChange)

  // 이미 SyncStep2로 도착해 있는 원격 요소가 있으면 즉시 캔버스에 반영한다
  if (yElements.size > 0) {
    applyRemoteState()
  }

  return {
    handleLocalChange,
    applyRemoteState,
    destroy: () => {
      if (destroyed) return
      destroyed = true
      yElements.unobserve(handleYMapChange)
      lastSyncedVersions.clear()
    },
  }
}
