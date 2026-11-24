import { useEffect, useState } from 'react'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { useCategories } from '../../contest/hooks'
import { useContestSearch } from '../hooks'
import { EXTERNAL_TITLE_MAX, URL_MAX, todayString } from '../postForm'
import type { PostFormErrors, PostFormValues, SelectedContest } from '../postForm'

type ContestValues = Pick<
  PostFormValues,
  | 'contestMode'
  | 'contest'
  | 'externalTitle'
  | 'externalCategoryCode'
  | 'externalDeadline'
  | 'externalUrl'
>

interface ContestPickerProps {
  values: ContestValues
  errors: PostFormErrors
  onChange: (patch: Partial<ContestValues>) => void
}

// 글자를 칠 때마다 부르지 않고 잠깐 멈췄을 때 검색한다
const SEARCH_DELAY_MS = 300

function formatDate(date: string) {
  return date.replaceAll('-', '.')
}

// PST-002 ② 공모전: 진행 중인 서비스 공모전 검색 또는 외부 공모전 직접 입력 (Figma 02-1·02-2)
export function ContestPicker({ values, errors, onChange }: ContestPickerProps) {
  const isExternal = values.contestMode === 'external'

  return (
    <div className="flex flex-col">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
        <span id="post-contest-label" className="text-body font-bold text-text">
          공모전<span className="ml-1 font-normal text-text-sub">(필수)</span>
        </span>
        <button
          type="button"
          aria-pressed={isExternal}
          onClick={() => onChange({ contestMode: isExternal ? 'service' : 'external' })}
          className={[
            'rounded-pill inline-flex cursor-pointer items-center gap-1 px-3 py-1 text-small hover:bg-bg',
            isExternal ? 'font-bold text-primary' : 'text-text-sub',
          ].join(' ')}
        >
          {isExternal && (
            <svg aria-hidden="true" viewBox="0 0 16 16" className="size-4" fill="none">
              <path d="M3 8.5 6.5 12 13 4.5" stroke="currentColor" strokeWidth="2" />
            </svg>
          )}
          목록에 없는 공모전 직접 입력
          {!isExternal && <span aria-hidden="true">›</span>}
        </button>
      </div>

      {isExternal ? (
        <ExternalContestFields values={values} errors={errors} onChange={onChange} />
      ) : values.contest ? (
        <SelectedContestBox contest={values.contest} onClear={() => onChange({ contest: null })} />
      ) : (
        <ContestSearch error={errors.contest} onSelect={(contest) => onChange({ contest })} />
      )}

      {!isExternal && errors.contest && (
        <p className="mt-2 text-small text-danger">{errors.contest}</p>
      )}
    </div>
  )
}

export function SelectedContestBox({
  contest,
  onClear,
}: {
  contest: SelectedContest
  onClear: () => void
}) {
  return (
    <div className="flex min-h-14 items-center gap-3 rounded-input border border-line-input bg-surface px-4 py-2">
      <Badge className="shrink-0">{contest.categoryName}</Badge>
      <span className="min-w-0 flex-1 truncate text-body font-bold text-text">{contest.title}</span>
      {/* 좁은 화면에서는 제목이 보이도록 마감일을 숨긴다 */}
      <span className="hidden shrink-0 text-small text-text-sub sm:inline">
        마감 {formatDate(contest.deadline)}
      </span>
      <Button variant="text" size="sm" onClick={onClear}>
        다시 고르기
      </Button>
    </div>
  )
}

// includeEnded: 마감된 공모전도 찾는다 (커뮤니티 게시판 BRD-002 ①, 모집글은 진행 중만)
export function ContestSearch({
  error,
  onSelect,
  includeEnded = false,
}: {
  error?: string
  onSelect: (contest: SelectedContest) => void
  includeEnded?: boolean
}) {
  const [text, setText] = useState('')
  const [keyword, setKeyword] = useState('')
  const [open, setOpen] = useState(false)
  const { data, isFetching, isError, isPlaceholderData } = useContestSearch(
    keyword,
    includeEnded ? 'all' : 'active',
  )

  useEffect(() => {
    const timer = window.setTimeout(() => setKeyword(text.trim()), SEARCH_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [text])

  const results = data?.items ?? []
  // 입력을 멈추기 전이거나 앞 검색어의 결과를 보여 주는 중이면 「없어요」 대신 「찾는 중」
  const searching = isFetching || isPlaceholderData || text.trim() !== keyword
  const showList = open && keyword.length > 0

  return (
    <div
      className="relative"
      // 목록 안 항목으로 포커스가 옮겨 갈 때는 닫지 않는다
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false)
      }}
    >
      <svg
        aria-hidden="true"
        viewBox="0 0 20 20"
        className="pointer-events-none absolute top-1/2 left-4 size-5 -translate-y-1/2 text-text-sub"
        fill="none"
      >
        <circle cx="9" cy="9" r="6" stroke="currentColor" strokeWidth="1.5" />
        <path d="m13.5 13.5 4 4" stroke="currentColor" strokeWidth="1.5" />
      </svg>
      <input
        type="search"
        role="combobox"
        aria-labelledby="post-contest-label"
        aria-expanded={showList}
        aria-controls="post-contest-results"
        aria-invalid={error ? true : undefined}
        value={text}
        placeholder={includeEnded ? '공모전을 제목으로 검색' : '진행 중인 공모전을 제목으로 검색'}
        onChange={(event) => {
          setText(event.target.value)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(event) => {
          if (event.key === 'Escape') setOpen(false)
        }}
        className={[
          'h-14 w-full rounded-input bg-surface pr-4 pl-12 text-body text-text placeholder:text-text-disabled focus:outline-none',
          error
            ? 'border-2 border-danger'
            : 'border border-line-input focus:border-2 focus:border-primary',
        ].join(' ')}
      />
      {showList && (
        <ul
          id="post-contest-results"
          role="listbox"
          aria-busy={searching}
          // Safari는 버튼을 눌러도 버튼에 포커스를 주지 않아 입력칸 blur의 relatedTarget이 null이 된다.
          // 그러면 click 전에 목록이 닫혀 결과를 고를 수 없으므로, 목록을 누를 때는 포커스를 입력칸에 그대로 둔다
          // (스크롤바를 눌러도 닫히지 않는다). Tab으로 항목에 옮겨 가는 것은 그대로 된다.
          onMouseDown={(event) => event.preventDefault()}
          className="absolute top-full right-0 left-0 z-10 mt-2 max-h-[360px] overflow-y-auto rounded-popover border border-line bg-surface py-2 shadow-pop"
        >
          {results.map((contest) => (
            <li key={contest.contestId} role="option" aria-selected={false}>
              <button
                type="button"
                onClick={() => {
                  onSelect({
                    contestId: contest.contestId,
                    title: contest.title,
                    categoryName: contest.categoryName,
                    deadline: contest.deadline,
                  })
                  setOpen(false)
                }}
                className="flex h-14 w-full cursor-pointer items-center gap-3 px-4 text-left hover:bg-bg focus-visible:bg-bg focus-visible:outline-none"
              >
                <Badge className="shrink-0">{contest.categoryName}</Badge>
                <span className="min-w-0 flex-1 truncate text-body text-text">{contest.title}</span>
                <span className="shrink-0 text-small text-text-sub">
                  마감 {formatDate(contest.deadline)}
                </span>
              </button>
            </li>
          ))}
          {results.length === 0 && (
            <li className="flex h-14 items-center px-4 text-body text-text-sub">
              {isError
                ? '공모전을 불러오지 못했어요. 잠시 후 다시 시도해 주세요'
                : searching
                  ? '찾는 중이에요'
                  : includeEnded
                    ? '맞는 제목의 공모전이 없어요'
                    : '진행 중인 공모전 중에 맞는 제목이 없어요. 「목록에 없는 공모전 직접 입력」을 눌러 주세요'}
            </li>
          )}
        </ul>
      )}
    </div>
  )
}

function ExternalContestFields({ values, errors, onChange }: ContestPickerProps) {
  const { data: categories } = useCategories()

  return (
    <fieldset className="min-w-0 rounded-card border border-line-strong p-4 sm:p-6">
      <legend className="px-2 text-body font-bold text-text">외부 공모전 (직접 입력)</legend>
      {/* 넓은 화면은 한 줄 네 칸, 중간 화면은 두 칸씩, 좁은 화면은 한 칸씩 */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)]">
        <Input
          id="post-external-title"
          label="공모전명"
          required
          value={values.externalTitle}
          maxLength={EXTERNAL_TITLE_MAX}
          placeholder="공모전 이름을 적어 주세요"
          errorText={errors.externalTitle}
          onChange={(event) => onChange({ externalTitle: event.target.value })}
        />
        <div className="flex flex-col">
          <label htmlFor="post-external-category" className="mb-2 text-body font-bold text-text">
            분야<span className="ml-1 font-normal text-text-sub">(필수)</span>
          </label>
          <select
            id="post-external-category"
            value={values.externalCategoryCode}
            aria-invalid={errors.externalCategoryCode ? true : undefined}
            onChange={(event) => onChange({ externalCategoryCode: event.target.value })}
            className={[
              'h-14 rounded-input bg-surface px-4 text-body text-text focus:outline-none',
              errors.externalCategoryCode
                ? 'border-2 border-danger'
                : 'border border-line-input focus:border-2 focus:border-primary',
            ].join(' ')}
          >
            <option value="">분야 선택</option>
            {categories?.map((category) => (
              <option key={category.categoryCode} value={category.categoryCode}>
                {category.name}
              </option>
            ))}
          </select>
          {errors.externalCategoryCode && (
            <p className="mt-2 text-small text-danger">{errors.externalCategoryCode}</p>
          )}
        </div>
        <Input
          id="post-external-deadline"
          type="date"
          label="접수 마감일"
          required
          min={todayString()}
          value={values.externalDeadline}
          errorText={errors.externalDeadline}
          onChange={(event) => onChange({ externalDeadline: event.target.value })}
        />
        <Input
          id="post-external-url"
          type="url"
          label="공모전 링크"
          value={values.externalUrl}
          maxLength={URL_MAX}
          placeholder="https://"
          errorText={errors.externalUrl}
          onChange={(event) => onChange({ externalUrl: event.target.value })}
        />
      </div>
    </fieldset>
  )
}
