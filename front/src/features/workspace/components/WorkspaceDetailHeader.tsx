import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { ArrowLeftIcon } from '../../../components/icons/Icons'
import { strip } from '../notice/validation'
import { PencilIcon } from './WorkspaceIcons'

const DEFAULT_TITLE_MAX = 50

function clampChars(value: string, max: number) {
  const chars = Array.from(value)
  return chars.length > max ? chars.slice(0, max).join('') : value
}

interface InlineHeaderRenameInputProps {
  initialTitle: string
  inputLabel: string
  maxTitleLength: number
  renaming: boolean
  onSubmit: (title: string) => void
  onCancel: () => void
}

function InlineHeaderRenameInput({
  initialTitle,
  inputLabel,
  maxTitleLength,
  renaming,
  onSubmit,
  onCancel,
}: InlineHeaderRenameInputProps) {
  const [draft, setDraft] = useState(() => clampChars(initialTitle, maxTitleLength))
  const [invalid, setInvalid] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  // Esc·Enter 직후 이어지는 blur에서 중복 처리하지 않게 막는다
  const skipBlurRef = useRef(false)
  const composingRef = useRef(false)

  useEffect(() => {
    const el = inputRef.current
    if (!el) return
    el.focus()
    el.select()
  }, [])

  const charCount = Math.min(Array.from(draft).length, maxTitleLength)

  const commit = (cancelWhenBlank: boolean) => {
    if (renaming) return
    const cleaned = strip(clampChars(draft, maxTitleLength))
    if (cleaned.length === 0) {
      if (cancelWhenBlank) {
        onCancel()
      } else {
        setInvalid(true)
      }
      return
    }
    onSubmit(cleaned)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing || composingRef.current) return
    if (event.key === 'Enter') {
      event.preventDefault()
      const cleaned = strip(clampChars(draft, maxTitleLength))
      if (cleaned.length === 0) {
        setInvalid(true)
        return
      }
      skipBlurRef.current = true
      commit(false)
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
    commit(true)
  }

  return (
    <div className="flex min-w-0 flex-1 items-center gap-2 pr-1">
      <input
        ref={inputRef}
        type="text"
        value={draft}
        disabled={renaming}
        aria-label={inputLabel}
        aria-invalid={invalid}
        onCompositionStart={() => {
          composingRef.current = true
        }}
        onCompositionEnd={(event) => {
          composingRef.current = false
          setDraft(clampChars(event.currentTarget.value, maxTitleLength))
          if (invalid) setInvalid(false)
        }}
        onChange={(event) => {
          const next = event.target.value
          setDraft(composingRef.current ? next : clampChars(next, maxTitleLength))
          if (invalid) setInvalid(false)
        }}
        onKeyDown={handleKeyDown}
        onBlur={handleBlur}
        className={[
          'h-9 min-w-0 flex-1 rounded-input border-2 bg-surface px-2.5 text-body font-bold text-text outline-none disabled:border-line disabled:bg-surface-sub disabled:text-text-disabled',
          invalid ? 'border-danger' : 'border-primary',
        ].join(' ')}
      />
      <span className="shrink-0 text-caption text-text-sub">
        {charCount}/{maxTitleLength}
      </span>
    </div>
  )
}

interface WorkspaceDetailHeaderProps {
  title: string | undefined
  fallbackTitle: string
  backAriaLabel: string
  renameInputLabel: string
  canRename: boolean
  autoEdit: boolean
  renaming: boolean
  maxTitleLength?: number
  onBack: () => void
  onAutoEditConsumed: () => void
  onSubmitRename: (title: string, onDone: () => void) => void
}

// DOC-02·DOC-03·WB-01 1024px 미만 본문 상단바: 뒤로가기 + 제목 + 연필 아이콘 인라인 이름 바꾸기
export function WorkspaceDetailHeader({
  title,
  fallbackTitle,
  backAriaLabel,
  renameInputLabel,
  canRename,
  autoEdit,
  renaming,
  maxTitleLength = DEFAULT_TITLE_MAX,
  onBack,
  onAutoEditConsumed,
  onSubmitRename,
}: WorkspaceDetailHeaderProps) {
  const editable = canRename && title !== undefined
  const [editing, setEditing] = useState(() => editable && autoEdit)
  const consumedRef = useRef(false)

  const consumeAutoEdit = () => {
    if (!autoEdit || consumedRef.current) return
    consumedRef.current = true
    onAutoEditConsumed()
  }

  // 뒤로가기·화면 넓어짐 등으로 언마운트될 때 생성 직후 자동 편집 플래그를 정리한다
  useEffect(() => {
    if (!autoEdit) return
    return () => {
      if (!consumedRef.current) {
        consumedRef.current = true
        onAutoEditConsumed()
      }
    }
  }, [autoEdit, onAutoEditConsumed])

  const handleCancel = () => {
    consumeAutoEdit()
    setEditing(false)
  }

  const handleSubmit = (nextTitle: string) => {
    consumeAutoEdit()
    onSubmitRename(nextTitle, () => setEditing(false))
  }

  return (
    <div className="flex shrink-0 items-center gap-2 border-b border-divider px-2 py-2 lg:hidden">
      <button
        type="button"
        onClick={() => {
          consumeAutoEdit()
          onBack()
        }}
        aria-label={backAriaLabel}
        className="flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full text-text transition-colors hover:bg-bg"
      >
        <ArrowLeftIcon className="h-5 w-5" />
      </button>

      {editing && editable ? (
        <InlineHeaderRenameInput
          initialTitle={title}
          inputLabel={renameInputLabel}
          maxTitleLength={maxTitleLength}
          renaming={renaming}
          onSubmit={handleSubmit}
          onCancel={handleCancel}
        />
      ) : (
        <div className="flex min-w-0 flex-1 items-center gap-1 pr-1">
          <h2 className="min-w-0 truncate text-body font-bold text-text" title={title}>
            {title ?? fallbackTitle}
          </h2>
          {editable && (
            <button
              type="button"
              onClick={() => setEditing(true)}
              aria-label="이름 바꾸기"
              title="이름 바꾸기"
              className="flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-full text-text-sub transition-colors hover:bg-bg hover:text-text"
            >
              <PencilIcon className="h-4 w-4" />
            </button>
          )}
        </div>
      )}
    </div>
  )
}
