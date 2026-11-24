import axios from 'axios'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Toast } from '../components/ui/Toast'
import { ContestCard } from '../features/contest/components/ContestCard'
import type { ContestCardItem } from '../features/contest/types'
import { usePopularContests, useSearch } from '../features/search/hooks'
import type { SearchSort, SearchType } from '../features/search/types'
import { useTimedToast } from '../hooks/useTimedToast'

const PAGE_CLASS = 'mx-auto max-w-[1680px] px-4 py-8 md:px-6 md:py-12 lg:px-12 xl:px-[120px]'

// SRCH-02 ① 결과 유형 탭
const TABS: { value: SearchType; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'contest', label: '공모전' },
  { value: 'post', label: '커뮤니티' },
]

// SRCH-02 ② 정렬 기준
const SORTS: { value: SearchSort; label: string }[] = [
  { value: 'accuracy', label: '정확도순' },
  { value: 'latest', label: '최신순' },
  { value: 'deadline', label: '마감 임박순' },
]

function errorMessageOf(error: unknown) {
  if (axios.isAxiosError(error) && error.response?.data?.error?.message) {
    return error.response.data.error.message as string
  }
  // SRCH-02 ⑤
  return '검색 결과를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
}

function ContestGrid({
  contests,
  highlight,
  onToast,
}: {
  contests: ContestCardItem[]
  highlight?: string
  onToast: (message: string) => void
}) {
  return (
    <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 sm:gap-4 md:grid-cols-3 md:gap-6 xl:grid-cols-4">
      {contests.map((contest) => (
        <li key={contest.contestId}>
          <ContestCard contest={contest} highlight={highlight} onToast={onToast} />
        </li>
      ))}
    </ul>
  )
}

// SRCH-02 ④: 결과가 없으면 안내 문구와 최근 인기 공모전을 추천한다
function NoResults({ keyword, onToast }: { keyword: string; onToast: (message: string) => void }) {
  const { data: popular = [] } = usePopularContests(true)
  return (
    <div>
      <p className="py-12 text-center text-body text-text-sub">
        입력하신 '{keyword}'에 대한 검색 결과가 없습니다.
      </p>
      {popular.length > 0 && (
        <section>
          <h2 className="mb-6 text-heading font-bold">최근 인기 공모전</h2>
          <ContestGrid contests={popular} onToast={onToast} />
        </section>
      )}
    </div>
  )
}

function SearchResults({
  keyword,
  type,
  sort,
  includeClosed,
  onToast,
}: {
  keyword: string
  type: SearchType
  sort: SearchSort
  includeClosed: boolean
  onToast: (message: string) => void
}) {
  const { data, isPending, isError, error, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useSearch({ keyword, type, sort, includeClosed })
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 아래로 내리면 다음 20개를 불러온다 (메인 목록과 같은 방식)
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasNextPage) return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && !isFetchingNextPage) fetchNextPage()
    })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  if (isPending) {
    return (
      <ul
        aria-busy="true"
        className="grid grid-cols-1 gap-3 sm:grid-cols-2 sm:gap-4 md:grid-cols-3 md:gap-6 xl:grid-cols-4"
      >
        {Array.from({ length: 4 }, (_, index) => (
          <li
            key={index}
            className="h-32 animate-pulse rounded-card bg-surface-sub sm:aspect-3/4 sm:h-auto"
          />
        ))}
      </ul>
    )
  }
  if (isError) {
    return <p className="py-12 text-center text-body text-text-sub">{errorMessageOf(error)}</p>
  }

  const contests = data.pages.flatMap((page) => page.contests.items)
  // TODO(확인 필요): 모집글·작성자 결과(SRCH-05 ①)는 모집글 도메인이 생기면 「커뮤니티」 영역으로 함께 보여 준다
  const postCount = data.pages[0].posts.totalCount
  if (contests.length === 0 && postCount === 0) {
    return <NoResults keyword={keyword} onToast={onToast} />
  }

  return (
    <>
      <ContestGrid contests={contests} highlight={keyword} onToast={onToast} />
      <div ref={sentinelRef} aria-hidden="true" className="h-1" />
    </>
  )
}

// SRCH-02 통합 검색 결과 (/search?keyword=)
export default function SearchPage() {
  const keyword = (useSearchParams()[0].get('keyword') ?? '').trim()
  const [type, setType] = useState<SearchType>('all')
  const [sort, setSort] = useState<SearchSort>('accuracy')
  const [includeClosed, setIncludeClosed] = useState(false)
  const toast = useTimedToast()

  return (
    <main className={PAGE_CLASS}>
      <title>검색 결과 | 공방</title>

      <div className="flex flex-wrap items-end justify-between gap-4 border-b border-divider">
        {/* SRCH-02 ①: 탭은 하나만 선택 (기본 [전체]) */}
        <div role="tablist" aria-label="검색 결과 유형" className="flex gap-6 md:gap-10">
          {TABS.map((tab) => {
            const isActive = tab.value === type
            return (
              <button
                key={tab.value}
                type="button"
                role="tab"
                aria-selected={isActive}
                onClick={() => setType(tab.value)}
                className={`-mb-px h-13 border-b-2 text-subheading ${
                  isActive
                    ? 'border-primary font-bold text-text'
                    : 'border-transparent text-text-sub hover:text-text'
                }`}
              >
                {tab.label}
              </button>
            )
          })}
        </div>

        <div className="mb-3 flex flex-wrap items-center gap-4 md:gap-6">
          {/* SRCH-02 ①: 기본은 해제. 켜면 마감된 공모전도 「마감됨」 배지와 어둡게 보여 준다 */}
          <label className="flex cursor-pointer items-center gap-2 text-body">
            <input
              type="checkbox"
              checked={includeClosed}
              onChange={(event) => setIncludeClosed(event.target.checked)}
              className="size-5 cursor-pointer rounded accent-primary"
            />
            마감된 공모전 포함
          </label>

          {/* SRCH-02 ②: 고르면 바로 다시 정렬 */}
          <select
            value={sort}
            onChange={(event) => setSort(event.target.value as SearchSort)}
            aria-label="정렬 기준"
            className="h-11 w-39 cursor-pointer rounded-full border border-line-strong bg-surface px-5 text-body hover:bg-bg"
          >
            {SORTS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <section className="mt-6 md:mt-8">
        <SearchResults
          keyword={keyword}
          type={type}
          sort={sort}
          includeClosed={includeClosed}
          onToast={toast.show}
        />
      </section>
      <Toast message={toast.message} />
    </main>
  )
}
