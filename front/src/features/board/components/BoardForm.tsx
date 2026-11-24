import type { FormEvent } from 'react'
import { Button } from '../../../components/ui/Button'
import { Chip } from '../../../components/ui/Chip'
import { Input } from '../../../components/ui/Input'
import { ContestSearch, SelectedContestBox } from '../../post/components/ContestPicker'
import { PostImagePicker } from '../../post/components/PostImagePicker'
import type { PostImageDraft } from '../../post/postImages'
import { BOARD_MESSAGES, CONTENT_MAX, TITLE_MAX, URL_MAX } from '../boardForm'
import type { BoardFormErrors, BoardFormValues } from '../boardForm'
import { BOARD_CATEGORIES, BOARD_CATEGORY_LABELS } from '../types'

interface BoardFormProps {
  values: BoardFormValues
  errors: BoardFormErrors
  onChange: (patch: Partial<BoardFormValues>) => void
  images: PostImageDraft[]
  onImagesChange: (images: PostImageDraft[]) => void
  // BRD-003 ③ 결과물 자랑에서 다른 말머리로 바꿔 지워질 링크·사진이 있으면 안내한다
  showcaseWillClear: boolean
  formError: string | null
  submitLabel: string
  submitting: boolean
  onSubmit: () => void
  onCancel: () => void
}

// BRD-002·BRD-003 게시글 작성·수정 폼. 모양은 모집글 작성(PST-002)과 맞춘다.
export function BoardForm({
  values,
  errors,
  onChange,
  images,
  onImagesChange,
  showcaseWillClear,
  formError,
  submitLabel,
  submitting,
  onSubmit,
  onCancel,
}: BoardFormProps) {
  const isShowcase = values.category === 'SHOWCASE'
  const contestRequired = values.category === 'CONTEST'

  const submit = (event: FormEvent) => {
    event.preventDefault()
    // ⑤ 요청 중에는 다시 보내지 않는다
    if (!submitting) onSubmit()
  }

  return (
    <form noValidate onSubmit={submit} className="mt-8 flex max-w-[1100px] flex-col gap-8">
      {/* ① 말머리 (필수) */}
      <div className="flex flex-col">
        <span id="board-category-label" className="mb-2 text-body font-bold text-text">
          말머리<span className="ml-1 font-normal text-text-sub">(필수)</span>
        </span>
        <div role="group" aria-labelledby="board-category-label" className="flex flex-wrap gap-2">
          {BOARD_CATEGORIES.map((category) => (
            <Chip
              key={category}
              selected={values.category === category}
              onClick={() => onChange({ category })}
            >
              {BOARD_CATEGORY_LABELS[category]}
            </Chip>
          ))}
        </div>
        {errors.category && <p className="mt-2 text-small text-danger">{errors.category}</p>}
        {showcaseWillClear && (
          <p role="status" className="mt-2 text-small font-bold text-danger">
            {BOARD_MESSAGES.showcaseCleared}
          </p>
        )}
      </div>

      {/* ① 공모전: 공모전 이야기는 필수, 나머지는 선택. 마감된 공모전도 고를 수 있다 */}
      <div className="flex flex-col">
        <span id="post-contest-label" className="mb-2 text-body font-bold text-text">
          공모전
          <span className="ml-1 font-normal text-text-sub">
            {contestRequired ? '(필수)' : '(선택)'}
          </span>
        </span>
        {values.contest ? (
          <SelectedContestBox
            contest={values.contest}
            onClear={() => onChange({ contest: null })}
          />
        ) : (
          <ContestSearch
            includeEnded
            error={errors.contest}
            onSelect={(contest) => onChange({ contest })}
          />
        )}
        {errors.contest && <p className="mt-2 text-small text-danger">{errors.contest}</p>}
      </div>

      <div className="relative">
        <Input
          id="board-title"
          label="제목"
          required
          value={values.title}
          maxLength={TITLE_MAX}
          placeholder="제목을 적어 주세요"
          errorText={errors.title}
          onChange={(event) => onChange({ title: event.target.value })}
          className="pr-20"
        />
        <span className="pointer-events-none absolute top-8 right-4 flex h-14 items-center text-small text-text-sub">
          {values.title.length} / {TITLE_MAX}
        </span>
      </div>

      <div className="flex flex-col">
        <label htmlFor="board-content" className="mb-2 text-body font-bold text-text">
          내용<span className="ml-1 font-normal text-text-sub">(필수)</span>
        </label>
        <div className="relative">
          <textarea
            id="board-content"
            value={values.content}
            maxLength={CONTENT_MAX}
            placeholder="나누고 싶은 이야기를 적어 주세요"
            aria-invalid={errors.content ? true : undefined}
            aria-describedby="board-content-count"
            onChange={(event) => onChange({ content: event.target.value })}
            className={[
              'block h-72 w-full resize-y rounded-input bg-surface px-4 pt-4 pb-10 text-body text-text placeholder:text-text-disabled focus:outline-none',
              errors.content
                ? 'border-2 border-danger'
                : 'border border-line-input focus:border-2 focus:border-primary',
            ].join(' ')}
          />
          <span
            id="board-content-count"
            className="pointer-events-none absolute right-4 bottom-3 text-small text-text-sub"
          >
            {values.content.length.toLocaleString()} / {CONTENT_MAX.toLocaleString()}
          </span>
        </div>
        {errors.content && <p className="mt-2 text-small text-danger">{errors.content}</p>}
      </div>

      {/* ② 결과물 자랑만: 결과물 링크(선택)와 사진(선택, BRD-006) */}
      {isShowcase && (
        <>
          <Input
            id="board-link-url"
            type="url"
            label="결과물 링크"
            value={values.linkUrl}
            maxLength={URL_MAX}
            placeholder="https:// (선택)"
            errorText={errors.linkUrl}
            onChange={(event) => onChange({ linkUrl: event.target.value })}
          />
          <PostImagePicker drafts={images} onChange={onImagesChange} label="결과물 사진" />
        </>
      )}

      {formError && (
        <p role="alert" className="text-body text-danger">
          {formError}
        </p>
      )}

      <div className="flex justify-end gap-3">
        <Button size="lg" onClick={onCancel}>
          취소
        </Button>
        <Button type="submit" variant="primary" size="lg" loading={submitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
