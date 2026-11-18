import type { FormEvent, ReactNode } from 'react'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { CONTENT_MAX, TARGET_COUNT_MAX, TARGET_COUNT_MIN, TITLE_MAX, URL_MAX } from '../postForm'
import type { PostFormErrors, PostFormValues } from '../postForm'

interface PostFormProps {
  // 작성은 공모전 고르기, 수정은 잠긴 공모전 칸 (PST-003 ②)
  contestField: ReactNode
  values: PostFormValues
  errors: PostFormErrors
  onChange: (patch: Partial<PostFormValues>) => void
  formError: string | null
  submitLabel: string
  submitting: boolean
  onSubmit: () => void
  onCancel: () => void
}

// PST-002·PST-003 모집글 작성·수정 폼 / Figma 02-1~02-5
export function PostForm({
  contestField,
  values,
  errors,
  onChange,
  formError,
  submitLabel,
  submitting,
  onSubmit,
  onCancel,
}: PostFormProps) {
  const submit = (event: FormEvent) => {
    event.preventDefault()
    // PST-002 ⑧ 요청 중에는 다시 보내지 않는다
    if (!submitting) onSubmit()
  }

  return (
    <form noValidate onSubmit={submit} className="mt-8 flex max-w-[1100px] flex-col gap-8">
      {contestField}

      <Counted length={values.title.length} max={TITLE_MAX}>
        <Input
          id="post-title"
          label="제목"
          required
          value={values.title}
          maxLength={TITLE_MAX}
          placeholder="팀원 모집 목적을 적어 주세요"
          errorText={errors.title}
          onChange={(event) => onChange({ title: event.target.value })}
          className="pr-20"
        />
      </Counted>

      <div className="grid grid-cols-[240px_minmax(0,1.4fr)_minmax(0,1fr)] gap-6">
        <TargetCountStepper
          value={values.targetCount}
          error={errors.targetCount}
          onChange={(targetCount) => onChange({ targetCount })}
        />
        <PeriodFields
          start={values.recruitStartDate}
          end={values.recruitEndDate}
          error={errors.period}
          onChange={onChange}
        />
        <Input
          id="post-related-url"
          type="url"
          label="관련 URL"
          value={values.relatedUrl}
          maxLength={URL_MAX}
          placeholder="https:// (선택)"
          errorText={errors.relatedUrl}
          onChange={(event) => onChange({ relatedUrl: event.target.value })}
        />
      </div>

      <div className="flex flex-col">
        <label htmlFor="post-content" className="mb-2 text-body font-bold text-text">
          모집 내용<span className="ml-1 font-normal text-text-sub">(필수)</span>
        </label>
        <div className="relative">
          <textarea
            id="post-content"
            value={values.content}
            maxLength={CONTENT_MAX}
            placeholder="필요한 역할, 경험, 작업 방식 등 모집 내용을 적어 주세요"
            aria-invalid={errors.content ? true : undefined}
            aria-describedby="post-content-count"
            onChange={(event) => onChange({ content: event.target.value })}
            className={[
              'block h-60 w-full resize-y rounded-input bg-surface px-4 pt-4 pb-10 text-body text-text placeholder:text-text-disabled focus:outline-none',
              errors.content
                ? 'border-2 border-danger'
                : 'border border-line-input focus:border-2 focus:border-primary',
            ].join(' ')}
          />
          <span
            id="post-content-count"
            className="pointer-events-none absolute right-4 bottom-3 text-small text-text-sub"
          >
            {values.content.length.toLocaleString()} / {CONTENT_MAX.toLocaleString()}
          </span>
        </div>
        {errors.content && <p className="mt-2 text-small text-danger">{errors.content}</p>}
      </div>

      {formError && (
        <p role="alert" className="text-body text-danger">
          {formError}
        </p>
      )}

      {/* PST-002 ⑥, PST-003 ③ 하단 오른쪽 [취소] [등록·수정 완료] */}
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

// 입력창 오른쪽 안에 「12 / 50」 글자 수를 겹쳐 둔다 (STYLE_GUIDE 5-2)
function Counted({ length, max, children }: { length: number; max: number; children: ReactNode }) {
  return (
    <div className="relative">
      {children}
      <span className="pointer-events-none absolute top-8 right-4 flex h-14 items-center text-small text-text-sub">
        {length} / {max}
      </span>
    </div>
  )
}

// PST-002 ① 목표 팀원 수: 작성자 포함 2~8명. 수정에서는 확정 인원과 비교한 안내가 붙는다 (PST-003 ④).
function TargetCountStepper({
  value,
  error,
  onChange,
}: {
  value: number
  error?: string
  onChange: (n: number) => void
}) {
  const stepClass =
    'flex size-10 cursor-pointer items-center justify-center rounded-full text-subheading text-text hover:bg-bg disabled:cursor-not-allowed disabled:text-text-disabled disabled:hover:bg-transparent'

  return (
    <div className="flex flex-col">
      <span id="post-target-label" className="mb-2 text-body font-bold text-text">
        목표 팀원 수<span className="ml-1 font-normal text-text-sub">(필수)</span>
      </span>
      <div
        role="group"
        aria-labelledby="post-target-label"
        aria-describedby="post-target-help"
        className={[
          'flex h-14 items-center justify-between rounded-input bg-surface px-2',
          error ? 'border-2 border-danger' : 'border border-line-input',
        ].join(' ')}
      >
        <button
          type="button"
          aria-label="한 명 줄이기"
          disabled={value <= TARGET_COUNT_MIN}
          onClick={() => onChange(value - 1)}
          className={stepClass}
        >
          −
        </button>
        <output aria-live="polite" className="text-body-lg font-bold text-text">
          {value}명
        </output>
        <button
          type="button"
          aria-label="한 명 늘리기"
          disabled={value >= TARGET_COUNT_MAX}
          onClick={() => onChange(value + 1)}
          className={stepClass}
        >
          +
        </button>
      </div>
      <p
        id="post-target-help"
        className={['mt-2 text-small', error ? 'text-danger' : 'text-text-sub'].join(' ')}
      >
        {error ?? `작성자 포함 ${TARGET_COUNT_MIN}~${TARGET_COUNT_MAX}명`}
      </p>
    </div>
  )
}

// PST-002 ①③ 모집 기간: 시작일 ~ 종료일
function PeriodFields({
  start,
  end,
  error,
  onChange,
}: {
  start: string
  end: string
  error?: string
  onChange: (
    patch: Pick<PostFormValues, 'recruitStartDate'> | Pick<PostFormValues, 'recruitEndDate'>,
  ) => void
}) {
  const dateClass = [
    'h-14 min-w-0 flex-1 rounded-input bg-surface px-4 text-body text-text focus:outline-none',
    error
      ? 'border-2 border-danger'
      : 'border border-line-input focus:border-2 focus:border-primary',
  ].join(' ')

  return (
    <fieldset className="flex min-w-0 flex-col">
      <legend className="mb-2 text-body font-bold text-text">
        모집 기간<span className="ml-1 font-normal text-text-sub">(필수)</span>
      </legend>
      <div className="flex items-center gap-2">
        <input
          type="date"
          aria-label="모집 시작일"
          value={start}
          max={end || undefined}
          aria-invalid={error ? true : undefined}
          onChange={(event) => onChange({ recruitStartDate: event.target.value })}
          className={dateClass}
        />
        <span aria-hidden="true" className="text-body text-text-sub">
          ~
        </span>
        <input
          type="date"
          aria-label="모집 종료일"
          value={end}
          min={start || undefined}
          aria-invalid={error ? true : undefined}
          onChange={(event) => onChange({ recruitEndDate: event.target.value })}
          className={dateClass}
        />
      </div>
      {error && <p className="mt-2 text-small text-danger">{error}</p>}
    </fieldset>
  )
}
