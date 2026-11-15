import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Toast } from '../../components/ui/Toast'
import { useTimedToast } from '../../hooks/useTimedToast'
import { DDayBadge } from '../../features/contest/components/ContestCard'
import { updateBookmark } from '../../features/contest/api'
import { MyPageLayout } from '../../features/member/components/MyPageLayout'
import { formatDate } from '../../features/member/constants'
import { useBookmarks } from '../../features/member/hooks'
import type { BookmarkSort } from '../../features/member/types'

const SORTS: { value: BookmarkSort; label: string }[] = [
  { value: 'deadline', label: '마감 임박순' },
  { value: 'bookmarked', label: '북마크한 순' },
  { value: 'latest', label: '최신순' },
]

function BookmarkIcon({ filled }: { filled: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className="size-6"
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={2}
      aria-hidden="true"
    >
      <path d="M6 3h12v18l-6-4-6 4z" />
    </svg>
  )
}

// MY-03 북마크 목록 / Figma 10-1 · 10-2(정렬 팝업) · 10-3(해제)
export default function MyBookmarksPage() {
  const toast = useTimedToast()
  const queryClient = useQueryClient()
  const [sort, setSort] = useState<BookmarkSort>('deadline')
  const [sortOpen, setSortOpen] = useState(false)
  const [removed, setRemoved] = useState<number | null>(null)
  const sortRef = useRef<HTMLDivElement>(null)
  const { data, isLoading, isError } = useBookmarks(sort)

  useEffect(() => {
    if (!sortOpen) return
    const close = (e: MouseEvent) => {
      if (!sortRef.current?.contains(e.target as Node)) setSortOpen(false)
    }
    const esc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setSortOpen(false)
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', esc)
    }
  }, [sortOpen])

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['members', 'me', 'bookmarks'] })
    void queryClient.invalidateQueries({ queryKey: ['contests'] })
  }

  // ③ 아이콘을 다시 누르면 바로 해제 + [실행 취소] (Figma 10-3)
  const unbookmark = async (contestId: number) => {
    await updateBookmark(contestId, false)
    setRemoved(contestId)
    refresh()
    toast.show('북마크에서 해제했어요')
  }
  const undo = async () => {
    if (removed === null) return
    await updateBookmark(removed, true)
    setRemoved(null)
    refresh()
  }

  const items = data?.items ?? []

  return (
    <MyPageLayout
      breadcrumb="마이페이지 / 북마크"
      title="북마크"
      description="관심 있는 공모전을 모아 보고, 마감 전에 도전해 보세요."
    >
      <div className="flex items-center justify-between">
        <span className="text-body font-bold text-text">
          저장한 공모전 {data?.totalCount ?? 0}개
        </span>
        <div ref={sortRef} className="relative">
          <button
            type="button"
            aria-haspopup="listbox"
            aria-expanded={sortOpen}
            onClick={() => setSortOpen((v) => !v)}
            className="flex h-11 w-[156px] cursor-pointer items-center justify-between rounded-full border border-line-strong bg-surface px-4 text-small text-text"
          >
            {SORTS.find((s) => s.value === sort)?.label}
            <span aria-hidden="true">{sortOpen ? '⌃' : '⌄'}</span>
          </button>
          {sortOpen && (
            <ul
              role="listbox"
              className="absolute right-0 z-20 mt-2 w-[180px] rounded-popover border border-line bg-surface py-2 shadow-pop"
            >
              {SORTS.map((s) => (
                <li key={s.value} role="option" aria-selected={sort === s.value}>
                  <button
                    type="button"
                    onClick={() => {
                      setSort(s.value)
                      setSortOpen(false)
                    }}
                    className="flex h-11 w-full cursor-pointer items-center justify-between px-4 text-body text-text hover:bg-bg"
                  >
                    {s.label}
                    {sort === s.value && <span className="text-primary">✓</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {isLoading ? (
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="h-72 animate-pulse rounded-card bg-surface-sub" />
          ))}
        </div>
      ) : isError ? (
        <p className="mt-10 text-center text-small text-text-sub">
          북마크를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
      ) : items.length === 0 ? (
        // ⑤ 0건
        <div className="mt-20 flex flex-col items-center gap-4 text-center">
          <span className="text-text-disabled">
            <BookmarkIcon filled={false} />
          </span>
          <p className="text-body text-text-sub">북마크한 공모전이 없어요</p>
          <Link to="/">
            <Button variant="primary">공모전 둘러보기</Button>
          </Link>
        </div>
      ) : (
        <>
          <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {items.map((c) => (
              // ④ 마감된 공모전은 뒤로 + 딤드 + [마감됨]
              <article
                key={c.contestId}
                className={[
                  'flex flex-col rounded-card border border-line bg-surface p-5',
                  c.isClosed ? 'opacity-50' : '',
                ].join(' ')}
              >
                <div className="flex items-start justify-between">
                  {c.isClosed ? (
                    <Badge variant="surface-sub">마감됨</Badge>
                  ) : (
                    <DDayBadge dDay={c.dDay} />
                  )}
                  <button
                    type="button"
                    aria-label="북마크 해제"
                    aria-pressed
                    onClick={() => unbookmark(c.contestId)}
                    className="cursor-pointer text-primary"
                  >
                    <BookmarkIcon filled />
                  </button>
                </div>
                <p className="mt-16 text-caption text-text-sub">{c.categoryName}</p>
                <h3 className="mt-1 line-clamp-2 text-body font-bold text-text">{c.title}</h3>
                <p className="mt-1 text-caption text-text-sub">주최/주관: {c.host}</p>
                <p className="mt-4 border-t border-divider pt-3 text-caption text-text-sub">
                  마감 {formatDate(c.deadline)}
                </p>
                <Link to={`/contests/${c.contestId}`} className="mt-3">
                  <Button variant="outline" size="sm" className="w-full">
                    {c.isClosed ? '마감된 공모전 보기' : '공모전 자세히 보기'}
                  </Button>
                </Link>
              </article>
            ))}
          </div>
          <p className="mt-4 text-caption text-text-sub">
            마감된 공모전도 북마크에 남아요. 북마크 아이콘을 눌러 해제할 수 있어요.
          </p>
        </>
      )}

      {removed !== null && toast.message && (
        <div className="fixed top-6 left-1/2 z-50 flex -translate-x-1/2 items-center gap-4 rounded-popover bg-text px-5 py-3.5 text-body text-surface shadow-pop">
          북마크에서 해제했어요
          <button
            type="button"
            className="cursor-pointer font-bold text-primary-soft underline"
            onClick={undo}
          >
            실행 취소
          </button>
        </div>
      )}
      {removed === null && <Toast message={toast.message} />}
    </MyPageLayout>
  )
}
