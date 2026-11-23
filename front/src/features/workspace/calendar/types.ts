// 일반 일정 색 5가지 (CalendarEventColor). 자동 일정은 서버가 RED로 주지만 화면은 autoType으로 구분한다
export type EventColor = 'BLUE' | 'GREEN' | 'YELLOW' | 'PURPLE' | 'GRAY'

// GET /api/teams/{teamId}/events 항목 (CalendarEventResponse).
// 시각은 시간대 없는 한국 시각 「YYYY-MM-DDTHH:mm:ss」. 종일 일정은 「종료일 포함」(endAt = 마지막 날 00:00)
export interface CalendarEventData {
  id: number
  title: string
  startAt: string
  endAt: string
  allDay: boolean
  color: string
  memo: string | null
  // 자동 일정(CAL-06)이면 'CONTEST_DEADLINE', 일반 일정은 null
  autoType: string | null
  // 자동 일정·작성자 행이 지워진 일정은 null
  createdBy: number | null
  // 자동 일정은 null, 탈퇴한 작성자는 「탈퇴한 사용자」
  authorNickname: string | null
  createdAt: string
  // 지금 나(작성자 또는 팀장)가 수정·삭제할 수 있는지. 자동 일정은 항상 false
  canEdit: boolean
}

// CAL-04 POST /api/teams/{teamId}/events, CAL-05 PATCH /api/events/{eventId} (모든 값을 보낸다)
export interface CalendarEventRequest {
  title: string
  startAt: string
  endAt: string
  allDay: boolean
  color: EventColor
  memo: string
}

// 생성·수정 모달 입력값. 날짜는 'YYYY-MM-DD', 시간은 'HH:mm'
export interface EventFormValues {
  title: string
  startDate: string
  startTime: string
  endDate: string
  endTime: string
  allDay: boolean
  color: EventColor
  memo: string
}
