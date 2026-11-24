import { useEditorState, type Editor } from '@tiptap/react'
import type { MouseEvent, ReactNode } from 'react'

interface DocumentToolbarProps {
  editor: Editor | null
  onOpenLinkModal: () => void
}

interface ToolbarState {
  bold: boolean
  italic: boolean
  underline: boolean
  strike: boolean
  h1: boolean
  h2: boolean
  h3: boolean
  bulletList: boolean
  orderedList: boolean
  taskList: boolean
  link: boolean
  codeBlock: boolean
}

const DEFAULT_TOOLBAR_STATE: ToolbarState = {
  bold: false,
  italic: false,
  underline: false,
  strike: false,
  h1: false,
  h2: false,
  h3: false,
  bulletList: false,
  orderedList: false,
  taskList: false,
  link: false,
  codeBlock: false,
}

// W-01 / DOC-05~07 문서 에디터 상단 고정 툴바
export function DocumentToolbar({ editor, onOpenLinkModal }: DocumentToolbarProps) {
  const active =
    useEditorState<ToolbarState>({
      editor,
      selector: ({ editor: current }) => {
        if (!current || current.isDestroyed) return DEFAULT_TOOLBAR_STATE
        return {
          bold: current.isActive('bold'),
          italic: current.isActive('italic'),
          underline: current.isActive('underline'),
          strike: current.isActive('strike'),
          h1: current.isActive('heading', { level: 1 }),
          h2: current.isActive('heading', { level: 2 }),
          h3: current.isActive('heading', { level: 3 }),
          bulletList: current.isActive('bulletList'),
          orderedList: current.isActive('orderedList'),
          taskList: current.isActive('taskList'),
          link: current.isActive('link'),
          codeBlock: current.isActive('codeBlock'),
        }
      },
    }) ?? DEFAULT_TOOLBAR_STATE

  const disabled = !editor || editor.isDestroyed

  const runWithEditor = (action: (activeEditor: Editor) => void) => {
    if (!editor || editor.isDestroyed) return
    action(editor)
  }

  const keepFocus = (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault()
  }

  return (
    <div
      role="toolbar"
      aria-label="문서 서식 도구"
      className="flex shrink-0 flex-wrap items-center gap-1 border-b border-divider bg-surface px-4 py-2 lg:px-6"
    >
      {/* 인라인 서식 */}
      <ToolbarButton
        label="굵게"
        title="굵게 (Ctrl+B)"
        active={active.bold}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) => activeEditor.chain().focus().toggleBold().run())
        }
      >
        <span className="font-bold">B</span>
      </ToolbarButton>
      <ToolbarButton
        label="기울임"
        title="기울임 (Ctrl+I)"
        active={active.italic}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) => activeEditor.chain().focus().toggleItalic().run())
        }
      >
        <span className="italic">I</span>
      </ToolbarButton>
      <ToolbarButton
        label="밑줄"
        title="밑줄 (Ctrl+U)"
        active={active.underline}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) => activeEditor.chain().focus().toggleUnderline().run())
        }
      >
        <span className="underline">U</span>
      </ToolbarButton>
      <ToolbarButton
        label="취소선"
        title="취소선 (Ctrl+Shift+X)"
        active={active.strike}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) => activeEditor.chain().focus().toggleStrike().run())
        }
      >
        <span className="line-through">S</span>
      </ToolbarButton>

      <ToolbarDivider />

      {/* 제목 1~3 */}
      <ToolbarButton
        label="제목 1"
        title="제목 1 (# )"
        active={active.h1}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) =>
            activeEditor.chain().focus().toggleHeading({ level: 1 }).run(),
          )
        }
      >
        H1
      </ToolbarButton>
      <ToolbarButton
        label="제목 2"
        title="제목 2 (## )"
        active={active.h2}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) =>
            activeEditor.chain().focus().toggleHeading({ level: 2 }).run(),
          )
        }
      >
        H2
      </ToolbarButton>
      <ToolbarButton
        label="제목 3"
        title="제목 3 (### )"
        active={active.h3}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) =>
            activeEditor.chain().focus().toggleHeading({ level: 3 }).run(),
          )
        }
      >
        H3
      </ToolbarButton>

      <ToolbarDivider />

      {/* 목록 */}
      <ToolbarButton
        label="글머리 기호 목록"
        title="글머리 기호 (- )"
        active={active.bulletList}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) => activeEditor.chain().focus().toggleBulletList().run())
        }
      >
        <BulletListIcon />
      </ToolbarButton>
      <ToolbarButton
        label="번호 목록"
        title="번호 목록 (1. )"
        active={active.orderedList}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) => activeEditor.chain().focus().toggleOrderedList().run())
        }
      >
        <OrderedListIcon />
      </ToolbarButton>
      <ToolbarButton
        label="체크리스트"
        title="체크리스트 ([] )"
        active={active.taskList}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) => activeEditor.chain().focus().toggleTaskList().run())
        }
      >
        <ChecklistIcon />
      </ToolbarButton>

      <ToolbarDivider />

      {/* 링크 · 코드 블록 */}
      <ToolbarButton
        label="링크"
        title="링크 (Ctrl+K)"
        active={active.link}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={onOpenLinkModal}
      >
        <LinkIcon />
      </ToolbarButton>
      <ToolbarButton
        label="코드 블록"
        title="코드 블록 (```)"
        active={active.codeBlock}
        disabled={disabled}
        onMouseDown={keepFocus}
        onClick={() =>
          runWithEditor((activeEditor) => activeEditor.chain().focus().toggleCodeBlock().run())
        }
      >
        <CodeBlockIcon />
      </ToolbarButton>

      <ToolbarDivider />

      {/* 2차 기능 비활성 버튼 (AGENTS.md 0-1, W-01) */}
      <button
        type="button"
        disabled
        aria-disabled="true"
        title="2차 예정 기능"
        className="inline-flex h-8 cursor-not-allowed items-center gap-1 rounded-input px-2.5 text-caption font-medium text-text-disabled"
      >
        <span>표 (2차)</span>
      </button>
      <button
        type="button"
        disabled
        aria-disabled="true"
        title="2차 예정 기능"
        className="inline-flex h-8 cursor-not-allowed items-center gap-1 rounded-input px-2.5 text-caption font-medium text-text-disabled"
      >
        <span>이미지 (2차)</span>
      </button>
    </div>
  )
}

function ToolbarDivider() {
  return <span aria-hidden="true" className="mx-1 h-5 w-px bg-divider" />
}

interface ToolbarButtonProps {
  label: string
  title: string
  active: boolean
  disabled: boolean
  onMouseDown: (event: MouseEvent<HTMLButtonElement>) => void
  onClick: () => void
  children: ReactNode
}

function ToolbarButton({
  label,
  title,
  active,
  disabled,
  onMouseDown,
  onClick,
  children,
}: ToolbarButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={active}
      title={title}
      disabled={disabled}
      onMouseDown={onMouseDown}
      onClick={onClick}
      className={[
        'inline-flex h-8 min-w-8 cursor-pointer items-center justify-center rounded-input px-2 text-small font-medium transition-colors',
        'disabled:cursor-not-allowed disabled:text-text-disabled',
        active ? 'bg-primary-soft font-bold text-primary' : 'text-text hover:bg-bg',
      ].join(' ')}
    >
      {children}
    </button>
  )
}

function BulletListIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="h-4 w-4"
    >
      <line x1="9" x2="20" y1="6" y2="6" />
      <line x1="9" x2="20" y1="12" y2="12" />
      <line x1="9" x2="20" y1="18" y2="18" />
      <circle cx="4.5" cy="6" r="1" fill="currentColor" />
      <circle cx="4.5" cy="12" r="1" fill="currentColor" />
      <circle cx="4.5" cy="18" r="1" fill="currentColor" />
    </svg>
  )
}

function OrderedListIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="h-4 w-4"
    >
      <line x1="10" x2="20" y1="6" y2="6" />
      <line x1="10" x2="20" y1="12" y2="12" />
      <line x1="10" x2="20" y1="18" y2="18" />
      <path d="M4 6h1v4" />
      <path d="M4 10h2" />
      <path d="M6 18H4c0-1 2-2 2-3s-1-1.5-2-1" />
    </svg>
  )
}

function ChecklistIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="h-4 w-4"
    >
      <rect width="6" height="6" x="3" y="4" rx="1" />
      <path d="m4.5 7 1 1 2-2" />
      <line x1="12" x2="21" y1="7" y2="7" />
      <rect width="6" height="6" x="3" y="14" rx="1" />
      <line x1="12" x2="21" y1="17" y2="17" />
    </svg>
  )
}

function LinkIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="h-4 w-4"
    >
      <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
      <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
    </svg>
  )
}

function CodeBlockIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="h-4 w-4"
    >
      <polyline points="16 18 22 12 16 6" />
      <polyline points="8 6 2 12 8 18" />
    </svg>
  )
}
