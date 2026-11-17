import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useMarkAllRead, useRecentNotifications, useUnreadCount } from '../hooks'
import { NotificationRow } from './NotificationRow'

function BellIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-6"
      aria-hidden="true"
    >
      <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
    </svg>
  )
}

// NOTI-03 종 + 빨간 숫자(99+) / NOTI-02 드롭다운 (Figma 14)
export function NotificationBell({ onError }: { onError?: (message: string) => void }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const unread = useUnreadCount()
  const recent = useRecentNotifications(open)
  const markAll = useMarkAllRead()
  const count = unread.data ?? 0

  // ⑤ 바깥을 누르거나 Esc
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    const esc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', esc)
    }
  }, [open])

  const items = recent.data?.items ?? []

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-label={`알림${count ? ` ${count}개 안 읽음` : ''}`}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="relative flex size-10 cursor-pointer items-center justify-center rounded-full text-text hover:bg-bg"
      >
        <BellIcon />
        {count > 0 && (
          <span className="absolute top-0.5 right-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1 text-caption font-bold text-surface">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>
      {open && (
        <div
          role="dialog"
          aria-label="알림"
          className="absolute right-0 z-40 mt-2 w-[380px] rounded-popover border border-line bg-surface shadow-pop"
        >
          <div className="flex items-center justify-between border-b border-divider px-4 py-3">
            <span className="text-body font-bold text-text">알림 {count > 0 ? count : ''}</span>
            <button
              type="button"
              onClick={() => markAll.mutate()}
              className="cursor-pointer text-caption text-text-sub hover:text-text"
            >
              모두 읽음
            </button>
          </div>
          {recent.isLoading ? (
            <div className="m-4 h-40 animate-pulse rounded-input bg-surface-sub" />
          ) : items.length === 0 ? (
            <p className="px-4 py-10 text-center text-small text-text-sub">새 알림이 없어요</p>
          ) : (
            <ul className="max-h-[420px] overflow-y-auto">
              {items.map((item) => (
                <NotificationRow
                  key={item.notificationId}
                  item={item}
                  compact
                  onNavigate={() => setOpen(false)}
                  onError={onError}
                />
              ))}
            </ul>
          )}
          <Link
            to="/notifications"
            onClick={() => setOpen(false)}
            className="block border-t border-divider py-3 text-center text-small font-bold text-text hover:bg-bg"
          >
            알림 전체 보기 ›
          </Link>
        </div>
      )}
    </div>
  )
}
