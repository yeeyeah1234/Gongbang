import type { EventInput } from '@fullcalendar/core'
import type { CalendarEventData, EventFormValues } from './types'

// 캘린더의 날짜 변환과 표시 문구는 모두 여기에 둔다.
// 서버 시각은 시간대 없는 한국 시각이고, FullCalendar도 timeZone 'local'(브라우저 시각)로 그대로 읽는다.
// TODO(확인 필요): 한국 밖 시간대 브라우저는 고려하지 않는다 (AGENTS 2-4: 모두 KST)

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function parseDay(day: string) {
  const [year, month, date] = day.split('-').map(Number)
  return { year, month, date }
}

// 브라우저 시각 기준 'YYYY-MM-DD'
export function ymd(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

// 'YYYY-MM-DD' → n일 뒤 'YYYY-MM-DD'. UTC로 계산해 서머타임·시간대 영향을 받지 않는다
export function addDays(day: string, days: number) {
  const { year, month, date } = parseDay(day)
  return new Date(Date.UTC(year, month - 1, date + days)).toISOString().slice(0, 10)
}

export function datePart(dateTime: string) {
  return dateTime.slice(0, 10)
}

export function timePart(dateTime: string) {
  return dateTime.slice(11, 16)
}

// 'YYYY-MM-DD' + 'HH:mm' → 서버 형식 'YYYY-MM-DDTHH:mm:00'
export function toDateTime(day: string, time: string) {
  return `${day}T${time}:00`
}

/**
 * 서버 일정 → FullCalendar 입력.
 * 종일 일정은 서버가 「종료일 포함」(11/25~26 → endAt 11-26T00:00)으로, FullCalendar는 「끝 날짜 제외」로 본다.
 * 그래서 FullCalendar에 넘길 때만 끝에 하루를 더한다. 이 변환은 이 함수에서만 한다.
 */
export function toCalendarInput(event: CalendarEventData): EventInput {
  const base = { id: String(event.id), title: event.title, extendedProps: { event } }
  if (event.allDay) {
    return {
      ...base,
      allDay: true,
      start: datePart(event.startAt),
      end: addDays(datePart(event.endAt), 1),
    }
  }
  return { ...base, allDay: false, start: event.startAt, end: event.endAt }
}

// FullCalendar datesSet의 보이는 칸 전체(앞뒤 달 날짜 포함, end는 제외) → 조회 범위(from·to 둘 다 포함)
export function rangeFromView(start: Date, end: Date) {
  return { from: ymd(start), to: addDays(ymd(end), -1) }
}

// 일정이 걸친 마지막 날. 시간 일정이 00:00에 끝나면 그 전날까지 (FullCalendar 기본 nextDayThreshold와 같다)
export function lastDayOf(event: CalendarEventData) {
  const end = datePart(event.endAt)
  if (event.allDay) return end
  return timePart(event.endAt) === '00:00' && end > datePart(event.startAt) ? addDays(end, -1) : end
}

// 그날 보이는 일정: 종일 일정 먼저, 그다음 시작 시각 순 (달력 칸과 같은 기준)
export function eventsOnDay(events: CalendarEventData[], day: string) {
  return events
    .filter((event) => datePart(event.startAt) <= day && day <= lastDayOf(event))
    .sort(
      (a, b) =>
        Number(b.allDay) - Number(a.allDay) || a.startAt.localeCompare(b.startAt) || a.id - b.id,
    )
}

// day를 일정 기간 안으로 맞춘다 (막대를 키보드로 눌러 누른 칸을 모를 때)
export function clampToEvent(event: CalendarEventData, day: string) {
  const first = datePart(event.startAt)
  const last = lastDayOf(event)
  if (day < first) return first
  if (day > last) return last
  return day
}

// CAL-03 ⑥ 기본값: 다음 정각부터 1시간. 23시 이후는 23:00 ~ 다음 날 00:00
export function defaultTimes(now: Date) {
  const startHour = Math.min(now.getHours() + 1, 23)
  const endHour = startHour + 1
  return {
    startTime: `${pad(startHour)}:00`,
    endTime: `${pad(endHour % 24)}:00`,
    endDayOffset: endHour === 24 ? 1 : 0,
  }
}

// 「+ 일정」(오늘)·날짜 칸 클릭(그 날짜)으로 여는 새 일정 기본값
export function newEventValues(day: string, now: Date): EventFormValues {
  const { startTime, endTime, endDayOffset } = defaultTimes(now)
  return {
    title: '',
    startDate: day,
    startTime,
    endDate: addDays(day, endDayOffset),
    endTime,
    allDay: false,
    color: 'BLUE',
    memo: '',
  }
}

// 수정 모달에 채울 값. 종일 일정의 시간 칸은 비활성이므로 기본값으로 채워 둔다
export function eventToFormValues(event: CalendarEventData, now: Date): EventFormValues {
  const times = event.allDay
    ? defaultTimes(now)
    : { startTime: timePart(event.startAt), endTime: timePart(event.endAt) }
  return {
    title: event.title,
    startDate: datePart(event.startAt),
    startTime: times.startTime,
    endDate: datePart(event.endAt),
    endTime: times.endTime,
    allDay: event.allDay,
    color: isEventColor(event.color) ? event.color : 'BLUE',
    // textarea가 CRLF를 LF로 바꾸므로 맞춰 둬야 손대지 않은 값이 바뀐 것으로 보이지 않는다
    memo: (event.memo ?? '').replace(/\r\n?/g, '\n'),
  }
}

function isEventColor(color: string): color is EventFormValues['color'] {
  return ['BLUE', 'GREEN', 'YELLOW', 'PURPLE', 'GRAY'].includes(color)
}

// 모달 값 → 요청 시각. 종일이면 둘 다 그날 00:00 (서버도 같게 맞춘다)
export function toRequestTimes(values: EventFormValues) {
  return {
    startAt: toDateTime(values.startDate, values.allDay ? '00:00' : values.startTime),
    endAt: toDateTime(values.endDate, values.allDay ? '00:00' : values.endTime),
  }
}

// 「2026년 11월」
export function formatMonthTitle(date: Date) {
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월`
}

// 「11월 12일」
function formatMonthDay(day: string) {
  const { month, date } = parseDay(day)
  return `${month}월 ${date}일`
}

// 「2026년 11월 12일 (목)」
export function formatDayTitle(day: string) {
  const { year, month, date } = parseDay(day)
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, date)).getUTCDay()]
  return `${year}년 ${formatMonthDay(day)} (${weekday})`
}

// 'HH:mm' → 「오후 07:00」
export function formatTime12(time: string) {
  const hour = Number(time.slice(0, 2))
  const half = hour < 12 ? '오전' : '오후'
  return `${half} ${pad(hour % 12 === 0 ? 12 : hour % 12)}:${time.slice(3, 5)}`
}

// 팝업 카드 시간: 「오후 07:00 ~ 오후 08:00」, 「종일」, 여러 날이면 날짜를 붙인다
export function formatTimeRange(event: CalendarEventData) {
  const startDay = datePart(event.startAt)
  const endDay = datePart(event.endAt)
  if (event.allDay) {
    return startDay === endDay
      ? '종일'
      : `${formatMonthDay(startDay)} ~ ${formatMonthDay(endDay)} · 종일`
  }
  const start = formatTime12(timePart(event.startAt))
  const end = formatTime12(timePart(event.endAt))
  if (startDay === endDay) return `${start} ~ ${end}`
  return `${formatMonthDay(startDay)} ${start} ~ ${formatMonthDay(endDay)} ${end}`
}

// 시간 목록 00:00 ~ 23:30 (30분 단위)
const TIME_VALUES = Array.from(
  { length: 48 },
  (_, i) => `${pad(Math.floor(i / 2))}:${i % 2 ? '30' : '00'}`,
)

// 30분 단위가 아닌 기존 값(예: 19:15)도 고를 수 있게 목록에 끼운다
export function timeOptions(...extra: string[]) {
  return [...new Set([...TIME_VALUES, ...extra])]
    .sort()
    .map((value) => ({ value, label: formatTime12(value) }))
}
