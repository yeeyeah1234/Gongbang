import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { apiClient } from '../../api/client'
import { addDays, datePart, toDateTime, ymd } from '../../features/workspace/calendar/dates'
import type {
  CalendarEventData,
  CalendarEventRequest,
} from '../../features/workspace/calendar/types'
import { strip, textLength } from '../../features/workspace/notice/validation'

// 개발 전용: /dev/calendar-preview가 떠 있는 동안만 팀 요약·일정 API를 메모리 데이터로 답한다.
export type PreviewView = 'leader' | 'member' | 'empty' | 'error'

export const PREVIEW_TEAM_ID = 0
const LEADER_ID = 1
const MEMBER_ID = 2
const OTHER_ID = 3
const NICKNAMES: Record<number, string> = { 1: '김하늘', 2: '이바다', 3: '박산들' }
const STALE_PERMISSION_ID = 90 // 수정 버튼은 보이지만 쓰면 403 NOT_EVENT_AUTHOR
const GONE_ON_WRITE_ID = 91 // 쓰면 404 (그사이 다른 사람이 지운 일정)
const COLORS = ['BLUE', 'GREEN', 'YELLOW', 'PURPLE', 'GRAY']
const MAX_RANGE_DAYS = 62
const DELAY_MS = 300

// 서버 ErrorCode 문구 그대로
const MESSAGES: Record<string, string> = {
  INTERNAL_SERVER_ERROR: '서버 오류(미리보기)',
  INVALID_INPUT: '잘못된 입력값입니다.',
  NOT_EVENT_AUTHOR: '작성자나 팀장만 바꿀 수 있어요',
  AUTO_EVENT_NOT_EDITABLE: '자동 일정은 바꿀 수 없어요',
  RESOURCE_NOT_FOUND: '없거나 삭제된 항목이에요',
}

type StoredEvent = Omit<CalendarEventData, 'authorNickname' | 'canEdit'>

// 이번 달 d일 (0 이하·말일 넘으면 앞뒤 달로 넘어간다). 2026-11에 열면 기능정의서 예시 날짜와 같다
function day(date: number, monthOffset = 0) {
  const now = new Date()
  return ymd(new Date(now.getFullYear(), now.getMonth() + monthOffset, date))
}

function timed(
  id: number,
  title: string,
  date: string,
  start: string,
  end: string,
  color: string,
  createdBy: number | null,
  memo: string | null = null,
): StoredEvent {
  // end가 start보다 이르면 다음 날 (밤샘 일정)
  const endDate = end <= start ? addDays(date, 1) : date
  return {
    id,
    title,
    startAt: toDateTime(date, start),
    endAt: toDateTime(endDate, end),
    allDay: false,
    color,
    memo,
    autoType: null,
    createdBy,
    createdAt: toDateTime(day(1), '09:00'),
  }
}

function allDay(
  id: number,
  title: string,
  start: string,
  end: string,
  color: string,
  createdBy: number | null,
  memo: string | null = null,
): StoredEvent {
  return {
    id,
    title,
    startAt: toDateTime(start, '00:00'),
    endAt: toDateTime(end, '00:00'),
    allDay: true,
    color,
    memo,
    autoType: null,
    createdBy,
    createdAt: toDateTime(day(1), '09:00'),
  }
}

function createEvents(): StoredEvent[] {
  return [
    // 달 경계에 걸친 여러 날 종일 일정 (11/28 ~ 12/03)
    allDay(
      1,
      '기획안 최종 검토 주간',
      day(28),
      day(3, 1),
      'GREEN',
      LEADER_ID,
      '각자 맡은 부분 다시 읽기\n마지막 날 오후에 합치기',
    ),
    // 하루짜리 종일 일정
    allDay(2, '기획안 1차 마감', day(9), day(9), 'PURPLE', MEMBER_ID),
    // 시간 일정 (매주 같은 시각)
    ...[5, 12, 19, 26].map((date, i) =>
      timed(3 + i, '정기 회의', day(date), '19:00', '20:00', 'BLUE', LEADER_ID, '회의록은 문서 탭'),
    ),
    // 자동 일정 (CAL-06). 서버는 RED로 주고 작성자·수정 권한이 없다
    {
      ...allDay(7, '공모전 접수 마감', day(16), day(16), 'RED', null),
      autoType: 'CONTEST_DEADLINE',
    },
    // 한 날짜에 5개 → 「+2개」
    allDay(8, '와이어프레임 공유', day(21), day(21), 'YELLOW', MEMBER_ID),
    timed(9, '디자인 리뷰', day(21), '10:00', '11:00', 'GRAY', OTHER_ID),
    timed(10, '점심 회의', day(21), '12:00', '13:00', 'PURPLE', LEADER_ID),
    timed(
      STALE_PERMISSION_ID,
      '권한이 바뀐 일정 (쓰면 403)',
      day(21),
      '15:00',
      '16:00',
      'BLUE',
      OTHER_ID,
    ),
    timed(
      GONE_ON_WRITE_ID,
      '지워진 일정 (쓰면 404)',
      day(21),
      '17:00',
      '18:00',
      'GREEN',
      MEMBER_ID,
    ),
    // 자정을 넘는 시간 일정
    timed(11, '야간 작업', day(25), '23:00', '01:00', 'GRAY', OTHER_ID),
    // 작성자가 탈퇴한 일정
    timed(12, '자료 조사', day(14), '14:00', '15:00', 'BLUE', null),
    // 아주 긴 제목
    timed(
      13,
      '제목이아주길어서막대에서는잘리고팝업에서는줄바꿈되는일정',
      day(2),
      '09:30',
      '10:00',
      'PURPLE',
      OTHER_ID,
    ),
  ]
}

type Result = { status: number; data?: unknown; code?: string }
const OK: Result = { status: 200 }
const INVALID: Result = { status: 400, code: 'INVALID_INPUT' }

// CalendarEventService.EventValues와 같은 검사. 통과하면 저장할 값
function normalize(body: Partial<CalendarEventRequest>) {
  const title = strip(body.title ?? '')
  const memo = strip(body.memo ?? '')
  const color = body.color ?? 'BLUE'
  if (textLength(title) < 1 || textLength(title) > 30 || textLength(memo) > 200) return null
  if (!COLORS.includes(color) || !body.startAt || !body.endAt) return null
  const isAllDay = body.allDay === true
  const startAt = isAllDay ? toDateTime(datePart(body.startAt), '00:00') : body.startAt
  const endAt = isAllDay ? toDateTime(datePart(body.endAt), '00:00') : body.endAt
  if (isAllDay ? endAt < startAt : endAt <= startAt) return null
  return { title, memo: memo || null, color, allDay: isAllDay, startAt, endAt }
}

function createHandler(view: PreviewView) {
  let events = view === 'empty' ? [] : createEvents()
  let nextId = 101
  const isLeader = view !== 'member'
  const me = view === 'member' ? MEMBER_ID : LEADER_ID

  const canEdit = (event: StoredEvent) =>
    event.autoType === null &&
    (event.id === STALE_PERMISSION_ID || isLeader || event.createdBy === me)

  const toResponse = (event: StoredEvent): CalendarEventData => ({
    ...event,
    authorNickname:
      event.autoType !== null
        ? null
        : event.createdBy === null
          ? '탈퇴한 사용자'
          : NICKNAMES[event.createdBy],
    canEdit: canEdit(event),
  })

  const write = (method: string, url: string, body: Partial<CalendarEventRequest>): Result => {
    if (method === 'post') {
      const values = normalize(body)
      if (!values) return INVALID
      const id = nextId++
      events.push({
        id,
        ...values,
        autoType: null,
        createdBy: me,
        createdAt: toDateTime(ymd(new Date()), '12:00'),
      })
      return { status: 200, data: { eventId: id } }
    }

    const id = Number(url.split('/').pop())
    if (id === GONE_ON_WRITE_ID) events = events.filter((event) => event.id !== id)
    const event = events.find((item) => item.id === id)
    if (!event) return { status: 404, code: 'RESOURCE_NOT_FOUND' }
    if (event.autoType !== null) return { status: 403, code: 'AUTO_EVENT_NOT_EDITABLE' }
    if (id === STALE_PERMISSION_ID || !canEdit(event))
      return { status: 403, code: 'NOT_EVENT_AUTHOR' }

    if (method === 'delete') {
      events = events.filter((item) => item !== event)
      return OK
    }
    const values = normalize(body)
    if (!values) return INVALID
    Object.assign(event, values)
    return { status: 200, data: { eventId: id } }
  }

  return ({ url = '', method = 'get', params, data }: InternalAxiosRequestConfig): Result => {
    if (url === `/api/teams/${PREVIEW_TEAM_ID}`) {
      return {
        status: 200,
        data: {
          teamId: PREVIEW_TEAM_ID,
          teamName: '공방팀',
          // member는 외부 공모전 팀처럼 「공모전 상세 보기」가 숨는다
          contestId: view === 'member' ? null : 1,
          contestTitle: '미리보기 공모전',
          deadline: day(16),
          isLeader,
          myMemberId: me,
        },
      }
    }
    if (view === 'error') return { status: 500, code: 'INTERNAL_SERVER_ERROR' }
    if (method !== 'get') return write(method, url, data ? JSON.parse(data) : {})

    if (url.startsWith('/api/teams/')) {
      // 서버와 같게: from~to 둘 다 포함, 최대 62일, 기간이 겹치는 일정
      const { from = '', to = '' } = params ?? {}
      const days = (Date.parse(to) - Date.parse(from)) / 86_400_000 + 1
      if (!(days >= 1 && days <= MAX_RANGE_DAYS)) return INVALID
      const toNext = toDateTime(addDays(to, 1), '00:00')
      const fromStart = toDateTime(from, '00:00')
      const items = events
        .filter((event) => event.startAt < toNext && event.endAt >= fromStart)
        .map(toResponse)
      return { status: 200, data: items }
    }

    const event = events.find((item) => item.id === Number(url.split('/').pop()))
    return event
      ? { status: 200, data: toResponse(event) }
      : { status: 404, code: 'RESOURCE_NOT_FOUND' }
  }
}

// 돌려주는 함수를 부르면 해제된다
export function installPreviewApi(view: PreviewView) {
  const handle = createHandler(view)

  const adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    await new Promise((resolve) => setTimeout(resolve, DELAY_MS))
    const { status, data = null, code } = handle(config)
    const error = code ? { code, message: MESSAGES[code] ?? code } : null
    const response: AxiosResponse = {
      status,
      statusText: String(status),
      headers: {},
      config,
      data: { success: !error, data, error },
    }
    if (error) throw new AxiosError(code, code, config, null, response)
    return response
  }

  const id = apiClient.interceptors.request.use((config) => {
    const isPreviewApi = /^\/api\/(teams\/0($|\/events)|events\/)/.test(config.url ?? '')
    return isPreviewApi ? Object.assign(config, { adapter }) : config
  })
  return () => apiClient.interceptors.request.eject(id)
}
