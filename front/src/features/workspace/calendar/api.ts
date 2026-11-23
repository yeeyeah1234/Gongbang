import { apiClient } from '../../../api/client'
import type { ApiResponse } from '../../../api/types'
import type { CalendarEventData, CalendarEventRequest } from './types'

// CAL-03 목록: from~to('YYYY-MM-DD', 둘 다 포함, 최대 62일)와 겹치는 일정. 자동 일정 포함
export async function fetchEvents(teamId: number, from: string, to: string) {
  return (
    await apiClient.get<ApiResponse<CalendarEventData[]>>(`/api/teams/${teamId}/events`, {
      params: { from, to },
    })
  ).data.data
}

// CAL-04 생성. 새 일정 ID를 돌려준다
export async function createEvent(teamId: number, body: CalendarEventRequest) {
  return (
    await apiClient.post<ApiResponse<{ eventId: number }>>(`/api/teams/${teamId}/events`, body)
  ).data.data.eventId
}

// CAL-04 ⑤ 수정 (모든 값을 보낸다)
export async function updateEvent(eventId: number, body: CalendarEventRequest) {
  await apiClient.patch(`/api/events/${eventId}`, body)
}

// CAL-05 삭제
export async function deleteEvent(eventId: number) {
  await apiClient.delete(`/api/events/${eventId}`)
}
