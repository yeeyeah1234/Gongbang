import { useEffect, useLayoutEffect, useRef, useState } from 'react'
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
import { memberColorIndex } from '../../workspace/onlineMembers'
import type { ChatMessage, ChatRoom, TeamMember } from '../types'

interface ChatTimelineProps {
  room: ChatRoom | undefined
  messages: ChatMessage[]
  members: TeamMember[]
  currentUserId: number
  autoAlertMessage: string
  showAutoAlert: boolean
  isLoadingMessages?: boolean
  hasNextPage?: boolean
  isFetchingNextPage?: boolean
  isConnected?: boolean
  onLoadOlder?: () => void
  onReachBottom?: () => void
  onDismissAutoAlert: () => void
  onOpenSettings: () => void
  onSendMessage: (content: string) => boolean
  onUnsupportedFeature: (message: string) => void
}

/** 중앙 채팅 타임라인 + D-day 자동 알림 + 메시지 입력창 */
export function ChatTimeline({
  room,
  messages,
  members,
  currentUserId,
  autoAlertMessage,
  showAutoAlert,
  isLoadingMessages = false,
  hasNextPage = false,
  isFetchingNextPage = false,
  isConnected = false,
  onLoadOlder,
  onReachBottom,
  onDismissAutoAlert,
  onOpenSettings,
  onSendMessage,
  onUnsupportedFeature,
}: ChatTimelineProps) {
  const [draft, setDraft] = useState('')
  const [isAtBottom, setIsAtBottom] = useState(true)
  const [seenLastId, setSeenLastId] = useState<number | null>(null)

  const scrollRef = useRef<HTMLDivElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const initializedRoomRef = useRef(false)
  const prevFirstIdRef = useRef<number | null>(null)
  const prevLastIdRef = useRef<number | null>(null)
  const prevScrollHeightRef = useRef(0)

  const memberMap = new Map(members.map((member) => [member.id, member]))

  const latestMessage = messages[messages.length - 1]
  const latestMessageId = latestMessage?.id ?? null

  const scrollToBottom = (behavior: ScrollBehavior = 'smooth') => {
    const el = scrollRef.current
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior })
    setIsAtBottom(true)
    if (latestMessageId !== null) {
      setSeenLastId(latestMessageId)
    }
    onReachBottom?.()
  }

  useLayoutEffect(() => {
    const el = scrollRef.current
    if (!el || messages.length === 0) {
      prevFirstIdRef.current = null
      prevLastIdRef.current = null
      prevScrollHeightRef.current = 0
      return
    }

    const firstId = messages[0].id
    const lastId = messages[messages.length - 1].id

    if (!initializedRoomRef.current) {
      el.scrollTop = el.scrollHeight
      initializedRoomRef.current = true
    } else if (
      prevFirstIdRef.current !== null &&
      firstId !== prevFirstIdRef.current &&
      lastId === prevLastIdRef.current
    ) {
      // 이전 대화가 위쪽에 추가되면 보고 있던 스크롤 위치를 유지한다
      const delta = el.scrollHeight - prevScrollHeightRef.current
      if (delta > 0) {
        el.scrollTop += delta
      }
    } else if (prevLastIdRef.current !== null && lastId !== prevLastIdRef.current) {
      const latest = messages[messages.length - 1]
      if (isAtBottom || latest?.senderId === currentUserId) {
        requestAnimationFrame(() => {
          el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
        })
      }
    }

    prevFirstIdRef.current = firstId
    prevLastIdRef.current = lastId
    prevScrollHeightRef.current = el.scrollHeight
  }, [messages, isAtBottom, currentUserId])

  useEffect(() => {
    if (isAtBottom && latestMessageId !== null) {
      onReachBottom?.()
    }
  }, [isAtBottom, latestMessageId, onReachBottom])

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
    prevScrollHeightRef.current = el.scrollHeight

    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    const nearBottom = distanceFromBottom <= 32
    setIsAtBottom(nearBottom)
    if (nearBottom) {
      if (latestMessageId !== null) {
        setSeenLastId(latestMessageId)
      }
      onReachBottom?.()
    }
  }

  const handleImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    // TODO(확인 필요): 공통 파일 업로드(uploaded_file) API가 연결되면 이미지 첨부 전송 구현
    onUnsupportedFeature('이미지 첨부는 파일 업로드 모듈 연동 후 이용할 수 있어요')
  }

  const handleSubmit = () => {
    const trimmed = draft.trim()
    if (!trimmed) return
    const sent = onSendMessage(trimmed)
    if (!sent) return
    setDraft('')
    setIsAtBottom(true)
    if (latestMessageId !== null) {
      setSeenLastId(latestMessageId)
    }
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      handleSubmit()
    }
  }

  const participantCount = room.type === 'TEAM' ? members.length : 2
  const canSend = draft.trim().length > 0 && isConnected
  const hasUnseenBelow =
    !isAtBottom && latestMessageId !== null && (seenLastId === null || latestMessageId > seenLastId)

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
          {!isConnected && <Badge variant="surface-sub">연결 중</Badge>}
        </div>

        <Button variant="outline" size="sm" onClick={onOpenSettings}>
          <SlidersIcon className="h-4 w-4" />
          <span>방 설정 / 팀원 관리</span>
        </Button>
      </div>

      {/* D-day 자동 알림 배너 */}
      {showAutoAlert && autoAlertMessage && room.type === 'TEAM' && (
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
          {hasNextPage && onLoadOlder && (
            <div className="flex justify-center">
              <Button
                variant="outline"
                size="sm"
                disabled={isFetchingNextPage}
                onClick={onLoadOlder}
              >
                {isFetchingNextPage ? '불러오는 중…' : '이전 대화 더 보기'}
              </Button>
            </div>
          )}

          {isLoadingMessages && messages.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center text-center">
              <p className="text-body text-text-sub">대화 내역을 불러오고 있어요…</p>
            </div>
          ) : messages.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
              <MessageSquareIcon className="h-12 w-12 text-text-disabled" />
              <p className="text-body text-text-sub">
                아직 나눈 대화가 없습니다. 첫 메시지를 보내보세요.
              </p>
            </div>
          ) : (
            messages.map((message, index) => {
              const previous = index > 0 ? messages[index - 1] : undefined
              const showDateDivider = Boolean(
                message.dateLabel && previous?.dateLabel !== message.dateLabel,
              )

              if (message.type === 'SYSTEM') {
                return (
                  <div key={message.id} className="space-y-4">
                    {showDateDivider && (
                      <div className="flex justify-center">
                        <span className="rounded-full border border-line bg-surface px-4 py-1 text-caption font-medium text-text-sub">
                          {message.dateLabel}
                        </span>
                      </div>
                    )}
                    <div className="flex justify-center">
                      <span className="rounded-full bg-surface-sub px-4 py-1.5 text-small text-text-sub">
                        {message.content}
                      </span>
                    </div>
                  </div>
                )
              }

              const isMine = message.senderId === currentUserId
              const sender = message.senderId !== null ? memberMap.get(message.senderId) : undefined
              const senderName = sender?.name ?? message.senderNickname
              const senderColor =
                sender?.colorIndex ??
                (message.senderId !== null ? memberColorIndex(message.senderId) : 1)
              const senderIsLeader = sender?.role === 'LEADER'

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
                        <span className="text-text-sub">{message.timeLabel}</span>
                      </div>

                      <div className="max-w-[70%] rounded-card bg-primary px-4 py-3 text-body text-surface">
                        {message.imageUrl && (
                          <img
                            src={message.imageUrl}
                            alt="첨부 이미지"
                            className="mb-2 max-h-60 rounded-popover object-cover"
                          />
                        )}
                        {message.content && (
                          <p className="whitespace-pre-line">{message.content}</p>
                        )}
                      </div>

                      <Avatar
                        name={senderName}
                        colorIndex={senderColor}
                        size="sm"
                        isLeader={senderIsLeader}
                      />
                    </div>
                  ) : (
                    <div className="flex items-start gap-3">
                      <Avatar
                        name={senderName}
                        colorIndex={senderColor}
                        size="sm"
                        isLeader={senderIsLeader}
                      />

                      <div className="flex max-w-[75%] min-w-0 flex-col gap-1">
                        <span className="text-small font-bold text-text">{senderName}</span>
                        <div className="flex items-end gap-2">
                          <div className="rounded-card border border-line bg-surface px-4 py-3 text-body text-text">
                            {message.imageUrl && (
                              <img
                                src={message.imageUrl}
                                alt="첨부 이미지"
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
            maxLength={2000}
            placeholder={
              isConnected
                ? '메시지를 입력하세요 (Enter: 전송 / Shift + Enter: 줄바꿈)'
                : '실시간 채팅 서버에 연결하고 있습니다…'
            }
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
