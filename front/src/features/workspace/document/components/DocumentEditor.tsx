import { Collaboration } from '@tiptap/extension-collaboration'
import { CollaborationCaret } from '@tiptap/extension-collaboration-caret'
import { TaskItem, TaskList } from '@tiptap/extension-list'
import { EditorContent, useEditor, type Editor } from '@tiptap/react'
import { StarterKit } from '@tiptap/starter-kit'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useLoginMember } from '../../../auth/store'
import { useWorkspace } from '../../context'
import { isValidMemberColorIndex, resolveCursorColor } from '../cursorColor'
import { ListMaxDepth } from '../listDepthExtension'
import { isSafeHttpUrl, normalizeSafeHttpUrl } from '../urlSafety'
import { useDocumentSync } from '../useDocumentSync'
import { DocumentSaveStatus } from './DocumentSaveStatus'
import { DocumentToolbar } from './DocumentToolbar'
import { LinkModal } from './LinkModal'

// DOC-09: 3초 동안 입력·커서 변화가 없으면 원격 커서 이름표만 숨긴다
const CARET_LABEL_IDLE_MS = 3_000

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

// DOC-05~10 Tiptap + Yjs 동시 편집 에디터 (DocumentEditorPane이 React.lazy로 불러온다)
export default function DocumentEditor({
  teamId,
  documentId,
  title,
  offlinePreview = false,
  onDocumentGone,
  onForbidden,
}: DocumentEditorProps) {
  const { summary } = useWorkspace()
  const loginMember = useLoginMember()
  const editorContainerRef = useRef<HTMLDivElement | null>(null)
  const [caretIdleTracker] = useState(createCaretIdleTracker)

  const { session, saveState, retrySync } = useDocumentSync({
    teamId,
    documentId,
    offlinePreview,
    onDocumentGone,
    onForbidden,
  })

  const nickname = loginMember?.nickname ?? '팀원'
  const cursorColor = resolveCursorColor({ memberId: summary.myMemberId })
  const myMemberId = summary.myMemberId
  const myColorIndex = cursorColor.colorIndex
  const myClientKey = `${myMemberId}-${session.instanceId}`

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
        CollaborationCaret.configure({
          provider: session.provider,
          user: {
            name: nickname,
            colorIndex: myColorIndex,
            clientKey: myClientKey,
            memberId: myMemberId,
          },
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
    // 사용자 정보의 초기값은 마운트 시점에 넘기고, 이후 변경은 아래 updateUser effect가 반영한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, caretIdleTracker])

  const editor = useEditor(
    {
      extensions,
      editorProps: {
        attributes: {
          'aria-label': '문서 본문',
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
      },
    },
    [extensions],
  )

  // 로그인 사용자 닉네임·색이 늦게 채워지면 awareness user 정보 갱신
  useEffect(() => {
    if (!isEditorAlive(editor) || !session.provider) return
    editor.commands.updateUser({
      name: nickname,
      colorIndex: myColorIndex,
      clientKey: myClientKey,
      memberId: myMemberId,
    })
  }, [editor, session.provider, nickname, myColorIndex, myClientKey, myMemberId])

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
        className="gb-editor min-h-0 flex-1 overflow-y-auto px-6 py-6 pb-16 lg:px-8"
        onClick={(event) => {
          if (event.target === event.currentTarget && isEditorAlive(editor)) {
            editor.commands.focus('end')
          }
        }}
      >
        <EditorContent editor={editor} className="min-h-full" />
      </div>

      {/* 작업 영역 오른쪽 아래: 저장 상태 (WS-04) */}
      <div className="pointer-events-none absolute right-4 bottom-4 z-10">
        <DocumentSaveStatus state={saveState} onRetry={retrySync} />
      </div>

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
