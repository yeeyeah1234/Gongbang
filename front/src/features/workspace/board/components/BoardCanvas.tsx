import { Excalidraw, MainMenu } from '@excalidraw/excalidraw'
import '@excalidraw/excalidraw/index.css'
import type { OrderedExcalidrawElement } from '@excalidraw/excalidraw/element/types'
import type {
  AppState,
  Collaborator,
  ExcalidrawImperativeAPI,
  SocketId,
  UIOptions,
} from '@excalidraw/excalidraw/types'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { Awareness } from 'y-protocols/awareness'
import type { MemberColorIndex } from '../../../../components/ui/Avatar'
import { useLoginMember } from '../../../auth/store'
import { useWorkspace } from '../../context'
import { useIsPhone } from '../../useIsPhone'
import { DocumentSaveStatus } from '../../document/components/DocumentSaveStatus'
import { isValidMemberColorIndex, resolveCursorColor } from '../../document/cursorColor'
import { NoticeSkeleton } from '../../notice/components/NoticeStates'
import { createExcalidrawYjsBinding, type ExcalidrawYjsBinding } from '../excalidrawYjsBinding'
import {
  useWhiteboardSyncConnection,
  useWhiteboardSyncSession,
  type WhiteboardSyncSessionHandle,
} from '../useWhiteboardSync'

// 포인터 이동 awareness 전송 간격 (부드러운 커서 이동과 네트워크 트래픽 균형)
const POINTER_THROTTLE_MS = 50

const BOARD_UI_OPTIONS: UIOptions = {
  canvasActions: {
    changeViewBackgroundColor: false,
    clearCanvas: false,
    export: false,
    loadScene: false,
    saveAsImage: false,
    saveToActiveFile: false,
    toggleTheme: false,
  },
  tools: {
    image: false,
  },
}

const BOARD_INITIAL_DATA = {
  appState: {
    viewBackgroundColor: '#ffffff',
    currentItemStrokeColor: '#1e1e1e',
    currentItemBackgroundColor: 'transparent',
    currentItemStrokeWidth: 2,
  },
}

const DEFAULT_FALLBACK_COLOR = '#2f6feb'

export interface BoardCanvasProps {
  teamId: number
  boardId: number
  /** 개발 미리보기(/dev/board-preview)에서 sync-server 없이 열 때만 true */
  offlinePreview?: boolean
  onBoardGone: (boardId: number) => void
  onForbidden: () => void
}

interface AwarenessBoardUser {
  memberId: number
  nickname: string
  colorIndex: MemberColorIndex
}

interface AwarenessPointer {
  x: number
  y: number
  tool: 'pointer' | 'laser'
}

interface AwarenessBoardState {
  user?: Partial<AwarenessBoardUser>
  pointer?: AwarenessPointer | null
  button?: 'up' | 'down'
  selectedElementIds?: Record<string, true>
}

function resolveMemberColorHex(colorIndex: MemberColorIndex): string {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return DEFAULT_FALLBACK_COLOR
  }
  const value = window
    .getComputedStyle(document.documentElement)
    .getPropertyValue(`--color-member-${colorIndex}`)
    .trim()
  return value || DEFAULT_FALLBACK_COLOR
}

function isSameAwarenessUser(a: AwarenessBoardUser, b: AwarenessBoardUser): boolean {
  return a.memberId === b.memberId && a.nickname === b.nickname && a.colorIndex === b.colorIndex
}

function isSameSelectedIds(
  a: Record<string, true> | undefined,
  b: Record<string, true> | undefined,
): boolean {
  if (a === b) return true
  const keysA = a ? Object.keys(a) : []
  const keysB = b ? Object.keys(b) : []
  if (keysA.length !== keysB.length) return false
  for (const key of keysA) {
    if (!b?.[key]) return false
  }
  return true
}

function applyAwarenessUser(awareness: Awareness, user: AwarenessBoardUser): void {
  if (awareness.getLocalState() === null) {
    awareness.setLocalState({ user })
    return
  }
  awareness.setLocalStateField('user', user)
}

interface BoardSyncStatusOverlayProps {
  session: WhiteboardSyncSessionHandle
  teamId: number
  boardId: number
  offlinePreview: boolean
  onBoardGone: (boardId: number) => void
  onForbidden: () => void
}

// WS-04: 저장·접속 상태 구독을 자식으로 분리해 저장 상태 변경 시 Excalidraw 캔버스가 리렌더링되지 않게 한다
function BoardSyncStatusOverlay({
  session,
  teamId,
  boardId,
  offlinePreview,
  onBoardGone,
  onForbidden,
}: BoardSyncStatusOverlayProps) {
  const { saveState, retrySync } = useWhiteboardSyncConnection({
    session,
    teamId,
    boardId,
    offlinePreview,
    onBoardGone,
    onForbidden,
  })

  // Excalidraw 기본 오른쪽 아래 버튼(? 도움말)과 겹치지 않도록 바로 위(bottom-16)에 둔다
  return (
    <div className="pointer-events-none absolute right-4 bottom-16 z-10">
      <DocumentSaveStatus state={saveState} onRetry={retrySync} />
    </div>
  )
}

interface BoardCanvasContentProps extends BoardCanvasProps {
  session: WhiteboardSyncSessionHandle
}

// WB-02~10 Excalidraw + Yjs 실시간 동시 편집 캔버스 (BoardCanvasPane이 React.lazy로 불러온다)
export default function BoardCanvas(props: BoardCanvasProps) {
  const { syncSocket } = useWorkspace()
  const session = useWhiteboardSyncSession(
    props.boardId,
    syncSocket ?? null,
    props.offlinePreview ?? false,
  )

  if (!session) {
    return <NoticeSkeleton />
  }

  return <BoardCanvasContent key={session.instanceId} {...props} session={session} />
}

function BoardCanvasContent({
  session,
  teamId,
  boardId,
  offlinePreview = false,
  onBoardGone,
  onForbidden,
}: BoardCanvasContentProps) {
  const { summary, myColorIndex: teamColorIndex } = useWorkspace()
  const loginMember = useLoginMember()
  const isPhone = useIsPhone()

  const [excalidrawAPI, setExcalidrawAPI] = useState<ExcalidrawImperativeAPI | null>(null)
  const bindingRef = useRef<ExcalidrawYjsBinding | null>(null)
  const lastSelectedIdsRef = useRef<Record<string, true>>({})
  const lastAppliedUserRef = useRef<AwarenessBoardUser | null>(null)
  const lastPointerSentAtRef = useRef(0)
  const pendingPointerRef = useRef<{ pointer: AwarenessPointer; button: 'up' | 'down' } | null>(
    null,
  )
  const pointerTimerRef = useRef<number | null>(null)

  const nickname = loginMember?.nickname ?? '팀원'
  const cursorColor = resolveCursorColor({
    memberId: summary.myMemberId,
    colorIndex: teamColorIndex,
  })
  const myMemberId = summary.myMemberId
  const myColorIndex = cursorColor.colorIndex

  // Y.Map('elements') ↔ Excalidraw 양방향 바인딩 연결
  useEffect(() => {
    if (!excalidrawAPI) return
    const binding = createExcalidrawYjsBinding(session.ydoc, excalidrawAPI, {
      provider: session.provider,
      onLocalWrite: session.markUserEdited,
    })
    bindingRef.current = binding
    return () => {
      binding.destroy()
      if (bindingRef.current === binding) {
        bindingRef.current = null
      }
    }
  }, [session, excalidrawAPI])

  // 내 awareness 사용자 정보 설정·갱신 (15초 awareness heartbeat 유지를 위해 연결 직후 비어 있지 않게 설정)
  useEffect(() => {
    const awareness = session.provider?.awareness
    if (!awareness) return

    const nextUser: AwarenessBoardUser = {
      memberId: myMemberId,
      nickname,
      colorIndex: myColorIndex,
    }
    if (lastAppliedUserRef.current && isSameAwarenessUser(lastAppliedUserRef.current, nextUser)) {
      return
    }
    lastAppliedUserRef.current = nextUser
    applyAwarenessUser(awareness, nextUser)
  }, [session, myMemberId, nickname, myColorIndex])

  // 포인터 전송 타이머 정리
  useEffect(() => {
    return () => {
      if (pointerTimerRef.current !== null) {
        window.clearTimeout(pointerTimerRef.current)
        pointerTimerRef.current = null
      }
      pendingPointerRef.current = null
    }
  }, [session])

  // 다른 팀원 커서·이름·선택 요소 표시 (Excalidraw collaborators + Yjs awareness)
  useEffect(() => {
    const awareness = session.provider?.awareness
    if (!awareness || !excalidrawAPI) return

    const syncCollaborators = () => {
      const localClientId = awareness.clientID
      const collaborators = new Map<SocketId, Collaborator>()

      awareness.getStates().forEach((raw, clientId) => {
        if (clientId === localClientId) return
        if (!raw || typeof raw !== 'object') return

        const state = raw as AwarenessBoardState
        const user = state.user
        const remoteNickname =
          typeof user?.nickname === 'string' && user.nickname.trim().length > 0
            ? user.nickname
            : null
        const remoteMemberId =
          typeof user?.memberId === 'number' && Number.isSafeInteger(user.memberId)
            ? user.memberId
            : clientId

        if (!remoteNickname && !state.pointer) return

        const resolvedColor = resolveCursorColor({
          memberId: remoteMemberId,
          colorIndex: isValidMemberColorIndex(user?.colorIndex) ? user.colorIndex : undefined,
        })
        const hex = resolveMemberColorHex(resolvedColor.colorIndex)
        const socketId = String(clientId) as SocketId

        collaborators.set(socketId, {
          id: String(remoteMemberId),
          socketId,
          username: remoteNickname ?? '팀원',
          pointer: state.pointer ?? undefined,
          button: state.button,
          selectedElementIds: state.selectedElementIds,
          color: {
            background: hex,
            stroke: hex,
          },
        })
      })

      excalidrawAPI.updateScene({ collaborators })
    }

    syncCollaborators()
    awareness.on('change', syncCollaborators)
    return () => {
      awareness.off('change', syncCollaborators)
    }
  }, [session, excalidrawAPI])

  const flushPointerToAwareness = useCallback(() => {
    const awareness = session.provider?.awareness
    const pending = pendingPointerRef.current
    if (!awareness || !pending || awareness.getLocalState() === null) {
      return
    }
    pendingPointerRef.current = null
    lastPointerSentAtRef.current = performance.now()
    awareness.setLocalStateField('pointer', pending.pointer)
    awareness.setLocalStateField('button', pending.button)
  }, [session])

  const handlePointerUpdate = useCallback(
    (payload: { pointer: AwarenessPointer; button: 'up' | 'down' }) => {
      const awareness = session.provider?.awareness
      if (!awareness) return

      pendingPointerRef.current = {
        pointer: payload.pointer,
        button: payload.button,
      }

      const elapsed = performance.now() - lastPointerSentAtRef.current
      if (elapsed >= POINTER_THROTTLE_MS) {
        if (pointerTimerRef.current !== null) {
          window.clearTimeout(pointerTimerRef.current)
          pointerTimerRef.current = null
        }
        flushPointerToAwareness()
        return
      }

      if (pointerTimerRef.current === null) {
        pointerTimerRef.current = window.setTimeout(() => {
          pointerTimerRef.current = null
          flushPointerToAwareness()
        }, POINTER_THROTTLE_MS - elapsed)
      }
    },
    [session, flushPointerToAwareness],
  )

  const handleCanvasChange = useCallback(
    (elements: readonly OrderedExcalidrawElement[], appState: AppState) => {
      const wroteLocalEdit = bindingRef.current?.handleLocalChange(elements) ?? false
      if (wroteLocalEdit) {
        session.markUserEdited()
      }

      const awareness = session.provider?.awareness
      if (awareness && awareness.getLocalState() !== null) {
        const nextSelected = appState.selectedElementIds
        if (!isSameSelectedIds(lastSelectedIdsRef.current, nextSelected)) {
          lastSelectedIdsRef.current = { ...nextSelected }
          awareness.setLocalStateField('selectedElementIds', lastSelectedIdsRef.current)
        }
      }
    },
    [session],
  )

  // 휴대폰(<640px)에서는 보기 전용으로 전환하고, 640px 이상으로 넓어지면 편집 가능으로 되돌린다 (상세명세 8-2)
  useEffect(() => {
    if (!excalidrawAPI) return
    excalidrawAPI.updateScene({ appState: { viewModeEnabled: isPhone } })
  }, [excalidrawAPI, isPhone])

  const renderTopRightUI = useCallback(() => null, [])

  return (
    <div className="gb-board relative flex min-h-0 w-full flex-1 flex-col lg:h-full lg:min-h-[520px]">
      {isPhone && (
        <p
          role="status"
          className="shrink-0 border-b border-divider bg-primary-soft px-4 py-2 text-center text-caption font-medium text-primary"
        >
          화이트보드 편집은 PC에서 해 주세요
        </p>
      )}
      <div className="relative min-h-0 w-full flex-1">
        <div className="absolute inset-0">
          <Excalidraw
            excalidrawAPI={setExcalidrawAPI}
            langCode="ko-KR"
            theme="light"
            viewModeEnabled={isPhone}
            isCollaborating={!offlinePreview}
            aiEnabled={false}
            UIOptions={BOARD_UI_OPTIONS}
            initialData={BOARD_INITIAL_DATA}
            onChange={handleCanvasChange}
            onPointerUpdate={handlePointerUpdate}
            renderTopRightUI={renderTopRightUI}
          >
            <MainMenu>
              <MainMenu.DefaultItems.SearchMenu />
              <MainMenu.DefaultItems.Help />
            </MainMenu>
          </Excalidraw>
        </div>
      </div>

      {/* 작업 영역 오른쪽 아래: 저장 상태 (WS-04, 첫 사용자 편집 전 숨김) */}
      <BoardSyncStatusOverlay
        session={session}
        teamId={teamId}
        boardId={boardId}
        offlinePreview={offlinePreview}
        onBoardGone={onBoardGone}
        onForbidden={onForbidden}
      />
    </div>
  )
}
