import { Button } from '../../../../components/ui/Button'
import { Checkbox } from '../../../../components/ui/Checkbox'
import { Input } from '../../../../components/ui/Input'
import { Modal } from '../../../../components/ui/Modal'
import { textLength } from '../../notice/validation'
import { EVENT_COLORS } from '../colors'
import { timeOptions } from '../dates'
import type { EventFormValues } from '../types'
import { hasErrors, MEMO_MAX, TITLE_MAX, validateEventForm } from '../validation'

interface EventFormModalProps {
  editing: boolean
  // 확인 모달과 바꿔 그려도 남도록 값은 부모가 들고 있는다
  values: EventFormValues
  titleTouched: boolean
  busy: boolean
  onChange: (change: Partial<EventFormValues>) => void
  onSubmit: () => void
  onClose: () => void
}

// 입력창 규격(STYLE_GUIDE 5-2)을 따르는 시간 목록. 공통 Input과 같은 모양
const SELECT_BASE =
  'h-14 w-full cursor-pointer rounded-input bg-surface px-4 text-body text-text focus:outline-none disabled:cursor-not-allowed disabled:border disabled:border-line disabled:bg-surface-sub disabled:text-text-disabled'
const SELECT_NORMAL = 'border border-line-input focus:border-2 focus:border-primary'
const SELECT_ERROR = 'border-2 border-danger'

interface TimeSelectProps {
  id: string
  label: string
  value: string
  disabled: boolean
  errorText?: string
  onChange: (value: string) => void
}

// CAL-03 ④ 30분 단위 시간 목록 (CAL-03b 커스텀 목록 대신 기본 select)
function TimeSelect({ id, label, value, disabled, errorText, onChange }: TimeSelectProps) {
  return (
    <div className="flex flex-col">
      <label htmlFor={id} className="mb-2 text-body font-bold text-text">
        {label}
      </label>
      <select
        id={id}
        value={value}
        disabled={disabled}
        aria-invalid={Boolean(errorText)}
        onChange={(event) => onChange(event.target.value)}
        className={[SELECT_BASE, errorText ? SELECT_ERROR : SELECT_NORMAL].join(' ')}
      >
        {timeOptions(value).map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {errorText && <p className="mt-2 text-small text-danger">{errorText}</p>}
    </div>
  )
}

// CAL-03 일정 생성, CAL-04 ⑤ 수정 (작성 모달 600)
export function EventFormModal({
  editing,
  values,
  titleTouched,
  busy,
  onChange,
  onSubmit,
  onClose,
}: EventFormModalProps) {
  const errors = validateEventForm(values)
  const titleError = titleTouched ? errors.title : undefined
  const memoLength = textLength(values.memo)

  return (
    <Modal
      open
      size="lg"
      title={editing ? '일정 수정' : '새 일정'}
      onClose={onClose}
      footer={
        <>
          <Button size="lg" disabled={busy} onClick={onClose}>
            취소
          </Button>
          <Button
            variant="primary"
            size="lg"
            loading={busy}
            disabled={hasErrors(errors)}
            onClick={onSubmit}
          >
            저장
          </Button>
        </>
      }
    >
      {/* 공통 Modal에는 스크롤이 없어 낮은 화면에서 버튼이 밖으로 나간다. 본문만 스크롤 */}
      <div className="-mx-1 flex max-h-[calc(100dvh-16rem)] flex-col gap-4 overflow-y-auto px-1">
        <div className="relative shrink-0">
          <Input
            id="event-title"
            label="제목"
            required
            autoFocus
            readOnly={busy}
            value={values.title}
            aria-invalid={Boolean(titleError)}
            aria-describedby="event-title-count"
            errorText={titleError}
            // 오류가 없어도 한 줄을 잡아 두어 글자 수 자리와 버튼이 움직이지 않게
            helperText={' '}
            onChange={(event) => onChange({ title: event.target.value })}
          />
          <span
            id="event-title-count"
            className={[
              'absolute right-0 bottom-0 text-small',
              titleError ? 'text-danger' : 'text-text-sub',
            ].join(' ')}
          >
            {textLength(values.title)} / {TITLE_MAX}
          </span>
        </div>

        <div className="grid shrink-0 grid-cols-2 gap-4">
          <Input
            id="event-start-date"
            label="시작일"
            type="date"
            required
            readOnly={busy}
            value={values.startDate}
            onChange={(event) => onChange({ startDate: event.target.value })}
          />
          <Input
            id="event-end-date"
            label="종료일"
            type="date"
            required
            readOnly={busy}
            value={values.endDate}
            aria-invalid={Boolean(errors.endDate)}
            errorText={errors.endDate}
            onChange={(event) => onChange({ endDate: event.target.value })}
          />
        </div>

        <Checkbox
          className="shrink-0 self-start"
          label="종일"
          checked={values.allDay}
          disabled={busy}
          onChange={(event) => onChange({ allDay: event.target.checked })}
        />

        {/* CAL-03 ⑤ 종일이면 시간은 고를 수 없다 */}
        <div className="grid shrink-0 grid-cols-2 gap-4">
          <TimeSelect
            id="event-start-time"
            label="시작 시간"
            value={values.startTime}
            disabled={busy || values.allDay}
            onChange={(startTime) => onChange({ startTime })}
          />
          <TimeSelect
            id="event-end-time"
            label="종료 시간"
            value={values.endTime}
            disabled={busy || values.allDay}
            errorText={errors.endTime}
            onChange={(endTime) => onChange({ endTime })}
          />
        </div>

        <fieldset className="shrink-0">
          <legend className="mb-2 text-body font-bold text-text">색상</legend>
          <div className="flex gap-3">
            {EVENT_COLORS.map((color) => (
              <label key={color.value} className="cursor-pointer p-1">
                <input
                  type="radio"
                  name="event-color"
                  value={color.value}
                  checked={values.color === color.value}
                  disabled={busy}
                  aria-label={color.label}
                  onChange={() => onChange({ color: color.value })}
                  className="peer sr-only"
                />
                <span
                  aria-hidden="true"
                  className={`block size-8 rounded-full ring-offset-2 ring-offset-surface peer-checked:ring-2 peer-checked:ring-text peer-focus-visible:outline-2 peer-focus-visible:outline-offset-4 peer-focus-visible:outline-primary ${color.solid}`}
                />
              </label>
            ))}
          </div>
        </fieldset>

        <div className="flex shrink-0 flex-col">
          <label htmlFor="event-memo" className="mb-2 text-body font-bold text-text">
            메모<span className="ml-1 font-normal text-text-sub">(선택)</span>
          </label>
          <textarea
            id="event-memo"
            readOnly={busy}
            value={values.memo}
            aria-invalid={Boolean(errors.memo)}
            aria-describedby="event-memo-count"
            onChange={(event) => onChange({ memo: event.target.value })}
            className={[
              'h-40 w-full resize-none rounded-input bg-surface px-4 py-3 text-body text-text focus:outline-none',
              errors.memo
                ? 'border-2 border-danger'
                : 'border border-line-input focus:border-2 focus:border-primary',
            ].join(' ')}
          />
          <p className="mt-2 flex justify-between gap-2 text-small text-danger">
            <span>{errors.memo}</span>
            <span id="event-memo-count" className={errors.memo ? '' : 'text-text-sub'}>
              {memoLength} / {MEMO_MAX}
            </span>
          </p>
        </div>

        {/* TODO(정진우): 일정 알림(CAL-07) 예약이 연결되어야 아래 문구대로 알림이 간다 */}
        <p className="shrink-0 text-small text-text-sub">
          저장하면 팀원 전체에게 시작 24시간 전에 알림이 가요.
        </p>
      </div>
    </Modal>
  )
}
