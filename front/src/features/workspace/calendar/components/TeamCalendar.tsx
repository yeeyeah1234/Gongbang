import type {
  DatesSetArg,
  DayCellContentArg,
  DayHeaderContentArg,
  EventClickArg,
  EventContentArg,
  MoreLinkArg,
  MoreLinkSimpleAction,
} from '@fullcalendar/core'
import koLocale from '@fullcalendar/core/locales/ko'
import dayGridPlugin from '@fullcalendar/daygrid'
import interactionPlugin, { type DateClickArg } from '@fullcalendar/interaction'
import FullCalendar from '@fullcalendar/react'
import { useMemo, type Ref } from 'react'
import { eventColorClasses, isAutoEvent } from '../colors'
import { clampToEvent, datePart, rangeFromView, timePart, toCalendarInput, ymd } from '../dates'
import type { CalendarRange } from '../hooks'
import type { CalendarEventData } from '../types'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const MAX_EVENTS_PER_DAY = 3

// FullCalendar v6 MoreLinkContainer.handleClick: 문자열이 아닌 truthy 값을 돌려주면
// 기본 팝오버도 열지 않고 다른 보기로 옮기지도 않는다. 타입에는 없는 값이라 여기 한 곳에서만 바꿔 쓴다
const KEEP_DEFAULT_POPOVER_CLOSED = true as unknown as MoreLinkSimpleAction

interface TeamCalendarProps {
  calendarRef: Ref<FullCalendar>
  events: CalendarEventData[]
  // monthStart: 보고 있는 달의 1일 (제목용)
  onDatesSet: (range: CalendarRange, monthStart: Date) => void
  // 막대·「+N개」가 아닌 날짜 칸
  onDayClick: (day: string) => void
  onEventClick: (day: string, eventId: number) => void
  onMoreClick: (day: string) => void
}

function eventOf(arg: { event: { extendedProps: Record<string, unknown> } }) {
  return arg.event.extendedProps.event as CalendarEventData
}

// 여러 날 막대는 첫 칸 안에서 옆 칸 위로 걸쳐 그려지므로, 누른 자리 아래의 날짜 칸으로 그날을 찾는다
function clickedDay(arg: EventClickArg, event: CalendarEventData) {
  if (arg.jsEvent instanceof MouseEvent) {
    const cell = document
      .elementsFromPoint(arg.jsEvent.clientX, arg.jsEvent.clientY)
      .find((el) => el.matches('td.fc-daygrid-day[data-date]'))
    const day = cell?.getAttribute('data-date')
    if (day) return clampToEvent(event, day)
  }
  // 키보드로 눌렀으면 그 막대가 들어 있는 칸(그 주의 첫 칸)
  const day = arg.el.closest('[data-date]')?.getAttribute('data-date')
  return clampToEvent(event, day ?? datePart(event.startAt))
}

function renderDayHeader(arg: DayHeaderContentArg) {
  return <span className="text-small font-medium text-text">{WEEKDAYS[arg.dow]}</span>
}

// CAL-01 ②: 오늘은 파란 원, 일요일은 빨간 숫자, 다른 달 날짜는 흐리게
function renderDayCell(arg: DayCellContentArg) {
  const color = arg.isToday
    ? 'bg-primary font-bold text-surface'
    : arg.isOther
      ? 'text-text-disabled'
      : arg.dow === 0
        ? 'text-danger'
        : 'text-text'
  return (
    <span
      className={`flex size-7 items-center justify-center rounded-full text-small ${color}`}
      aria-current={arg.isToday ? 'date' : undefined}
    >
      {arg.date.getDate()}
    </span>
  )
}

// CAL-01 ③⑤, CAL-06 ②: 색 막대 + 제목. 시간 일정은 첫 조각에만 「19:00」을 붙인다
// 자동 일정은 막대에서는 「자동 · 제목」 글자만 (자물쇠는 그날 팝업 카드에만)
function renderEvent(arg: EventContentArg) {
  const event = eventOf(arg)
  const { soft, solid } = eventColorClasses(event)
  const auto = isAutoEvent(event)
  let text = event.title
  if (auto) text = `자동 · ${event.title}`
  else if (!event.allDay && arg.isStart) text = `${timePart(event.startAt)} ${event.title}`

  return (
    <div
      className={`flex h-6 w-full items-center gap-1.5 overflow-hidden rounded-sm pr-2 text-small ${soft}`}
    >
      <span aria-hidden="true" className={`h-full w-1 shrink-0 ${solid}`} />
      <span className={`truncate ${auto ? 'font-bold' : 'font-medium'}`}>{text}</span>
    </div>
  )
}

// CAL-01 월간 보기 (CAL-02 주간 보기는 2차). 날짜 이동·제목은 바깥 툴바가 calendarRef로 한다
export function TeamCalendar({
  calendarRef,
  events,
  onDatesSet,
  onDayClick,
  onEventClick,
  onMoreClick,
}: TeamCalendarProps) {
  const inputs = useMemo(() => events.map(toCalendarInput), [events])

  return (
    <div className="gb-calendar">
      <FullCalendar
        ref={calendarRef}
        plugins={[dayGridPlugin, interactionPlugin]}
        initialView="dayGridMonth"
        locale={koLocale}
        firstDay={0}
        headerToolbar={false}
        fixedWeekCount={false}
        height="auto"
        events={inputs}
        eventDisplay="block"
        dayMaxEvents={MAX_EVENTS_PER_DAY}
        moreLinkContent={(arg) => `+${arg.num}개`}
        dayHeaderContent={renderDayHeader}
        dayCellContent={renderDayCell}
        eventContent={renderEvent}
        datesSet={(arg: DatesSetArg) =>
          onDatesSet(rangeFromView(arg.start, arg.end), arg.view.currentStart)
        }
        dateClick={(arg: DateClickArg) => onDayClick(arg.dateStr.slice(0, 10))}
        eventClick={(arg: EventClickArg) => {
          arg.jsEvent.preventDefault()
          const event = eventOf(arg)
          onEventClick(clickedDay(arg, event), event.id)
        }}
        moreLinkClick={(arg: MoreLinkArg) => {
          onMoreClick(ymd(arg.date))
          return KEEP_DEFAULT_POPOVER_CLOSED
        }}
      />
    </div>
  )
}
