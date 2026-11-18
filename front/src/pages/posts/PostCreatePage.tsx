import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { getApiError } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { ContestPicker } from '../../features/post/components/ContestPicker'
import { useCreatePost } from '../../features/post/hooks'
import {
  CONTENT_MAX,
  EMPTY_POST_FORM,
  MESSAGES,
  TARGET_COUNT_MAX,
  TARGET_COUNT_MIN,
  TITLE_MAX,
  URL_MAX,
  toCreateRequest,
  validatePostForm,
} from '../../features/post/postForm'
import type { PostFormErrors, PostFormValues } from '../../features/post/postForm'

// 서버가 막은 이유를 해당 칸 아래에 보여 준다 (PST-002 ⑥)
function serverErrorToField(code: string | null, message: string | null): PostFormErrors | null {
  switch (code) {
    case 'CONTEST_REQUIRED':
      return { contest: MESSAGES.contestRequired }
    case 'CONTEST_ENDED':
      return { contest: MESSAGES.contestEnded, externalDeadline: MESSAGES.contestEnded }
    case 'CONTEST_NOT_FOUND':
      return { contest: message ?? MESSAGES.contestRequired }
    case 'INVALID_PERIOD':
      return { period: MESSAGES.invalidPeriod }
    default:
      return null
  }
}

// PST-002 모집글 작성 / Figma 02-1·02-2·02-3. 로그인 회원만 들어온다 (RequireLogin).
export default function PostCreatePage() {
  const navigate = useNavigate()
  const createPost = useCreatePost()
  const [values, setValues] = useState<PostFormValues>(EMPTY_POST_FORM)
  const [submitted, setSubmitted] = useState(false)
  const [serverErrors, setServerErrors] = useState<PostFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  // 한 번 「등록」을 누른 뒤부터는 고칠 때마다 다시 검사해 안내를 바로 지운다
  const errors: PostFormErrors = submitted ? { ...validatePostForm(values), ...serverErrors } : {}

  const update = (patch: Partial<PostFormValues>) => {
    setValues((prev) => ({ ...prev, ...patch }))
    setServerErrors({})
    setFormError(null)
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    // ⑧ 요청 중에는 다시 보내지 않는다
    if (createPost.isPending) return
    setSubmitted(true)
    setServerErrors({})
    setFormError(null)
    if (Object.keys(validatePostForm(values)).length > 0) return

    createPost.mutate(toCreateRequest(values), {
      // ⑦ 등록하면 상세 화면으로
      onSuccess: ({ postId }) => navigate(`/posts/${postId}`, { replace: true }),
      onError: (error) => {
        const { code, message } = getApiError(error)
        const fieldErrors = serverErrorToField(code, message)
        if (fieldErrors) setServerErrors(fieldErrors)
        else setFormError(message ?? '잠시 후 다시 시도해 주세요')
      },
    })
  }

  return (
    <main className="mx-auto max-w-[1680px] px-6 py-12 lg:px-12 xl:px-[120px]">
      <title>모집글 작성 | 공방</title>
      <h1 className="text-title font-bold text-text">모집글 작성</h1>

      <form noValidate onSubmit={submit} className="mt-8 flex max-w-[1100px] flex-col gap-8">
        <ContestPicker values={values} errors={errors} onChange={update} />

        <Counted length={values.title.length} max={TITLE_MAX}>
          <Input
            id="post-title"
            label="제목"
            required
            value={values.title}
            maxLength={TITLE_MAX}
            placeholder="팀원 모집 목적을 적어 주세요"
            errorText={errors.title}
            onChange={(event) => update({ title: event.target.value })}
            className="pr-20"
          />
        </Counted>

        <div className="grid grid-cols-[240px_minmax(0,1.4fr)_minmax(0,1fr)] gap-6">
          <TargetCountStepper
            value={values.targetCount}
            onChange={(targetCount) => update({ targetCount })}
          />
          <PeriodFields
            start={values.recruitStartDate}
            end={values.recruitEndDate}
            error={errors.period}
            onChange={update}
          />
          <Input
            id="post-related-url"
            type="url"
            label="관련 URL"
            value={values.relatedUrl}
            maxLength={URL_MAX}
            placeholder="https:// (선택)"
            errorText={errors.relatedUrl}
            onChange={(event) => update({ relatedUrl: event.target.value })}
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
              onChange={(event) => update({ content: event.target.value })}
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

        {/* ⑥ 하단 오른쪽 [취소] [등록] */}
        <div className="flex justify-end gap-3">
          <Button size="lg" onClick={() => navigate('/posts')}>
            취소
          </Button>
          <Button type="submit" variant="primary" size="lg" loading={createPost.isPending}>
            등록
          </Button>
        </div>
      </form>
    </main>
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

// ① 목표 팀원 수: 작성자 포함 2~8명
function TargetCountStepper({ value, onChange }: { value: number; onChange: (n: number) => void }) {
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
        className="flex h-14 items-center justify-between rounded-input border border-line-input bg-surface px-2"
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
      <p className="mt-2 text-small text-text-sub">
        작성자 포함 {TARGET_COUNT_MIN}~{TARGET_COUNT_MAX}명
      </p>
    </div>
  )
}

// ①③ 모집 기간: 시작일 ~ 종료일
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
