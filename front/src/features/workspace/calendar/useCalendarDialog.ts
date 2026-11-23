import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { getApiError, getErrorMessage } from '../../../api/errors'
import { useWorkspace } from '../context'
import { workspaceKeys } from '../hooks'
import { strip } from '../notice/validation'
import { eventToFormValues, newEventValues, toRequestTimes, ymd } from './dates'
import { useCreateEvent, useDeleteEvent, useRefreshCalendar, useUpdateEvent } from './hooks'
import type { CalendarEventData, CalendarEventRequest, EventFormValues } from './types'
import { hasErrors, validateEventForm } from './validation'

export const GONE_MESSAGE = '이 일정은 삭제되었어요'

export interface EventForm {
  // null이면 생성
  eventId: number | null
  initial: EventFormValues
  values: EventFormValues
  titleTouched: boolean
}

// 한 번에 하나만 그린다 (공통 Modal은 겹치면 Esc에 함께 닫히고 id도 겹친다)
export type CalendarDialogState =
  // CAL-04 그날 일정 팝업. selectedId: 막대를 눌러 열었으면 그 일정
  | { kind: 'day'; day: string; selectedId: number | null }
  | { kind: 'form'; form: EventForm }
  // 닫기 확인. 「취소」면 폼으로 돌아가므로 값을 들고 있는다
  | { kind: 'discard'; form: EventForm }
  // 「취소」면 그날 팝업으로 돌아간다
  | { kind: 'delete'; event: CalendarEventData; day: string }

interface CalendarDialogOptions {
  showToast: (message: string) => void
}

function isDirty({ initial, values }: EventForm) {
  return (Object.keys(initial) as (keyof EventFormValues)[]).some(
    (key) => initial[key] !== values[key],
  )
}

// CAL-03~05 모달 상태, 연타 잠금, 쓰기 오류 처리
export function useCalendarDialog({ showToast }: CalendarDialogOptions) {
  const { teamId } = useWorkspace()
  const queryClient = useQueryClient()
  const refresh = useRefreshCalendar(teamId)
  const create = useCreateEvent()
  const update = useUpdateEvent()
  const remove = useDeleteEvent()

  const [state, setState] = useState<CalendarDialogState | null>(null)
  const [busy, setBusy] = useState(false)
  const lock = useRef(false)
  useEffect(() => {
    if (!busy) lock.current = false
  }, [busy])

  const begin = () => {
    if (lock.current) return false
    lock.current = true
    setBusy(true)
    return true
  }

  const end = () => {
    lock.current = false
    setBusy(false)
  }

  const close = () => setState(null)

  // 성공: 닫는다 (목록은 mutation의 onSuccess가 다시 불러온다)
  const succeed = () => {
    end()
    close()
  }

  const fail = (error: unknown) => {
    end()
    const { code } = getApiError(error)
    if (code === 'RESOURCE_NOT_FOUND') {
      showToast(GONE_MESSAGE)
      close()
      void refresh()
      return
    }
    // 400·500·오프라인은 모달을 그대로 두어 다시 시도할 수 있게
    showToast(getErrorMessage(error))
    if (code === 'NOT_EVENT_AUTHOR' || code === 'AUTO_EVENT_NOT_EDITABLE') {
      // 그사이 권한이 바뀌었다. canEdit를 새로 받는다
      close()
      void refresh()
    } else if (code === 'NOT_TEAM_MEMBER') {
      // 팀에서 빠졌으면 WorkspaceLayout이 권한 없음 화면으로 바꾼다
      close()
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
    }
  }

  // ---------- 그날 일정 팝업 ----------

  const openDay = (day: string, selectedId: number | null = null) =>
    setState({ kind: 'day', day, selectedId })

  const select = (selectedId: number) =>
    setState((current) => (current?.kind === 'day' ? { ...current, selectedId } : current))

  // ---------- 생성·수정 (CAL-03, CAL-04 ⑤) ----------

  const openForm = (eventId: number | null, values: EventFormValues) =>
    setState({ kind: 'form', form: { eventId, initial: values, values, titleTouched: false } })

  // day 없음: 「+ 일정」은 오늘
  const openCreate = (day?: string) => {
    const now = new Date()
    openForm(null, newEventValues(day ?? ymd(now), now))
  }

  const openEdit = (event: CalendarEventData) =>
    openForm(event.id, eventToFormValues(event, new Date()))

  const change = (values: Partial<EventFormValues>) =>
    setState((current) =>
      current?.kind === 'form'
        ? {
            kind: 'form',
            form: {
              ...current.form,
              values: { ...current.form.values, ...values },
              titleTouched: current.form.titleTouched || values.title !== undefined,
            },
          }
        : current,
    )

  const submit = () => {
    if (state?.kind !== 'form') return
    const { eventId, values } = state.form
    if (hasErrors(validateEventForm(values)) || !begin()) return

    const body: CalendarEventRequest = {
      title: strip(values.title),
      ...toRequestTimes(values),
      allDay: values.allDay,
      color: values.color,
      memo: strip(values.memo),
    }
    if (eventId === null) create.mutate(body, { onSuccess: succeed, onError: fail })
    else update.mutate({ eventId, body }, { onSuccess: succeed, onError: fail })
  }

  // X·Esc·바깥·취소: 입력이 있으면 한 번 묻는다
  const requestClose = () => {
    if (busy || state?.kind !== 'form') return
    if (isDirty(state.form)) setState({ kind: 'discard', form: state.form })
    else close()
  }

  const keepEditing = () =>
    setState((current) =>
      current?.kind === 'discard' ? { kind: 'form', form: current.form } : current,
    )

  // ---------- 삭제 (CAL-05) ----------

  const requestDelete = (event: CalendarEventData, day: string) =>
    setState({ kind: 'delete', event, day })

  const cancelDelete = () => {
    if (busy) return
    setState((current) =>
      current?.kind === 'delete'
        ? { kind: 'day', day: current.day, selectedId: current.event.id }
        : current,
    )
  }

  const confirmDelete = () => {
    if (state?.kind !== 'delete' || !begin()) return
    remove.mutate(state.event.id, { onSuccess: succeed, onError: fail })
  }

  return {
    state,
    busy,
    close,
    openDay,
    select,
    openCreate,
    openEdit,
    change,
    submit,
    requestClose,
    keepEditing,
    requestDelete,
    cancelDelete,
    confirmDelete,
  }
}

export type CalendarDialog = ReturnType<typeof useCalendarDialog>
