import { useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../components/ui/Button'
import { Chip } from '../components/ui/Chip'
import { Toast } from '../components/ui/Toast'
import { useTimedToast } from '../hooks/useTimedToast'
import { NotificationRow } from '../features/notification/components/NotificationRow'
import { CATEGORY_FILTER, dateGroup } from '../features/notification/format'
import {
  useDeleteReadNotifications,
  useMarkAllRead,
  useNotificationList,
  useUnreadCount,
} from '../features/notification/hooks'
import type { NotificationCategory, NotificationItem } from '../features/notification/types'

// NOTI-01 알림 전체 보기 / Figma 12-1(전체) · 12-2(안 읽음)
export default function NotificationsPage() {
  const toast = useTimedToast()
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [category, setCategory] = useState<NotificationCategory | undefined>(undefined)
  const list = useNotificationList({ unreadOnly, category })
  const unread = useUnreadCount()
  const markAll = useMarkAllRead()
  const deleteRead = useDeleteReadNotifications()

  const items = list.data?.pages.flatMap((p) => p.items) ?? []
  // ③ 오늘 / 어제 / 이번 주 / 이전
  const groups = items.reduce<{ label: string; items: NotificationItem[] }[]>((acc, item) => {
    const label = dateGroup(item.createdAt)
    const last = acc[acc.length - 1]
    if (last && last.label === label) last.items.push(item)
    else acc.push({ label, items: [item] })
    return acc
  }, [])

  return (
    <main className="mx-auto w-full max-w-[960px] px-4 py-10">
      <div className="flex items-center justify-between">
        <h1 className="text-title font-bold text-text">알림</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => markAll.mutate()}>
            모두 읽음
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() =>
              deleteRead.mutate(undefined, { onSuccess: () => toast.show('읽은 알림을 지웠어요') })
            }
          >
            읽은 알림 삭제
          </Button>
          <Link to="/mypage/settings?tab=notification">
            <Button variant="secondary" size="sm">
              알림 설정
            </Button>
          </Link>
        </div>
      </div>

      <div className="mt-6 flex gap-10 border-b border-divider" role="tablist">
        {[false, true].map((value) => (
          <button
            key={String(value)}
            type="button"
            role="tab"
            aria-selected={unreadOnly === value}
            onClick={() => setUnreadOnly(value)}
            className={[
              '-mb-px h-[52px] cursor-pointer border-b-2 text-subheading',
              unreadOnly === value
                ? 'border-primary font-bold text-text'
                : 'border-transparent text-text-sub',
            ].join(' ')}
          >
            {value ? `안 읽음 ${unread.data ?? ''}` : '전체'}
          </button>
        ))}
      </div>

      {/* ② 종류 필터 */}
      <div className="mt-4 flex flex-wrap gap-2">
        {CATEGORY_FILTER.map((f) => (
          <Chip
            key={f.label}
            size="sm"
            selected={category === f.value}
            onClick={() => setCategory(f.value)}
          >
            {f.label}
          </Chip>
        ))}
      </div>

      {/* ⑥ 30일 자동 삭제 안내 */}
      <p className="mt-4 text-caption text-text-sub">30일이 지난 알림은 자동으로 지워져요.</p>

      {list.isLoading ? (
        <div className="mt-6 h-64 animate-pulse rounded-card bg-surface-sub" />
      ) : list.isError ? (
        <p className="mt-10 text-center text-small text-text-sub">
          알림을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
      ) : items.length === 0 ? (
        <div className="mt-20 flex flex-col items-center gap-3 text-center">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            className="size-12 text-text-disabled"
            aria-hidden="true"
          >
            <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
            <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
          </svg>
          <p className="text-body text-text-sub">
            {unreadOnly ? '읽지 않은 알림이 없어요' : '새 알림이 없어요'}
          </p>
        </div>
      ) : (
        <>
          {groups.map((group) => (
            <section key={group.label} className="mt-6">
              <h2 className="mb-2 text-small font-bold text-text-sub">{group.label}</h2>
              <ul className="overflow-hidden rounded-card border border-line bg-surface">
                {group.items.map((item) => (
                  <NotificationRow key={item.notificationId} item={item} onError={toast.show} />
                ))}
              </ul>
            </section>
          ))}
          {unreadOnly && !list.hasNextPage && (
            <div className="mt-6 text-center">
              <p className="text-small text-text-sub">안 읽은 알림은 여기까지예요</p>
              <Button variant="text" size="sm" onClick={() => setUnreadOnly(false)}>
                지난 알림 전체 보기
              </Button>
            </div>
          )}
          {list.hasNextPage && (
            <div className="mt-6 text-center">
              <Button
                variant="outline"
                loading={list.isFetchingNextPage}
                onClick={() => list.fetchNextPage()}
              >
                더 보기
              </Button>
            </div>
          )}
        </>
      )}
      <Toast message={toast.message} />
    </main>
  )
}
