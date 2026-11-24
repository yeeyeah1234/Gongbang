/**
 * IME 조합(한글 등) 안전 원격 커서 Tiptap 확장.
 *
 * 원본 패키지·버전:
 * - @tiptap/extension-collaboration-caret@3.35.0 (CollaborationCaret)
 * - @tiptap/y-tiptap@3.3.1 (yCursorPlugin, createDecorations)
 *
 * 원본에서 바꾼 지점(3곳):
 * 1. 로컬 awareness 에코 무시: awareness 'change' 이벤트에서 변경된 clientID가 로컬뿐이면
 *    awarenessUpdated 트랜잭션을 발행하지 않는다.
 * 2. IME 조합 중 보류: view.composing 또는 compositionstart~compositionend 구간에는
 *    로컬 커서 발행(awareness.setLocalState)과 원격 awareness 데코레이션 갱신(setMeta)을
 *    보류했다가 compositionend 직후 1회만 반영한다.
 * 3. 원격 커서 위젯 side: 10 → -1: Yjs RelativePosition(assoc: -1)과 방향을 일치시켜
 *    입력 중 위젯이 글자 뒤로 밀렸다가 앞으로 튀는 핑퐁과 addTextblockHacks 간섭을 막는다.
 */
import { Extension, isValidCSSStyleValue } from '@tiptap/core'
import { Plugin, PluginKey, type EditorState, type Selection } from '@tiptap/pm/state'
import { Decoration, DecorationSet, type DecorationAttrs, type EditorView } from '@tiptap/pm/view'
import {
  absolutePositionToRelativePosition,
  defaultAwarenessStateFilter,
  defaultCursorBuilder,
  defaultSelectionBuilder,
  isStructuralTransaction,
  relativePositionToAbsolutePosition,
  setMeta,
  yCursorPluginKey,
  ySyncPluginKey,
} from '@tiptap/y-tiptap'
import type { Awareness } from 'y-protocols/awareness'
import * as Y from 'yjs'

interface CursorUser {
  name: string
  color: string
  [key: string]: unknown
}

interface AwarenessCursor {
  anchor: Record<string, unknown>
  head: Record<string, unknown>
}

interface AwarenessCursorState {
  user?: Record<string, unknown>
  cursor?: AwarenessCursor | null
  lastSelection?: Record<string, unknown>
  [key: string]: unknown
}

interface AwarenessChangePayload {
  added: number[]
  updated: number[]
  removed: number[]
}

interface AwarenessProvider {
  awareness: Awareness | null
}

export interface ImeSafeCollaborationCaretStorage {
  users: Array<{
    clientId: number
    [key: string]: unknown
  }>
}

export interface ImeSafeCollaborationCaretOptions {
  provider: AwarenessProvider | null
  user: Record<string, unknown>
  render: (user: Record<string, unknown>) => HTMLElement
  selectionRender: (user: Record<string, unknown>) => DecorationAttrs
}

interface ImeSafeCursorPluginOptions {
  awarenessStateFilter?: (
    currentClientId: number,
    userClientId: number,
    state: Record<string, unknown>,
  ) => boolean
  cursorBuilder?: (user: CursorUser, clientId: number) => HTMLElement
  selectionBuilder?: (user: CursorUser, clientId: number) => DecorationAttrs
  getSelection?: (state: EditorState) => Selection
}

const rxValidColor = /^#[0-9a-fA-F]{6}$/

const hasCursorUser = (user: Record<string, unknown>): user is CursorUser =>
  typeof user.name === 'string' && typeof user.color === 'string'

const getMappableSyncState = (state: EditorState) => {
  const ystate = ySyncPluginKey.getState(state)
  return ystate?.binding && ystate.binding.invalidContentError === null ? ystate : null
}

// @tiptap/y-tiptap@3.3.1 createDecorations 기반 (변경점 3: 위젯 side: 10 → -1)
const createImeSafeDecorations = (
  state: EditorState,
  awareness: Awareness,
  awarenessFilter: NonNullable<ImeSafeCursorPluginOptions['awarenessStateFilter']>,
  createCursor: (user: CursorUser, clientId: number) => HTMLElement,
  createSelection: NonNullable<ImeSafeCursorPluginOptions['selectionBuilder']>,
): DecorationSet => {
  const ystate = getMappableSyncState(state)
  if (ystate == null || ystate.doc == null) return DecorationSet.create(state.doc, [])
  const y = ystate.doc
  const decorations: Decoration[] = []
  if (ystate.snapshot != null || ystate.prevSnapshot != null || ystate.binding.mapping.size === 0) {
    return DecorationSet.create(state.doc, [])
  }
  awareness.getStates().forEach((rawAw, clientId) => {
    const aw = rawAw as AwarenessCursorState | undefined
    if (!awarenessFilter(y.clientID, clientId, (aw ?? {}) as Record<string, unknown>)) return
    if (aw && aw.cursor != null) {
      const user: Record<string, unknown> = { ...aw.user }
      if (user.color == null) user.color = '#ffa500'
      else if (typeof user.color !== 'string' || !rxValidColor.test(user.color)) {
        console.warn('A user uses an unsupported color format', user)
      }
      if (user.name == null) user.name = `User: ${clientId}`
      if (!hasCursorUser(user)) return
      let anchor = relativePositionToAbsolutePosition(
        y,
        ystate.type,
        Y.createRelativePositionFromJSON(aw.cursor.anchor),
        ystate.binding.mapping,
      )
      let head = relativePositionToAbsolutePosition(
        y,
        ystate.type,
        Y.createRelativePositionFromJSON(aw.cursor.head),
        ystate.binding.mapping,
      )
      if (anchor !== null && head !== null) {
        const maxsize = Math.max(state.doc.content.size - 1, 0)
        anchor = Math.min(anchor, maxsize)
        head = Math.min(head, maxsize)
        decorations.push(
          Decoration.widget(head, () => createCursor(user, clientId), {
            key: `${clientId}`,
            // 변경점 3: Yjs RelativePosition(assoc: -1)과 일치시켜 조합 글자 뒤로 밀리는 핑퐁 방지
            side: -1,
          }),
        )
        const from = Math.min(anchor, head)
        const to = Math.max(anchor, head)
        decorations.push(
          Decoration.inline(from, to, createSelection(user, clientId), {
            inclusiveEnd: true,
            inclusiveStart: false,
          }),
        )
      }
    }
  })
  return DecorationSet.create(state.doc, decorations)
}

const isSameSelection = (
  cursor: { anchor: Y.RelativePosition; head: Y.RelativePosition } | null | undefined,
  selection: { anchor: Y.RelativePosition; head: Y.RelativePosition },
) =>
  cursor != null &&
  Y.compareRelativePositions(
    Y.createRelativePositionFromJSON(cursor.anchor),
    Y.createRelativePositionFromJSON(selection.anchor),
  ) &&
  Y.compareRelativePositions(
    Y.createRelativePositionFromJSON(cursor.head),
    Y.createRelativePositionFromJSON(selection.head),
  )

// @tiptap/y-tiptap@3.3.1 yCursorPlugin 기반 (변경점 1·2·3 적용)
const createImeSafeCursorPlugin = (
  awareness: Awareness,
  {
    awarenessStateFilter = defaultAwarenessStateFilter,
    cursorBuilder = defaultCursorBuilder,
    selectionBuilder = defaultSelectionBuilder,
    getSelection = (state) => state.selection,
  }: ImeSafeCursorPluginOptions = {},
  cursorStateField = 'cursor',
) => {
  if (cursorStateField === 'lastSelection') {
    throw new Error('yCursorPlugin: "lastSelection" is reserved and cannot be the cursor field')
  }
  return new Plugin<DecorationSet>({
    key: yCursorPluginKey,
    state: {
      init(_, state) {
        return createImeSafeDecorations(
          state,
          awareness,
          awarenessStateFilter,
          cursorBuilder,
          selectionBuilder,
        )
      },
      apply(tr, prevState, oldState, newState) {
        const ystate = ySyncPluginKey.getState(newState)
        const yCursorState = tr.getMeta(yCursorPluginKey) as
          { awarenessUpdated?: boolean } | undefined
        const isRemoteChange = Boolean(ystate && ystate.isChangeOrigin)
        if (tr.docChanged && !isRemoteChange && isStructuralTransaction(tr, oldState.doc)) {
          return DecorationSet.empty
        }
        if (isRemoteChange || (yCursorState && yCursorState.awarenessUpdated)) {
          return createImeSafeDecorations(
            newState,
            awareness,
            awarenessStateFilter,
            cursorBuilder,
            selectionBuilder,
          )
        }
        if (tr.docChanged) return prevState.map(tr.mapping, tr.doc)
        return prevState
      },
    },
    props: {
      decorations: (state) => yCursorPluginKey.getState(state) ?? DecorationSet.empty,
    },
    view: (view) => {
      const syncType = ySyncPluginKey.getState(view.state)?.type
      const field = syncType && syncType._item === null ? Y.findRootTypeKey(syncType) : undefined
      let publishedCursor: { anchor: Y.RelativePosition; head: Y.RelativePosition } | null = null
      let publishedEntry: Record<string, unknown> | null = null

      // 변경점 2: IME 조합 중(view.composing 또는 compositionstart~compositionend) 보류 상태
      let isComposing = false
      let pendingAwarenessUpdate = false
      let pendingCursorPublish = false
      let compositionFlushTimer: number | null = null

      const isViewComposing = (editorView: EditorView) => isComposing || editorView.composing

      const clearCompositionFlushTimer = () => {
        if (compositionFlushTimer !== null) {
          window.clearTimeout(compositionFlushTimer)
          compositionFlushTimer = null
        }
      }

      const awarenessListener = ({ added, updated, removed }: AwarenessChangePayload) => {
        if (view.isDestroyed) return
        // 변경점 1: 로컬 awareness 변경만 발생한 에코는 원격 커서 데코레이션을 다시 그리지 않는다
        const hasRemoteChange =
          added.some((id) => id !== awareness.clientID) ||
          updated.some((id) => id !== awareness.clientID) ||
          removed.some((id) => id !== awareness.clientID)
        if (!hasRemoteChange) return
        // 변경점 2: 조합 중 도착한 원격 awareness 변경은 compositionend 이후 1회만 반영한다
        if (isViewComposing(view)) {
          pendingAwarenessUpdate = true
          return
        }
        setMeta(view, yCursorPluginKey, { awarenessUpdated: true })
      }

      const publish = (
        current: Record<string, unknown>,
        selection: { anchor: Y.RelativePosition; head: Y.RelativePosition },
        focused: boolean,
        cursor: { anchor: Y.RelativePosition; head: Y.RelativePosition } | null,
      ) => {
        if (field === undefined || typeof awareness.setLocalState !== 'function') {
          if (cursor !== current[cursorStateField]) {
            awareness.setLocalStateField(cursorStateField, cursor)
          }
          return
        }
        publishedEntry = {
          ...selection,
          time: Date.now(),
          focused,
        }
        const prevLastSelection =
          current.lastSelection && typeof current.lastSelection === 'object'
            ? (current.lastSelection as Record<string, unknown>)
            : {}
        awareness.setLocalState({
          ...current,
          [cursorStateField]: cursor,
          lastSelection: {
            ...prevLastSelection,
            [field]: publishedEntry,
          },
        })
      }

      const releaseCursor = (current: Record<string, unknown>) => {
        if (publishedCursor === null) return
        const cursor = current[cursorStateField]
        const ownsCursor = cursor === publishedCursor
        const lastSelection =
          current.lastSelection && typeof current.lastSelection === 'object'
            ? (current.lastSelection as Record<string, unknown>)
            : undefined
        if (field !== undefined && lastSelection?.[field] === publishedEntry) {
          publish(
            current,
            publishedCursor,
            false,
            ownsCursor
              ? null
              : (cursor as { anchor: Y.RelativePosition; head: Y.RelativePosition } | null),
          )
        } else if (ownsCursor) {
          awareness.setLocalStateField(cursorStateField, null)
        }
        publishedCursor = null
        publishedEntry = null
      }

      const updateCursorInfo = (editorView: EditorView) => {
        // 변경점 2: 조합 중에는 로컬 awareness 커서를 발행하지 않고 compositionend 이후 1회 발행한다
        if (isViewComposing(editorView)) {
          pendingCursorPublish = true
          return
        }
        const ystate = getMappableSyncState(editorView.state)
        if (!ystate) {
          const localState = awareness.getLocalState()
          if (localState !== null) releaseCursor(localState)
          return
        }
        const localState = awareness.getLocalState()
        if (localState === null) return
        const current = localState || {}
        if (!editorView.hasFocus()) {
          releaseCursor(current)
          return
        }
        const selection = getSelection(editorView.state)
        const anchor = absolutePositionToRelativePosition(
          selection.anchor,
          ystate.type,
          ystate.binding.mapping,
        )
        const head = absolutePositionToRelativePosition(
          selection.head,
          ystate.type,
          ystate.binding.mapping,
        )
        const cursor = current[cursorStateField] as
          { anchor: Y.RelativePosition; head: Y.RelativePosition } | null | undefined
        if (cursor === publishedCursor && isSameSelection(cursor, { anchor, head })) return
        publishedCursor = { anchor, head }
        publish(current, publishedCursor, true, publishedCursor)
      }

      const onFocusChange = () => updateCursorInfo(view)

      const onCompositionStart = () => {
        isComposing = true
        clearCompositionFlushTimer()
      }

      const onCompositionEnd = () => {
        isComposing = false
        clearCompositionFlushTimer()
        compositionFlushTimer = window.setTimeout(() => {
          compositionFlushTimer = null
          if (view.isDestroyed || isViewComposing(view)) return
          if (pendingCursorPublish) {
            pendingCursorPublish = false
            updateCursorInfo(view)
          }
          if (pendingAwarenessUpdate) {
            pendingAwarenessUpdate = false
            setMeta(view, yCursorPluginKey, { awarenessUpdated: true })
          }
        }, 0)
      }

      awareness.on('change', awarenessListener)
      view.dom.addEventListener('focusin', onFocusChange)
      view.dom.addEventListener('focusout', onFocusChange)
      view.dom.addEventListener('compositionstart', onCompositionStart)
      view.dom.addEventListener('compositionend', onCompositionEnd)

      return {
        update: updateCursorInfo,
        destroy: () => {
          clearCompositionFlushTimer()
          view.dom.removeEventListener('focusin', onFocusChange)
          view.dom.removeEventListener('focusout', onFocusChange)
          view.dom.removeEventListener('compositionstart', onCompositionStart)
          view.dom.removeEventListener('compositionend', onCompositionEnd)
          awareness.off('change', awarenessListener)
          if (publishedCursor === null) return
          const localState = awareness.getLocalState()
          if (localState !== null) releaseCursor(localState)
        },
      }
    },
  })
}

// @tiptap/extension-collaboration-caret@3.35.0 기반
const awarenessStatesToArray = (states: Map<number, Record<string, unknown>>) => {
  return Array.from(states.entries()).map(([key, value]) => {
    if (value && value.user && typeof value.user === 'object') {
      return {
        clientId: key,
        ...(value.user as Record<string, unknown>),
      }
    }
    return { clientId: key }
  })
}

const isValidUserColor = (color: unknown): color is string => {
  return typeof color === 'string' && isValidCSSStyleValue(color) && /^#[0-9a-fA-F]{6}$/.test(color)
}

const sanitizeUserColor = (user: Record<string, unknown>) => ({
  ...user,
  color: isValidUserColor(user.color) ? user.color : 'transparent',
})

export const ImeSafeCollaborationCaret = Extension.create<
  ImeSafeCollaborationCaretOptions,
  ImeSafeCollaborationCaretStorage
>({
  name: 'collaborationCaret',
  priority: 999,

  addOptions() {
    return {
      provider: null,
      user: {
        name: null,
        color: null,
      },
      render: (user) => {
        const cursor = document.createElement('span')
        const { color } = sanitizeUserColor(user)
        cursor.classList.add('collaboration-carets__caret')
        cursor.style.borderColor = color
        const label = document.createElement('div')
        label.classList.add('collaboration-carets__label')
        label.style.backgroundColor = color
        label.insertBefore(document.createTextNode(String(user.name ?? '')), null)
        cursor.insertBefore(label, null)
        return cursor
      },
      selectionRender: (user) => {
        if (!isValidUserColor(user.color)) return {}
        return defaultSelectionBuilder({
          name: String(user.name ?? ''),
          color: user.color,
        })
      },
    }
  },

  onCreate() {
    if (!this.options.provider) {
      throw new Error('The "provider" option is required for the CollaborationCaret extension')
    }
  },

  addStorage() {
    return { users: [] }
  },

  addProseMirrorPlugins() {
    const { provider, user } = this.options
    const awareness = provider?.awareness
    if (!awareness) return []
    const storage = this.storage

    return [
      new Plugin({
        key: new PluginKey('collaborationCaretAwarenessListener'),
        view: () => {
          const onAwarenessUpdate = () => {
            storage.users = awarenessStatesToArray(awareness.states)
          }
          awareness.setLocalStateField('user', user)
          storage.users = awarenessStatesToArray(awareness.states)
          awareness.on('update', onAwarenessUpdate)
          return {
            destroy: () => {
              awareness.off('update', onAwarenessUpdate)
              storage.users = []
            },
          }
        },
      }),
      createImeSafeCursorPlugin(awareness, {
        cursorBuilder: (currentUser) => this.options.render(sanitizeUserColor(currentUser)),
        selectionBuilder: (currentUser) =>
          this.options.selectionRender(sanitizeUserColor(currentUser)),
      }),
    ]
  },
})
