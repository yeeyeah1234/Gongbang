import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { useWorkspace } from '../context'
import { createEvent, deleteEvent, fetchEvents, updateEvent } from './api'
import type { CalendarEventRequest } from './types'

export interface CalendarRange {
  from: string
  to: string
}

export const calendarKeys = {
  all: (teamId: number) => ['calendarEvents', teamId] as const,
  range: (teamId: number, { from, to }: CalendarRange) =>
    [...calendarKeys.all(teamId), from, to] as const,
}

// range: 달력이 그려진 뒤(datesSet) 정해진다.
// keepPreviousData: 달을 넘길 때 비었다가 다시 차지 않게 (겹치는 주의 일정은 같은 날짜에 그대로 있다)
export function useCalendarEvents(teamId: number, range: CalendarRange | null) {
  return useQuery({
    queryKey: range ? calendarKeys.range(teamId, range) : calendarKeys.all(teamId),
    queryFn: () => fetchEvents(teamId, range!.from, range!.to),
    enabled: range !== null,
    placeholderData: keepPreviousData,
    retry: false,
  })
}

// 그 팀의 모든 달 일정을 다시 불러온다
export function useRefreshCalendar(teamId: number) {
  const client = useQueryClient()
  return useCallback(
    () => client.invalidateQueries({ queryKey: calendarKeys.all(teamId) }),
    [client, teamId],
  )
}

// 쓰기 공통
// - networkMode always: 오프라인이면 멈춰 기다리지 않고 바로 실패해 토스트가 뜬다 (공지와 같다)
// - 모달 닫기는 화면이 mutate(…, { onSuccess })로 한다
export function useCreateEvent() {
  const { teamId } = useWorkspace()
  const refresh = useRefreshCalendar(teamId)
  return useMutation({
    mutationFn: (body: CalendarEventRequest) => createEvent(teamId, body),
    networkMode: 'always',
    onSuccess: () => {
      void refresh()
    },
  })
}

export function useUpdateEvent() {
  const { teamId } = useWorkspace()
  const refresh = useRefreshCalendar(teamId)
  return useMutation({
    mutationFn: ({ eventId, body }: { eventId: number; body: CalendarEventRequest }) =>
      updateEvent(eventId, body),
    networkMode: 'always',
    onSuccess: () => {
      void refresh()
    },
  })
}

export function useDeleteEvent() {
  const { teamId } = useWorkspace()
  const refresh = useRefreshCalendar(teamId)
  return useMutation({
    mutationFn: deleteEvent,
    networkMode: 'always',
    onSuccess: () => {
      void refresh()
    },
  })
}
