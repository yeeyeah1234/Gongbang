import type FullCalendar from '@fullcalendar/react'
import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { getApiError } from '../api/errors'
import { Button } from '../components/ui/Button'
import { Toast } from '../components/ui/Toast'
import { CalendarToolbar } from '../features/workspace/calendar/components/CalendarToolbar'
import { DayEventsModal } from '../features/workspace/calendar/components/DayEventsModal'
import { EventFormModal } from '../features/workspace/calendar/components/EventFormModal'
import { TeamCalendar } from '../features/workspace/calendar/components/TeamCalendar'
import { eventsOnDay, formatMonthTitle } from '../features/workspace/calendar/dates'
import { useCalendarEvents, type CalendarRange } from '../features/workspace/calendar/hooks'
import type { CalendarEventData } from '../features/workspace/calendar/types'
import { useCalendarDialog } from '../features/workspace/calendar/useCalendarDialog'
import { useWorkspace } from '../features/workspace/context'
import { workspaceKeys } from '../features/workspace/hooks'
import { NoticeConfirmModal } from '../features/workspace/notice/components/NoticeConfirmModal'
import { useTimedToast } from '../hooks/useTimedToast'

const NO_EVENTS: CalendarEventData[] = []

// CAL-01·03·04·05·08 캘린더 탭: /workspaces/:teamId/calendar (월간 보기만, 주간 보기 CAL-02는 2차)
export default function WorkspaceCalendarPage() {
  const { teamId } = useWorkspace()
  // 팀이 바뀌면 보던 달·모달·토스트까지 처음부터
  return <CalendarTab key={teamId} />
}

function CalendarTab() {
  const { teamId, summary } = useWorkspace()
  const queryClient = useQueryClient()
  const toast = useTimedToast()
  const dialog = useCalendarDialog({ showToast: toast.show })
  const calendarRef = useRef<FullCalendar>(null)
  const [range, setRange] = useState<CalendarRange | null>(null)
  const [title, setTitle] = useState('')

  const query = useCalendarEvents(teamId, range)
  const events = query.data ?? NO_EVENTS

  // 팀에서 빠졌으면 레이아웃이 권한 없음 화면으로 바꾼다
  const notMember = query.isError && getApiError(query.error).code === 'NOT_TEAM_MEMBER'
  useEffect(() => {
    if (notMember) void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
  }, [notMember, teamId, queryClient])

  const handleDatesSet = useCallback((next: CalendarRange, monthStart: Date) => {
    setRange((current) => (current?.from === next.from && current.to === next.to ? current : next))
    setTitle(formatMonthTitle(monthStart))
  }, [])

  const calendarApi = () => calendarRef.current?.getApi()

  // CAL-01 ⑥: 일정이 있는 날은 그날 팝업, 빈 날은 그 날짜로 새 일정
  const handleDayClick = (day: string) => {
    if (eventsOnDay(events, day).length > 0) dialog.openDay(day)
    else dialog.openCreate(day)
  }

  const { state } = dialog
  // 다시 불러온 뒤 그날 일정이 모두 없어졌으면 팝업을 그리지 않는다 (다음에 열 때 상태가 바뀐다)
  const dayEvents = state?.kind === 'day' ? eventsOnDay(events, state.day) : NO_EVENTS
  const showing = state?.kind === 'day' && dayEvents.length === 0 ? null : state

  const renderModal = () => {
    switch (showing?.kind) {
      case 'day':
        return (
          <DayEventsModal
            day={showing.day}
            events={dayEvents}
            selectedId={showing.selectedId}
            contestId={summary.contestId}
            onSelect={dialog.select}
            onEdit={dialog.openEdit}
            onDelete={(event) => dialog.requestDelete(event, showing.day)}
            onClose={dialog.close}
          />
        )
      case 'form':
        return (
          <EventFormModal
            editing={showing.form.eventId !== null}
            values={showing.form.values}
            titleTouched={showing.form.titleTouched}
            busy={dialog.busy}
            onChange={dialog.change}
            onSubmit={dialog.submit}
            onClose={dialog.requestClose}
          />
        )
      case 'discard':
        return (
          <NoticeConfirmModal
            title="작성 중인 일정"
            question="작성 중인 일정이 사라져요. 닫을까요?"
            confirmLabel="닫기"
            onConfirm={dialog.close}
            onCancel={dialog.keepEditing}
          />
        )
      case 'delete':
        return (
          <NoticeConfirmModal
            title="일정 삭제"
            question={`‘${showing.event.title}’ 일정을 삭제할까요?`}
            note="삭제하면 되돌릴 수 없어요"
            confirmLabel="삭제"
            danger
            busy={dialog.busy}
            onConfirm={dialog.confirmDelete}
            onCancel={dialog.cancelDelete}
          />
        )
      default:
        return null
    }
  }

  return (
    <>
      {/* inert: 공통 Modal에 포커스 가두기가 없어 Tab으로 뒤쪽 버튼을 누르지 못하게 */}
      <div
        inert={showing !== null}
        className="flex flex-1 flex-col gap-4 lg:min-h-0 lg:overflow-y-auto"
      >
        <CalendarToolbar
          title={title}
          onToday={() => calendarApi()?.today()}
          onPrev={() => calendarApi()?.prev()}
          onNext={() => calendarApi()?.next()}
          onCreate={() => dialog.openCreate()}
        />
        {query.isError && !notMember && (
          // WS-07 ③ 불러오기 실패
          <div
            role="alert"
            className="flex flex-wrap items-center justify-between gap-3 rounded-popover border border-danger bg-surface px-5 py-4"
          >
            <div>
              <p className="text-body font-bold text-danger">불러오지 못했어요</p>
              <p className="text-small text-text-sub">인터넷 연결을 확인하고 다시 시도해 주세요.</p>
            </div>
            <Button size="sm" loading={query.isFetching} onClick={() => query.refetch()}>
              다시 시도
            </Button>
          </div>
        )}
        <section
          aria-label="팀 캘린더"
          aria-busy={query.isFetching}
          className="shrink-0 overflow-hidden rounded-card border border-line bg-surface"
        >
          <TeamCalendar
            calendarRef={calendarRef}
            events={events}
            onDatesSet={handleDatesSet}
            onDayClick={handleDayClick}
            onEventClick={dialog.openDay}
            onMoreClick={dialog.openDay}
          />
        </section>
      </div>
      {renderModal()}
      {/* Modal과 z-index가 같아 뒤에 그려야 위에 보인다 */}
      <Toast message={toast.message} />
    </>
  )
}
