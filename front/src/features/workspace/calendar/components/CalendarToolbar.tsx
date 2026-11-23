import { ChevronDownIcon, PlusIcon } from '../../../../components/icons/Icons'
import { Button } from '../../../../components/ui/Button'

interface CalendarToolbarProps {
  // 「2026년 11월」. 달력이 그려지기 전에는 빈 문자열
  title: string
  onToday: () => void
  onPrev: () => void
  onNext: () => void
  onCreate: () => void
}

// 공통 Button은 size의 px-5를 늘 붙이고, CSS에서 px-5가 px-0보다 뒤에 있어 덮어쓸 수 없다
// (44px 안에 아이콘 자리가 4px만 남아 빈 원으로 보였다). 그래서 outline 버튼과 같은 모양을 직접 그린다
const ARROW_CLASS =
  'flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-full border border-line-strong bg-surface text-text transition-colors hover:bg-bg'

// CAL-08 날짜 이동 + CAL-03 「+ 일정」 + 범례 (FullCalendar headerToolbar 대신 직접 그린다)
export function CalendarToolbar({
  title,
  onToday,
  onPrev,
  onNext,
  onCreate,
}: CalendarToolbarProps) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <Button onClick={onToday}>오늘</Button>
        <button type="button" aria-label="이전 달" className={ARROW_CLASS} onClick={onPrev}>
          <ChevronDownIcon className="h-5 w-5 rotate-90" />
        </button>
        <button type="button" aria-label="다음 달" className={ARROW_CLASS} onClick={onNext}>
          <ChevronDownIcon className="h-5 w-5 -rotate-90" />
        </button>
        <h2 aria-live="polite" className="ml-2 text-title font-bold text-text">
          {title}
        </h2>
        <Button variant="primary" className="ml-auto" onClick={onCreate}>
          <PlusIcon />
          일정
        </Button>
      </div>
      <ul className="flex flex-wrap items-center gap-x-4 gap-y-1 text-caption text-text-sub">
        <li className="flex items-center gap-1.5">
          <span
            aria-hidden="true"
            className="size-3 rounded-sm border border-primary bg-primary-soft"
          />
          팀 일정
        </li>
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" className="size-3 rounded-sm bg-danger" />
          자동 일정 (공모전 마감, 수정 불가)
        </li>
      </ul>
    </div>
  )
}
