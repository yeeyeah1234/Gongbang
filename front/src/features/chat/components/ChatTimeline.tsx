import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, KeyboardEvent } from 'react'
import {
  ArrowDownIcon,
  CloseIcon,
  FlagIcon,
  ImagePlusIcon,
  MessageSquareIcon,
  SlidersIcon,
} from '../../../components/icons/Icons'
import { Avatar } from '../../../components/ui/Avatar'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import type { ChatMessage, ChatRoom, TeamMember } from '../types'

interface ChatTimelineProps {
  room: ChatRoom | undefined
  messages: ChatMessage[]
  members: TeamMember[]
  currentUserId: number
  autoAlertMessage: string
  showAutoAlert: boolean
  onDismissAutoAlert: () => void
  onOpenSettings: () => void
  onSendMessage: (payload: { content: string; imageUrl?: string; imageName?: string }) => void
}

/** 중앙 채팅 타임라인 + D-day 자동 알림 + 메시지 입력창 */
export function ChatTimeline({
  room,
  messages,
  members,
  currentUserId,
  autoAlertMessage,
  showAutoAlert,
  onDismissAutoAlert,
  onOpenSettings,
  onSendMessage,
}: ChatTimelineProps) {
  const [draft, setDraft] = useState('')
  const [pendingImage, setPendingImage] = useState<{ url: string; name: string } | null>(null)
  const [isAtBottom, setIsAtBottom] = useState(true)
  const [seenCount, setSeenCount] = useState(messages.length)

  const scrollRef = useRef<HTMLDivElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const prevMessageCountRef = useRef(messages.length)

  const memberMap = new Map(members.map((member) => [member.id, member]))

  const scrollToBottom = (behavior: ScrollBehavior = 'smooth') => {
    const el = scrollRef.current
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior })
    setIsAtBottom(true)
    setSeenCount(messages.length)
  }

  // 최초 진입 시 최하단으로 스크롤
  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [])

  // 과거 내역 조회 중 강제 스크롤 방지: 맨 아래에 있거나 내가 보낸 메시지일 때만 자동 스크롤
  useEffect(() => {
    if (messages.length > prevMessageCountRef.current) {
      const latest = messages[messages.length - 1]
      if (isAtBottom || latest?.senderId === currentUserId) {
        const el = scrollRef.current
        if (el) {
          requestAnimationFrame(() => {
            el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
          })
        }
      }
    }
    prevMessageCountRef.current = messages.length
  }, [messages, isAtBottom, currentUserId])

  useEffect(() => {
    return () => {
      if (pendingImage?.url.startsWith('blob:')) {
        URL.revokeObjectURL(pendingImage.url)
      }
    }
  }, [pendingImage])

  if (!room) {
    return (
      <section className="flex flex-1 flex-col items-center justify-center gap-4 rounded-card border border-line bg-surface p-8 text-center">
        <MessageSquareIcon className="h-12 w-12 text-text-disabled" />
        <p className="text-body text-text-sub">선택된 채팅방이 없습니다</p>
      </section>
    )
  }

  const handleScroll = () => {
    const el = scrollRef.current
    if (!el) return
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    const nearBottom = distanceFromBottom <= 32
    setIsAtBottom(nearBottom)
    if (nearBottom) {
      setSeenCount(messages.length)
    }
  }

  const handleImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    const previewUrl = URL.createObjectURL(file)
    setPendingImage({ url: previewUrl, name: file.name })
    event.target.value = ''
  }

  const handleSubmit = () => {
    const trimmed = draft.trim()
    if (!trimmed && !pendingImage) return
    onSendMessage({
      content: trimmed,
      imageUrl: pendingImage?.url,
      imageName: pendingImage?.name,
    })
    setDraft('')
    setPendingImage(null)
    setIsAtBottom(true)
    setSeenCount(messages.length + 1)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      handleSubmit()
    }
  }

  const participantCount = room.memberIds.length
  const canSend = draft.trim().length > 0 || pendingImage !== null
  const hasUnseenBelow = !isAtBottom && messages.length > seenCount

  return (
    <section
      aria-label="채팅방 대화 내용"
      className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-card border border-line bg-surface"
    >
      {/* 채팅방 상단 헤더 */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-divider px-6 py-4">
        <div className="flex min-w-0 items-center gap-3">
          <h2 className="truncate text-subheading font-bold text-text">{room.name}</h2>
          <Badge variant="primary-soft">
            {room.type === 'TEAM' ? `참여 ${participantCount}명` : '1:1 채팅'}
          </Badge>
          {room.isEnded && <Badge variant="surface-sub">종료</Badge>}
        </div>

        <Button variant="outline" size="sm" onClick={onOpenSettings}>
          <SlidersIcon className="h-4 w-4" />
          <span>방 설정 / 팀원 관리</span>
        </Button>
      </div>

      {/* D-day 자동 알림 배너 */}
      {showAutoAlert && room.type === 'TEAM' && (
        <div className="mx-6 mt-4 flex items-center justify-between gap-3 rounded-popover border border-danger-line bg-danger-soft px-4 py-3">
          <div className="flex min-w-0 items-center gap-2">
            <FlagIcon className="h-4 w-4 shrink-0 text-danger" />
            <p className="truncate text-small font-medium text-danger">{autoAlertMessage}</p>
          </div>
          <button
            type="button"
            onClick={onDismissAutoAlert}
            aria-label="알림 닫기"
            className="flex h-6 w-6 shrink-0 cursor-pointer items-center justify-center rounded-full text-danger transition-colors hover:bg-surface/60"
          >
            <CloseIcon className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* 메시지 타임라인 영역 */}
      <div className="relative mx-6 my-4 flex min-h-0 flex-1 flex-col overflow-hidden rounded-card bg-bg">
        <div
          ref={scrollRef}
          onScroll={handleScroll}
          className="flex-1 space-y-5 overflow-y-auto p-6"
        >
          {messages.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
              <MessageSquareIcon className="h-12 w-12 text-text-disabled" />
              <p className="text-body text-text-sub">
                아직 나눈 대화가 없습니다. 첫 메시지를 보내보세요.
              </p>
            </div>
          ) : (
            messages.map((message, index) => {
              const previous = index > 0 ? messages[index - 1] : undefined
              const showDateDivider = previous?.dateLabel !== message.dateLabel
              const isMine = message.senderId === currentUserId
              const sender = memberMap.get(message.senderId)

              return (
                <div key={message.id} className="space-y-4">
                  {showDateDivider && (
                    <div className="flex justify-center">
                      <span className="rounded-full border border-line bg-surface px-4 py-1 text-caption font-medium text-text-sub">
                        {message.dateLabel}
                      </span>
                    </div>
                  )}

                  {isMine ? (
                    <div className="flex items-start justify-end gap-3">
                      <div className="flex flex-col items-end justify-end self-end text-caption">
                        {message.isRead && <span className="font-medium text-primary">읽음</span>}
                        <span className="text-text-sub">{message.timeLabel}</span>
                      </div>

                      <div className="max-w-[70%] rounded-card bg-primary px-4 py-3 text-body text-surface">
                        {message.imageUrl && (
                          <img
                            src={message.imageUrl}
                            alt={message.imageName ?? '첨부 이미지'}
                            className="mb-2 max-h-60 rounded-popover object-cover"
                          />
                        )}
                        {message.content && (
                          <p className="whitespace-pre-line">{message.content}</p>
                        )}
                      </div>

                      <Avatar
                        name={sender?.name ?? '나'}
                        colorIndex={sender?.colorIndex ?? 1}
                        size="sm"
                        isLeader={sender?.role === 'LEADER'}
                      />
                    </div>
                  ) : (
                    <div className="flex items-start gap-3">
                      <Avatar
                        name={sender?.name ?? '팀원'}
                        colorIndex={sender?.colorIndex ?? 2}
                        size="sm"
                        isLeader={sender?.role === 'LEADER'}
                      />

                      <div className="flex max-w-[75%] min-w-0 flex-col gap-1">
                        <span className="text-small font-bold text-text">
                          {sender?.name ?? '알 수 없음'}
                        </span>
                        <div className="flex items-end gap-2">
                          <div className="rounded-card border border-line bg-surface px-4 py-3 text-body text-text">
                            {message.imageUrl && (
                              <img
                                src={message.imageUrl}
                                alt={message.imageName ?? '첨부 이미지'}
                                className="mb-2 max-h-60 rounded-popover object-cover"
                              />
                            )}
                            {message.content && (
                              <p className="whitespace-pre-line">{message.content}</p>
                            )}
                          </div>
                          <span className="shrink-0 text-caption text-text-sub">
                            {message.timeLabel}
                          </span>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )
            })
          )}
        </div>

        {/* 스크롤 위치가 위일 때 표시되는 하단 이동 버튼 */}
        {(!isAtBottom || hasUnseenBelow) && (
          <div className="pointer-events-none absolute inset-x-0 bottom-4 flex justify-center">
            <button
              type="button"
              onClick={() => scrollToBottom('smooth')}
              className="pointer-events-auto inline-flex cursor-pointer items-center gap-2 rounded-full border border-line-strong bg-surface px-4 py-2 text-small font-medium text-text shadow-pop transition-colors hover:bg-bg"
            >
              <ArrowDownIcon className="h-4 w-4 text-primary" />
              <span>{hasUnseenBelow ? '새 메시지 도착' : '맨 아래로 이동'}</span>
            </button>
          </div>
        )}
      </div>

      {/* 하단 입력창 */}
      <div className="px-6 pt-1 pb-6">
        {pendingImage && (
          <div className="mb-3 inline-flex items-center gap-3 rounded-popover border border-line bg-bg px-3 py-2">
            <img
              src={pendingImage.url}
              alt={pendingImage.name}
              className="h-10 w-10 rounded-input object-cover"
            />
            <span className="max-w-[200px] truncate text-small text-text">{pendingImage.name}</span>
            <button
              type="button"
              onClick={() => setPendingImage(null)}
              aria-label="첨부 이미지 제거"
              className="flex h-6 w-6 cursor-pointer items-center justify-center rounded-full text-text-sub hover:bg-surface hover:text-text"
            >
              <CloseIcon className="h-4 w-4" />
            </button>
          </div>
        )}

        <div className="flex items-end gap-3">
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            onChange={handleImageChange}
            className="hidden"
          />

          <Button
            variant="outline"
            size="md"
            onClick={() => fileInputRef.current?.click()}
            aria-label="이미지 첨부"
            className="px-4"
          >
            <ImagePlusIcon className="h-5 w-5 text-text-sub" />
            <span className="hidden text-small sm:inline">이미지 첨부</span>
          </Button>

          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={handleKeyDown}
            rows={1}
            placeholder="메시지를 입력하세요 (Enter: 전송 / Shift + Enter: 줄바꿈)"
            className="max-h-32 min-h-11 flex-1 resize-none rounded-input border border-line-input bg-surface px-4 py-2 text-body text-text placeholder:text-text-disabled focus:border-2 focus:border-primary focus:outline-none"
          />

          <Button variant="primary" size="md" disabled={!canSend} onClick={handleSubmit}>
            전송
          </Button>
        </div>
      </div>
    </section>
  )
}
