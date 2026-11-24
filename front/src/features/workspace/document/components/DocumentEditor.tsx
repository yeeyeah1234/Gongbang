import { Collaboration } from '@tiptap/extension-collaboration'
import { TaskItem, TaskList } from '@tiptap/extension-list'
import type { EditorProps } from '@tiptap/pm/view'
import { EditorContent, useEditor, type Editor } from '@tiptap/react'
import { StarterKit } from '@tiptap/starter-kit'
import { useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'
import type { Awareness } from 'y-protocols/awareness'
import { useLoginMember } from '../../../auth/store'
import { useWorkspace } from '../../context'
import { isValidMemberColorIndex, resolveCursorColor } from '../cursorColor'
import { ImeSafeCollaborationCaret } from '../imeSafeCollaborationCaret'
import { ListMaxDepth } from '../listDepthExtension'
import { isSafeHttpUrl, normalizeSafeHttpUrl } from '../urlSafety'
import {
  useDocumentSyncConnection,
  useDocumentSyncSession,
  type SyncSessionHandle,
} from '../useDocumentSync'
import { DocumentSaveStatus } from './DocumentSaveStatus'
import { DocumentToolbar } from './DocumentToolbar'
import { LinkModal } from './LinkModal'

// DOC-09: 3초 동안 입력·커서 변화가 없으면 원격 커서 이름표만 숨긴다
const CARET_LABEL_IDLE_MS = 3_000

// useEditor 옵션 객체가 매 렌더 새로 만들어지지 않도록 모듈 상수로 고정한다
const EDITOR_PROPS: EditorProps = {
  attributes: {
    'aria-label': '문서 본문',
    class: 'px-6 pt-6 pb-16 lg:px-8',
  },
  // 선택 영역이 없는 상태에서 http(s) URL만 붙여넣을 때도 즉시 클릭 가능한 링크로 삽입
  handlePaste: (view, event) => {
    const text = event.clipboardData?.getData('text/plain')?.trim() ?? ''
    if (!text || /\s/.test(text)) return false
    const safeUrl = normalizeSafeHttpUrl(text)
    if (!safeUrl) return false
    const linkMarkType = view.state.schema.marks.link
    if (!linkMarkType) return false

    const { from, empty } = view.state.selection
    if (!empty) {
      // 선택 텍스트가 있으면 Tiptap 기본 linkOnPaste가 처리
      return false
    }
    const linkMark = linkMarkType.create({
      href: safeUrl,
      target: '_blank',
      rel: 'noopener noreferrer nofollow',
    })
    const tr = view.state.tr.insert(from, view.state.schema.text(safeUrl, [linkMark]))
    view.dispatch(tr.scrollIntoView())
    return true
  },
}

export interface DocumentEditorProps {
  teamId: number
  documentId: number
  title: string
  /** 개발 미리보기(/dev/document-preview)에서 sync-server 없이 열 때만 true */
  offlinePreview?: boolean
  onDocumentGone: (documentId: number) => void
  onForbidden: () => void
}

interface AwarenessUser {
  name: string
  colorIndex: number
  clientKey: string
  memberId: number
}

interface CaretIdleTracker {
  isHidden: (clientKey: string) => boolean
  setHidden: (clientKey: string, hidden: boolean) => void
  clear: () => void
}

function createCaretIdleTracker(): CaretIdleTracker {
  const hiddenKeys = new Set<string>()
  return {
    isHidden: (clientKey) => hiddenKeys.has(clientKey),
    setHidden: (clientKey, hidden) => {
      if (hidden) {
        hiddenKeys.add(clientKey)
      } else {
        hiddenKeys.delete(clientKey)
      }
    },
    clear: () => {
      hiddenKeys.clear()
    },
  }
}

function isEditorAlive(editor: Editor | null): editor is Editor {
  return editor !== null && !editor.isDestroyed
}

function isSameAwarenessUser(a: AwarenessUser, b: AwarenessUser): boolean {
  return (
    a.name === b.name &&
    a.colorIndex === b.colorIndex &&
    a.clientKey === b.clientKey &&
    a.memberId === b.memberId
  )
}

function applyAwarenessUser(awareness: Awareness, user: AwarenessUser): void {
  if (awareness.getLocalState() === null) {
    awareness.setLocalState({ user })
    return
  }
  awareness.setLocalStateField('user', user)
}

interface DocumentSyncStatusOverlayProps {
  session: SyncSessionHandle
  teamId: number
  documentId: number
  offlinePreview: boolean
  onDocumentGone: (documentId: number) => void
  onForbidden: () => void
}

// WS-04: 저장·접속 상태 구독을 별도 자식으로 분리해 입력 중 DocumentEditor 전체가 리렌더링되지 않게 한다
function DocumentSyncStatusOverlay({
  session,
  teamId,
  documentId,
  offlinePreview,
  onDocumentGone,
  onForbidden,
}: DocumentSyncStatusOverlayProps) {
  const { saveState, retrySync } = useDocumentSyncConnection({
    session,
    teamId,
    documentId,
    offlinePreview,
    onDocumentGone,
    onForbidden,
  })

  return (
    <div className="pointer-events-none absolute right-4 bottom-4 z-10">
      <DocumentSaveStatus state={saveState} onRetry={retrySync} />
    </div>
  )
}

// DOC-05~10 Tiptap + Yjs 동시 편집 에디터 (DocumentEditorPane이 React.lazy로 불러온다)
export default function DocumentEditor({
  teamId,
  documentId,
  title,
  offlinePreview = false,
  onDocumentGone,
  onForbidden,
}: DocumentEditorProps) {
  const { summary, myColorIndex: teamColorIndex } = useWorkspace()
  const loginMember = useLoginMember()
  const editorContainerRef = useRef<HTMLDivElement | null>(null)
  const editorRef = useRef<Editor | null>(null)
  const composingRef = useRef(false)
  const hasPendingLabelSyncRef = useRef(false)
  const pendingUserRef = useRef<AwarenessUser | null>(null)
  const [caretIdleTracker] = useState(createCaretIdleTracker)

  const session = useDocumentSyncSession(documentId, offlinePreview)

  const nickname = loginMember?.nickname ?? '팀원'
  const cursorColor = resolveCursorColor({
    memberId: summary.myMemberId,
    colorIndex: teamColorIndex,
  })
  const myMemberId = summary.myMemberId
  const myColorIndex = cursorColor.colorIndex
  const myClientKey = `${myMemberId}-${session.instanceId}`

  // 연결 시점의 사용자 정보를 1회 고정해 extensions가 문서 전환 외에는 재생성되지 않게 한다
  const [initialUser] = useState<AwarenessUser>(() => ({
    name: nickname,
    colorIndex: myColorIndex,
    clientKey: myClientKey,
    memberId: myMemberId,
  }))
  const lastAppliedUserRef = useRef<AwarenessUser>(initialUser)

  const [linkModalState, setLinkModalState] = useState<{
    open: boolean
    initialUrl: string
    hasExistingLink: boolean
  }>({ open: false, initialUrl: '', hasExistingLink: false })

  const extensions = useMemo(() => {
    const baseExtensions = [
      StarterKit.configure({
        // DOC-08: 협업 확장의 Yjs UndoManager를 쓰기 위해 기본 로컬 히스토리는 끈다
        undoRedo: false,
        heading: {
          levels: [1, 2, 3],
        },
        link: {
          openOnClick: true,
          autolink: true,
          linkOnPaste: true,
          defaultProtocol: 'https',
          HTMLAttributes: {
            target: '_blank',
            rel: 'noopener noreferrer nofollow',
          },
          isAllowedUri: (url) => isSafeHttpUrl(url),
          shouldAutoLink: (url) => isSafeHttpUrl(url),
        },
      }),
      TaskList,
      TaskItem.configure({
        nested: true,
      }),
      ListMaxDepth,
      Collaboration.configure({
        document: session.ydoc,
      }),
    ]

    if (session.provider) {
      baseExtensions.push(
        ImeSafeCollaborationCaret.configure({
          provider: session.provider,
          user: { ...initialUser },
          render: (rawUser) => {
            const user = rawUser as Partial<AwarenessUser>
            const colorIndex = isValidMemberColorIndex(user.colorIndex) ? user.colorIndex : 1
            const clientKey =
              typeof user.clientKey === 'string' && user.clientKey.length > 0
                ? user.clientKey
                : String(user.name ?? 'user')

            const caret = document.createElement('span')
            caret.className = 'gb-caret'
            caret.dataset.memberColor = String(colorIndex)

            const label = document.createElement('span')
            label.className = 'gb-caret-label'
            label.dataset.clientKey = clientKey
            if (caretIdleTracker.isHidden(clientKey)) {
              label.dataset.hidden = 'true'
            }
            label.textContent = typeof user.name === 'string' && user.name ? user.name : '팀원'

            caret.appendChild(label)
            return caret
          },
          selectionRender: (rawUser) => {
            const user = rawUser as Partial<AwarenessUser>
            const colorIndex = isValidMemberColorIndex(user.colorIndex) ? user.colorIndex : 1
            return {
              nodeName: 'span',
              class: 'gb-caret-selection',
              'data-member-color': String(colorIndex),
            }
          },
        }),
      )
    }

    return baseExtensions
  }, [session, caretIdleTracker, initialUser])

  const editor = useEditor(
    {
      extensions,
      editorProps: EDITOR_PROPS,
    },
    [extensions],
  )

  useEffect(() => {
    editorRef.current = editor
  }, [editor])

  // IME 조합 구간에는 에디터 DOM(이름표 숨김)과 awareness user 갱신을 보류했다가 종료 후 1회 반영한다
  useEffect(() => {
    const container = editorContainerRef.current
    if (!container) return
    let flushTimer: number | null = null

    const clearFlushTimer = () => {
      if (flushTimer !== null) {
        window.clearTimeout(flushTimer)
        flushTimer = null
      }
    }

    const handleCompositionStart = () => {
      composingRef.current = true
      clearFlushTimer()
    }

    const handleCompositionEnd = () => {
      composingRef.current = false
      clearFlushTimer()
      flushTimer = window.setTimeout(() => {
        flushTimer = null
        const activeEditor = editorRef.current
        if (composingRef.current || (isEditorAlive(activeEditor) && activeEditor.view.composing)) {
          return
        }
        if (hasPendingLabelSyncRef.current) {
          hasPendingLabelSyncRef.current = false
          const root = editorContainerRef.current
          if (root) {
            const labels = root.querySelectorAll<HTMLElement>('.gb-caret-label')
            labels.forEach((label) => {
              const key = label.dataset.clientKey
              if (key) {
                label.dataset.hidden = caretIdleTracker.isHidden(key) ? 'true' : 'false'
              }
            })
          }
        }
        const pendingUser = pendingUserRef.current
        const awareness = session.provider?.awareness
        if (pendingUser && awareness) {
          pendingUserRef.current = null
          applyAwarenessUser(awareness, pendingUser)
        }
      }, 0)
    }

    container.addEventListener('compositionstart', handleCompositionStart)
    container.addEventListener('compositionend', handleCompositionEnd)
    return () => {
      clearFlushTimer()
      composingRef.current = false
      container.removeEventListener('compositionstart', handleCompositionStart)
      container.removeEventListener('compositionend', handleCompositionEnd)
    }
  }, [session, caretIdleTracker])

  // 로그인 사용자 닉네임·색이 연결 이후 실제로 바뀐 경우에만 awareness user 정보 갱신
  useEffect(() => {
    const awareness = session.provider?.awareness
    if (!awareness) return
    const nextUser: AwarenessUser = {
      name: nickname,
      colorIndex: myColorIndex,
      clientKey: myClientKey,
      memberId: myMemberId,
    }
    if (isSameAwarenessUser(lastAppliedUserRef.current, nextUser)) return
    lastAppliedUserRef.current = nextUser

    const activeEditor = editorRef.current
    if (composingRef.current || (isEditorAlive(activeEditor) && activeEditor.view.composing)) {
      pendingUserRef.current = nextUser
      return
    }
    pendingUserRef.current = null
    applyAwarenessUser(awareness, nextUser)
  }, [session, nickname, myColorIndex, myClientKey, myMemberId])

  // DOC-06: Ctrl+K / Cmd+K 링크 삽입·수정 단축키
  useEffect(() => {
    const container = editorContainerRef.current
    if (!container || !isEditorAlive(editor)) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (!isEditorAlive(editor)) return
      if ((event.ctrlKey || event.metaKey) && !event.altKey && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        const existingHref = (editor.getAttributes('link').href as string | undefined) ?? ''
        setLinkModalState({
          open: true,
          initialUrl: existingHref,
          hasExistingLink: editor.isActive('link'),
        })
      }
    }

    container.addEventListener('keydown', handleKeyDown)
    return () => container.removeEventListener('keydown', handleKeyDown)
  }, [editor])

  // DOC-09: 원격 커서 활동 감지 → 3초 동안 변화가 없으면 해당 이름표만 숨김
  useEffect(() => {
    const awareness = session.provider?.awareness
    if (!awareness) return

    const clientIdToKey = new Map<number, string>()
    const timers = new Map<string, number>()

    const setLabelHiddenDom = (clientKey: string, hidden: boolean) => {
      // IME 조합 중에는 에디터 내부 DOM 속성을 건드리지 않고 compositionend 후 일괄 반영한다
      const activeEditor = editorRef.current
      if (composingRef.current || (isEditorAlive(activeEditor) && activeEditor.view.composing)) {
        hasPendingLabelSyncRef.current = true
        return
      }
      const root = editorContainerRef.current
      if (!root) return
      const labels = root.querySelectorAll<HTMLElement>('.gb-caret-label')
      labels.forEach((label) => {
        if (label.dataset.clientKey === clientKey) {
          label.dataset.hidden = hidden ? 'true' : 'false'
        }
      })
    }

    const markActive = (clientKey: string) => {
      caretIdleTracker.setHidden(clientKey, false)
      setLabelHiddenDom(clientKey, false)
      const prevTimer = timers.get(clientKey)
      if (prevTimer !== undefined) {
        window.clearTimeout(prevTimer)
      }
      const nextTimer = window.setTimeout(() => {
        caretIdleTracker.setHidden(clientKey, true)
        setLabelHiddenDom(clientKey, true)
        timers.delete(clientKey)
      }, CARET_LABEL_IDLE_MS)
      timers.set(clientKey, nextTimer)
    }

    const resolveKey = (clientId: number): string | null => {
      const state = awareness.getStates().get(clientId) as
        { user?: Partial<AwarenessUser> } | undefined
      const user = state?.user
      if (!user) return clientIdToKey.get(clientId) ?? null
      const key =
        typeof user.clientKey === 'string' && user.clientKey.length > 0
          ? user.clientKey
          : String(user.name ?? clientId)
      clientIdToKey.set(clientId, key)
      return key
    }

    const handleAwarenessChange = ({
      added,
      updated,
      removed,
    }: {
      added: number[]
      updated: number[]
      removed: number[]
    }) => {
      const localId = awareness.clientID

      for (const id of [...added, ...updated]) {
        if (id === localId) continue
        const key = resolveKey(id)
        if (key) markActive(key)
      }

      for (const id of removed) {
        const key = clientIdToKey.get(id)
        if (key) {
          const timer = timers.get(key)
          if (timer !== undefined) {
            window.clearTimeout(timer)
            timers.delete(key)
          }
          caretIdleTracker.setHidden(key, false)
          clientIdToKey.delete(id)
        }
      }
    }

    // 이미 접속해 있던 원격 사용자도 최초 3초 타이머 시작
    awareness.getStates().forEach((_state, clientId) => {
      if (clientId === awareness.clientID) return
      const key = resolveKey(clientId)
      if (key) markActive(key)
    })

    awareness.on('change', handleAwarenessChange)
    return () => {
      awareness.off('change', handleAwarenessChange)
      timers.forEach((timer) => window.clearTimeout(timer))
      timers.clear()
      caretIdleTracker.clear()
    }
  }, [session, caretIdleTracker])

  const openLinkModal = () => {
    if (!isEditorAlive(editor)) return
    const existingHref = (editor.getAttributes('link').href as string | undefined) ?? ''
    setLinkModalState({
      open: true,
      initialUrl: existingHref,
      hasExistingLink: editor.isActive('link'),
    })
  }

  const closeLinkModal = () => {
    setLinkModalState((prev) => ({ ...prev, open: false }))
    if (isEditorAlive(editor)) {
      editor.commands.focus()
    }
  }

  const handleApplyLink = (url: string) => {
    if (!isEditorAlive(editor)) {
      setLinkModalState((prev) => ({ ...prev, open: false }))
      return
    }
    const { empty } = editor.state.selection
    if (empty && !editor.isActive('link')) {
      // 선택 영역이 없으면 링크 주소를 텍스트로 삽입하면서 링크 적용
      editor
        .chain()
        .focus()
        .insertContent({
          type: 'text',
          text: url,
          marks: [
            {
              type: 'link',
              attrs: {
                href: url,
                target: '_blank',
                rel: 'noopener noreferrer nofollow',
              },
            },
          ],
        })
        .run()
    } else {
      editor
        .chain()
        .focus()
        .extendMarkRange('link')
        .setLink({
          href: url,
          target: '_blank',
          rel: 'noopener noreferrer nofollow',
        })
        .run()
    }
    setLinkModalState((prev) => ({ ...prev, open: false }))
  }

  const handleRemoveLink = () => {
    if (isEditorAlive(editor)) {
      editor.chain().focus().extendMarkRange('link').unsetLink().run()
    }
    setLinkModalState((prev) => ({ ...prev, open: false }))
  }

  const handleEditorBodyClick = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (!isEditorAlive(editor)) return

    // 세로 스크롤바 클릭은 무시한다
    const containerRect = event.currentTarget.getBoundingClientRect()
    if (event.clientX > containerRect.left + event.currentTarget.clientWidth) return

    // 드래그로 텍스트를 선택한 직후라면 선택 영역을 유지한다
    const domSelection = window.getSelection()
    if (domSelection && !domSelection.isCollapsed) return

    const prosemirror = editor.view.dom
    const target = event.target
    // 본문 블록 내부(문단·링크·체크박스 등)를 직접 클릭한 경우는 ProseMirror 기본 처리에 맡긴다
    if (target instanceof Node && prosemirror.contains(target) && target !== prosemirror) {
      return
    }

    // 문단 사이 간격이나 줄 좌우·상단 여백을 클릭한 경우도 끝으로 끌려가지 않게 둔다
    const lastChild = prosemirror.lastElementChild
    if (lastChild && event.clientY <= lastChild.getBoundingClientRect().bottom) {
      return
    }

    editor.commands.focus('end')
  }

  return (
    <div className="relative flex h-full min-h-[360px] flex-col">
      {/* 상단 고정: 문서 제목 + 서식 툴바 */}
      <header className="shrink-0 border-b border-divider px-6 py-4 lg:px-8">
        <h1 className="text-heading font-bold wrap-anywhere text-text">{title}</h1>
      </header>
      <DocumentToolbar editor={editor} onOpenLinkModal={openLinkModal} />

      {/* 본문만 스크롤 */}
      <div
        ref={editorContainerRef}
        className="gb-editor flex min-h-0 flex-1 cursor-text flex-col overflow-y-auto"
        onClick={handleEditorBodyClick}
      >
        <EditorContent editor={editor} className="flex w-full min-w-0 shrink-0 grow flex-col" />
      </div>

      {/* 작업 영역 오른쪽 아래: 저장 상태 (WS-04) */}
      <DocumentSyncStatusOverlay
        session={session}
        teamId={teamId}
        documentId={documentId}
        offlinePreview={offlinePreview}
        onDocumentGone={onDocumentGone}
        onForbidden={onForbidden}
      />

      <LinkModal
        open={linkModalState.open}
        initialUrl={linkModalState.initialUrl}
        hasExistingLink={linkModalState.hasExistingLink}
        onSubmit={handleApplyLink}
        onRemove={handleRemoveLink}
        onClose={closeLinkModal}
      />
    </div>
  )
}
