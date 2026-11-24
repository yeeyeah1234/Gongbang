import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent } from 'react'
import { Badge } from '../../../components/ui/Badge'
import {
  POST_IMAGE_ACCEPT,
  POST_IMAGE_MAX,
  POST_IMAGE_MESSAGES,
  checkImageFile,
  draftUrl,
  newDraft,
  releaseDraft,
} from '../postImages'
import type { PostImageDraft } from '../postImages'

interface PostImagePickerProps {
  drafts: PostImageDraft[]
  onChange: (drafts: PostImageDraft[]) => void
  // 칸 이름 (모집글은 「공모전 사진」, 커뮤니티 결과물 자랑은 「결과물 사진」)
  label?: string
}

const ICON_BUTTON =
  'flex size-9 cursor-pointer items-center justify-center rounded-full border border-line bg-surface text-text hover:bg-bg focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:text-text-disabled disabled:hover:bg-surface'

// PST-007 외부 공모전 사진: 최대 3장, 첫 장이 대표. 추가·삭제·순서 바꾸기는 저장(등록·수정 완료) 때 서버에 반영한다.
export function PostImagePicker({ drafts, onChange, label = '공모전 사진' }: PostImagePickerProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)

  // 화면을 떠날 때 아직 남은 미리보기 주소를 돌려준다
  const latest = useRef(drafts)
  useEffect(() => {
    latest.current = drafts
  }, [drafts])
  useEffect(() => () => latest.current.forEach(releaseDraft), [])

  const addFiles = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    // 같은 파일을 다시 골라도 change가 일어나게 비운다
    event.target.value = ''
    const added: PostImageDraft[] = []
    let message: string | null = null
    for (const file of files) {
      if (drafts.length + added.length >= POST_IMAGE_MAX) {
        message = POST_IMAGE_MESSAGES.limit
        break
      }
      // ② 맞지 않는 사진만 빼고 나머지는 넣는다
      const problem = checkImageFile(file)
      if (problem) {
        message = problem
        continue
      }
      added.push(newDraft(file))
    }
    setError(message)
    if (added.length > 0) onChange([...drafts, ...added])
  }

  const remove = (index: number) => {
    releaseDraft(drafts[index])
    setError(null)
    onChange(drafts.filter((_, i) => i !== index))
  }

  const move = (index: number, offset: -1 | 1) => {
    const next = [...drafts]
    ;[next[index], next[index + offset]] = [next[index + offset], next[index]]
    onChange(next)
  }

  const full = drafts.length >= POST_IMAGE_MAX

  return (
    <div className="flex flex-col">
      <span id="post-images-label" className="mb-2 text-body font-bold text-text">
        {label}
        <span className="ml-1 font-normal text-text-sub">(선택)</span>
      </span>
      <ul aria-labelledby="post-images-label" className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {drafts.map((draft, index) => (
          <li
            key={draft.key}
            className="relative aspect-4/3 overflow-hidden rounded-card border border-line bg-surface-sub"
          >
            <img
              src={draftUrl(draft)}
              alt={`${label} ${index + 1}`}
              className="size-full object-cover"
            />
            {index === 0 && (
              <Badge variant="primary" className="absolute top-3 left-3">
                대표
              </Badge>
            )}
            <div className="absolute right-3 bottom-3 flex gap-2">
              <button
                type="button"
                aria-label={`${index + 1}번째 사진을 앞으로`}
                disabled={index === 0}
                onClick={() => move(index, -1)}
                className={ICON_BUTTON}
              >
                <ArrowIcon direction="left" />
              </button>
              <button
                type="button"
                aria-label={`${index + 1}번째 사진을 뒤로`}
                disabled={index === drafts.length - 1}
                onClick={() => move(index, 1)}
                className={ICON_BUTTON}
              >
                <ArrowIcon direction="right" />
              </button>
              <button
                type="button"
                aria-label={`${index + 1}번째 사진 삭제`}
                onClick={() => remove(index)}
                className={`${ICON_BUTTON} text-danger`}
              >
                <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none">
                  <path d="m5 5 10 10M15 5 5 15" stroke="currentColor" strokeWidth="1.8" />
                </svg>
              </button>
            </div>
          </li>
        ))}
        {!full && (
          <li className="aspect-4/3">
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              className="flex size-full cursor-pointer flex-col items-center justify-center gap-2 rounded-card border border-dashed border-line-dashed bg-bg px-4 text-center hover:bg-surface-sub focus-visible:outline-2 focus-visible:outline-primary"
            >
              <svg
                aria-hidden="true"
                viewBox="0 0 20 20"
                className="size-6 text-text-sub"
                fill="none"
              >
                <path d="M10 4v12M4 10h12" stroke="currentColor" strokeWidth="1.8" />
              </svg>
              <span className="text-body font-bold text-text">사진 추가</span>
              <span className="text-small text-text-sub">
                {drafts.length} / {POST_IMAGE_MAX}
              </span>
            </button>
          </li>
        )}
      </ul>
      <input
        ref={inputRef}
        type="file"
        accept={POST_IMAGE_ACCEPT}
        multiple
        onChange={addFiles}
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
      />
      <p
        role={error ? 'alert' : undefined}
        className={['mt-2 text-small', error ? 'text-danger' : 'text-text-sub'].join(' ')}
      >
        {error ?? 'JPG·PNG·WebP·GIF·AVIF, 장당 10MB 이하, 3장까지. 첫 번째 사진이 대표 이미지예요'}
      </p>
    </div>
  )
}

function ArrowIcon({ direction }: { direction: 'left' | 'right' }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none">
      <path
        d={direction === 'left' ? 'M12 5 7 10l5 5' : 'm8 5 5 5-5 5'}
        stroke="currentColor"
        strokeWidth="1.8"
      />
    </svg>
  )
}
