import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import { Badge } from '../../../../components/ui/Badge'
import { Button } from '../../../../components/ui/Button'
import { Modal } from '../../../../components/ui/Modal'
import { LockIcon } from '../../components/WorkspaceIcons'
import { eventColorClasses, isAutoEvent } from '../colors'
import { formatDayTitle, formatTimeRange } from '../dates'
import type { CalendarEventData } from '../types'

interface DayEventsModalProps {
  day: string
  // eventsOnDay로 고른 그날 일정 (1개 이상)
  events: CalendarEventData[]
  selectedId: number | null
  // 자동 일정 「공모전 상세 보기」 링크. 외부 공모전이면 null
  contestId: number | null
  onSelect: (eventId: number) => void
  onEdit: (event: CalendarEventData) => void
  onDelete: (event: CalendarEventData) => void
  onClose: () => void
}

interface EventCardProps {
  event: CalendarEventData
  selected: boolean
  contestId: number | null
  onSelect: () => void
}

function EventCard({ event, selected, contestId, onSelect }: EventCardProps) {
  const auto = isAutoEvent(event)
  const { solid } = eventColorClasses(event)

  return (
    <article
      className={[
        'relative flex gap-3 rounded-popover border p-4 transition-colors',
        selected ? 'border-primary bg-primary-soft ring-1 ring-primary' : 'border-line hover:bg-bg',
      ].join(' ')}
    >
      {/* 카드 전체를 누르는 버튼. 안의 링크는 relative로 이 위에 올린다 (button 안에 a를 넣지 않게) */}
      <button
        type="button"
        aria-pressed={selected}
        aria-label={`${event.title} 선택`}
        onClick={onSelect}
        className="absolute inset-0 cursor-pointer rounded-popover focus-visible:outline-2 focus-visible:outline-primary"
      />
      <span aria-hidden="true" className={`w-1 shrink-0 self-stretch rounded-full ${solid}`} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-center gap-2">
          {auto && (
            <>
              <LockIcon className="h-4 w-4 shrink-0 text-danger" />
              <Badge variant="danger">자동</Badge>
            </>
          )}
          <h3 className="min-w-0 text-body font-bold wrap-anywhere text-text">{event.title}</h3>
        </div>
        <p className="text-small text-text-sub">{formatTimeRange(event)}</p>
        {event.memo && (
          <p className="text-small wrap-anywhere whitespace-pre-line text-text">{event.memo}</p>
        )}
        {auto ? (
          contestId !== null && (
            <Link
              to={`/contests/${contestId}`}
              className="relative self-start text-small font-medium text-primary hover:underline"
            >
              공모전 상세 보기
            </Link>
          )
        ) : (
          <p className="text-small text-text-sub">{event.authorNickname}</p>
        )}
      </div>
    </article>
  )
}

// CAL-04 그날 일정 팝업 (워크스페이스 안 작은 모달 460, 기능정의서 「약 440px」)
export function DayEventsModal({
  day,
  events,
  selectedId,
  contestId,
  onSelect,
  onEdit,
  onDelete,
  onClose,
}: DayEventsModalProps) {
  // 다시 불러온 뒤 없어진 일정이면 선택이 풀린 것으로 본다
  const selected = events.find((event) => event.id === selectedId) ?? null
  const wrapperRef = useRef<HTMLDivElement>(null)

  // 열릴 때 포커스를 제목으로 옮긴다 (화면 읽기 프로그램이 날짜부터 읽게. 공통 Modal에는 없다).
  // 누른 날짜 칸에 남은 선택 위치도 지운다. 캐럿은 아래 caret-transparent로 숨긴다
  useEffect(() => {
    const title = wrapperRef.current?.querySelector<HTMLElement>('#modal-title')
    if (!title) return
    window.getSelection()?.removeAllRanges()
    title.tabIndex = -1
    title.focus({ preventScroll: true })
  }, [])

  return (
    // 이 팝업에는 입력칸이 없어 안쪽 전체에서 캐럿을 숨겨도 된다 (Chrome 캐럿 브라우징·포커스 시작점)
    <div ref={wrapperRef} className="caret-transparent [&_#modal-title]:outline-none">
      <Modal
        open
        size="sm"
        title={formatDayTitle(day)}
        onClose={onClose}
        footer={
          <>
            {/* CAL-04 ④ 작성자·팀장만. 자동 일정은 canEdit가 항상 false */}
            {selected?.canEdit && (
              <>
                <Button
                  variant="danger"
                  size="lg"
                  className="mr-auto"
                  onClick={() => onDelete(selected)}
                >
                  일정 삭제
                </Button>
                <Button size="lg" onClick={() => onEdit(selected)}>
                  수정
                </Button>
              </>
            )}
            <Button variant="primary" size="lg" onClick={onClose}>
              확인
            </Button>
          </>
        }
      >
        <ul className="-mx-1 flex max-h-[calc(100dvh-20rem)] flex-col gap-3 overflow-y-auto px-1 py-1">
          {events.map((event) => (
            <li key={event.id}>
              <EventCard
                event={event}
                selected={event.id === selected?.id}
                contestId={contestId}
                onSelect={() => onSelect(event.id)}
              />
            </li>
          ))}
        </ul>
      </Modal>
    </div>
  )
}
