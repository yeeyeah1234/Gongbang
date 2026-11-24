import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { MoreVerticalIcon } from '../components/WorkspaceIcons'
import { strip } from '../notice/validation'
import { formatRelativeTime } from './relativeTime'
import type { WorkspaceSidebarItem, WorkspaceSidebarLabels } from './types'

const DEFAULT_TITLE_MAX = 50

function clampChars(value: string, max: number) {
  const chars = Array.from(value)
  return chars.length > max ? chars.slice(0, max).join('') : value
}

interface InlineRenameRowProps {
  initialTitle: string
  defaultTitle: string
  inputLabel: string
  maxTitleLength: number
  renaming: boolean
  onSubmit: (title: string) => void
  onCancel: () => void
}

function InlineRenameRow({
  initialTitle,
  defaultTitle,
  inputLabel,
  maxTitleLength,
  renaming,
  onSubmit,
  onCancel,
}: InlineRenameRowProps) {
  const [draft, setDraft] = useState(() => clampChars(initialTitle, maxTitleLength))
  const inputRef = useRef<HTMLInputElement>(null)
  // Esc·Enter 직후 이어지는 blur에서 중복 저장하지 않게 막는다
  const skipBlurRef = useRef(false)
  const composingRef = useRef(false)

  useEffect(() => {
    const el = inputRef.current
    if (!el) return
    el.focus()
    el.select()
  }, [])

  const charCount = Math.min(Array.from(draft).length, maxTitleLength)

  const commit = () => {
    if (renaming) return
    const cleaned = strip(clampChars(draft, maxTitleLength))
    onSubmit(cleaned.length > 0 ? cleaned : defaultTitle)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing || composingRef.current) return
    if (event.key === 'Enter') {
      event.preventDefault()
      skipBlurRef.current = true
      commit()
    } else if (event.key === 'Escape') {
      event.preventDefault()
      skipBlurRef.current = true
      onCancel()
    }
  }

  const handleBlur = () => {
    if (skipBlurRef.current) {
      skipBlurRef.current = false
      return
    }
    commit()
  }

  return (
    <div className="rounded-popover bg-primary-soft px-3 py-2.5">
      <input
        ref={inputRef}
        type="text"
        value={draft}
        disabled={renaming}
        aria-label={inputLabel}
        onCompositionStart={() => {
          composingRef.current = true
        }}
        onCompositionEnd={(event) => {
          composingRef.current = false
          setDraft(clampChars(event.currentTarget.value, maxTitleLength))
        }}
        onChange={(event) => {
          const next = event.target.value
          setDraft(composingRef.current ? next : clampChars(next, maxTitleLength))
        }}
        onKeyDown={handleKeyDown}
        onBlur={handleBlur}
        className="h-9 w-full rounded-input border-2 border-primary bg-surface px-2.5 text-small text-text outline-none disabled:border-line disabled:bg-surface-sub disabled:text-text-disabled"
      />
      <div className="mt-1 flex justify-end text-caption text-text-sub">
        {charCount}/{maxTitleLength}
      </div>
    </div>
  )
}

interface WorkspaceItemRowProps {
  item: WorkspaceSidebarItem
  to: string
  selected: boolean
  editing: boolean
  renaming: boolean
  menuOpen: boolean
  now: Date
  labels: WorkspaceSidebarLabels
  maxTitleLength?: number
  onToggleMenu: (id: number) => void
  onCloseMenu: () => void
  onStartRename: (id: number) => void
  onSubmitRename: (id: number, title: string) => void
  onCancelRename: () => void
  onRequestDelete: (item: WorkspaceSidebarItem) => void
}

// DOC-01·DOC-03·WB-01 공통 사이드바 한 줄: 선택 강조, 호버/선택 ⋮ 메뉴, 인라인 이름 바꾸기
export function WorkspaceItemRow({
  item,
  to,
  selected,
  editing,
  renaming,
  menuOpen,
  now,
  labels,
  maxTitleLength = DEFAULT_TITLE_MAX,
  onToggleMenu,
  onCloseMenu,
  onStartRename,
  onSubmitRename,
  onCancelRename,
  onRequestDelete,
}: WorkspaceItemRowProps) {
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const [openUp, setOpenUp] = useState(false)

  useEffect(() => {
    if (!menuOpen) return
    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as Node
      if (menuRef.current?.contains(target) || triggerRef.current?.contains(target)) return
      onCloseMenu()
    }
    const handleKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseMenu()
        triggerRef.current?.focus()
      }
    }
    document.addEventListener('mousedown', handlePointerDown)
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [menuOpen, onCloseMenu])

  if (editing) {
    return (
      <li>
        <InlineRenameRow
          key={item.id}
          initialTitle={item.title}
          defaultTitle={labels.defaultTitle}
          inputLabel={labels.renameInputLabel}
          maxTitleLength={maxTitleLength}
          renaming={renaming}
          onSubmit={(title) => onSubmitRename(item.id, title)}
          onCancel={onCancelRename}
        />
      </li>
    )
  }

  const handleToggleMenu = () => {
    if (!menuOpen) {
      const trigger = triggerRef.current
      if (trigger) {
        const rect = trigger.getBoundingClientRect()
        const scrollParent = trigger.closest('.overflow-y-auto')
        const boundaryBottom = scrollParent
          ? Math.min(scrollParent.getBoundingClientRect().bottom, window.innerHeight)
          : window.innerHeight
        const estimatedHeight = item.canDelete ? 96 : 52
        setOpenUp(rect.bottom + estimatedHeight + 8 > boundaryBottom)
      }
    }
    onToggleMenu(item.id)
  }

  const showTrigger = selected || menuOpen

  return (
    <li className="group relative">
      <div
        className={[
          'relative flex items-center rounded-popover transition-colors',
          selected ? 'bg-primary-soft' : 'hover:bg-bg',
        ].join(' ')}
      >
        <Link
          to={to}
          aria-current={selected ? 'page' : undefined}
          className="flex min-w-0 flex-1 flex-col gap-0.5 rounded-popover py-2.5 pr-10 pl-3 focus-visible:outline-2 focus-visible:outline-primary"
        >
          <span
            title={item.title}
            className={[
              'truncate text-body text-text',
              selected ? 'font-bold' : 'font-normal',
            ].join(' ')}
          >
            {item.title}
          </span>
          <span className="truncate text-caption text-text-sub">
            수정: {item.updatedByNickname} · {formatRelativeTime(item.updatedAt, now)}
          </span>
        </Link>

        <button
          ref={triggerRef}
          type="button"
          aria-label={labels.menuButtonLabel}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={handleToggleMenu}
          className={[
            'absolute top-1/2 right-1.5 flex h-7 w-7 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full text-text-sub transition-opacity hover:bg-surface hover:text-text focus-visible:pointer-events-auto focus-visible:opacity-100 focus-visible:outline-2 focus-visible:outline-primary',
            showTrigger
              ? 'pointer-events-auto opacity-100'
              : 'pointer-events-none opacity-0 group-hover:pointer-events-auto group-hover:opacity-100',
          ].join(' ')}
        >
          <MoreVerticalIcon className="h-4 w-4" />
        </button>
      </div>

      {menuOpen && (
        <div
          ref={menuRef}
          role="menu"
          className={[
            'absolute right-1.5 z-40 w-36 rounded-popover border border-line bg-surface py-1.5 shadow-pop',
            openUp ? 'bottom-full mb-1' : 'top-full mt-1',
          ].join(' ')}
        >
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              onCloseMenu()
              onStartRename(item.id)
            }}
            className="flex w-full cursor-pointer items-center px-3.5 py-2 text-left text-small text-text transition-colors hover:bg-bg"
          >
            {labels.renameAction}
          </button>
          {item.canDelete && (
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                onCloseMenu()
                onRequestDelete(item)
              }}
              className="flex w-full cursor-pointer items-center px-3.5 py-2 text-left text-small text-danger transition-colors hover:bg-danger-soft"
            >
              {labels.deleteAction}
            </button>
          )}
        </div>
      )}
    </li>
  )
}
