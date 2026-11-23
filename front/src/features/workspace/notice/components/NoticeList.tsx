import { useEffect, useRef } from 'react'
import { Button } from '../../../../components/ui/Button'
import type { useNoticeList } from '../hooks'
import type { NoticeListItemData } from '../types'
import { NoticeListItem } from './NoticeListItem'
import { NoticeCreateButton, NoticeError, NoticeSkeleton } from './NoticeStates'

interface NoticeListProps {
  query: ReturnType<typeof useNoticeList>
  items: NoticeListItemData[]
  basePath: string
  selectedId: number | null
  busy: boolean
  // 팀장일 때만 넘긴다
  onCreate?: () => void
}

// 끝에서 한 칸쯤 남았을 때부터 부른다. 0이면 몇 px만 덜 내려도 안 불러온다
const LOAD_MORE_MARGIN = '0px 0px 120px 0px'

// NTC-01 왼쪽 목록 (340px). 끝까지 내리면 10개씩 더 불러온다
export function NoticeList({
  query,
  items,
  basePath,
  selectedId,
  busy,
  onCreate,
}: NoticeListProps) {
  const {
    data,
    dataUpdatedAt,
    isError,
    isFetching,
    hasNextPage,
    isFetchNextPageError,
    fetchNextPage,
    refetch,
  } = query
  const scrollRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const sentinel = sentinelRef.current
    // 다른 조회 중이면 기다리고, 실패했으면 「다시 시도」로만 다시 부른다
    if (!sentinel || !hasNextPage || isFetching || isFetchNextPageError) return
    const observer = new IntersectionObserver(
      (entries) => {
        // 여러 번 바뀐 것이 묶여 오므로 마지막 값이 지금 상태
        if (entries[entries.length - 1].isIntersecting) void fetchNextPage({ cancelRefetch: false })
      },
      // 기준은 창이 아니라 목록 칸: 목록 아래쪽이 창 밖에 있어도 끝까지 내리면 부른다
      { root: scrollRef.current, rootMargin: LOAD_MORE_MARGIN },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
    // dataUpdatedAt: observer는 보임/안 보임이 바뀔 때만 알리고, 응답이 빠르면 isFetching=true가 렌더되지 않는다.
    // 쪽이 붙을 때마다 다시 감시해야 감지 지점이 계속 보일 때 이어서 부른다
  }, [hasNextPage, isFetching, isFetchNextPageError, fetchNextPage, dataUpdatedAt])

  return (
    <aside
      aria-label="공지 목록"
      className="flex shrink-0 flex-col border-b border-line lg:min-h-0 lg:w-[340px] lg:border-r lg:border-b-0"
    >
      {onCreate && (
        <div className="flex shrink-0 flex-col items-start gap-2 border-b border-divider p-4">
          <NoticeCreateButton disabled={busy} onClick={onCreate} />
          <p className="text-small text-text-sub">작성 버튼은 팀장에게만 보여요</p>
        </div>
      )}
      <div
        ref={scrollRef}
        className="relative max-h-80 min-h-0 overflow-y-auto p-2 lg:max-h-none lg:flex-1"
      >
        {data ? (
          <>
            <ul className="flex flex-col gap-1">
              {items.map((notice) => (
                <NoticeListItem
                  key={notice.noticeId}
                  notice={notice}
                  to={`${basePath}/${notice.noticeId}`}
                  selected={notice.noticeId === selectedId}
                />
              ))}
            </ul>
            {isFetchNextPageError && (
              <div className="flex flex-col items-center gap-2 py-4">
                <p className="text-small text-text-sub">불러오지 못했어요</p>
                <Button size="sm" onClick={() => fetchNextPage()}>
                  다시 시도
                </Button>
              </div>
            )}
            <div ref={sentinelRef} aria-hidden="true" className="h-1" />
          </>
        ) : isError ? (
          <NoticeError onRetry={() => refetch()} />
        ) : (
          <NoticeSkeleton />
        )}
      </div>
    </aside>
  )
}
