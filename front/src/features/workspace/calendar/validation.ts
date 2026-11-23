import { isValidLength, textLength } from '../notice/validation'
import { toRequestTimes } from './dates'
import type { EventFormValues } from './types'

// 서버(CalendarEventService.EventValues)와 같은 규칙: strip 뒤 코드포인트로 센다
export const TITLE_MAX = 30
export const MEMO_MAX = 200

export interface EventFormErrors {
  title?: string
  memo?: string
  // 종일: 종료일 칸, 시간 일정: 종료 시간 칸 아래에 표시
  endDate?: string
  endTime?: string
}

// CAL-03 ⑦ 문구는 기능정의서 그대로.
// TODO(확인 필요): 제목·메모 글자 수 오류 문구는 기능정의서에 없어 공지 형식을 따랐다
export function validateEventForm(values: EventFormValues): EventFormErrors {
  const errors: EventFormErrors = {}
  if (!isValidLength(values.title, TITLE_MAX))
    errors.title = `제목은 1~${TITLE_MAX}자로 입력해 주세요`
  if (textLength(values.memo) > MEMO_MAX) errors.memo = `메모는 ${MEMO_MAX}자까지 입력할 수 있어요`

  if (!values.startDate || !values.endDate) {
    errors.endDate = '날짜를 입력해 주세요'
    return errors
  }
  // 종료일이 앞서면 종일 여부와 상관없이 종료일 칸에만 띄운다.
  // 종료 시간 오류는 같은 날짜에서 종료 <= 시작일 때만 (다음 날로 넘어가는 일정은 통과)
  if (values.endDate < values.startDate) {
    errors.endDate = '종료일은 시작일보다 빠를 수 없어요'
  } else if (!values.allDay) {
    const { startAt, endAt } = toRequestTimes(values)
    if (endAt <= startAt) errors.endTime = '종료 시간은 시작 시간보다 늦어야 해요'
  }
  return errors
}

export function hasErrors(errors: EventFormErrors) {
  return Object.keys(errors).length > 0
}
