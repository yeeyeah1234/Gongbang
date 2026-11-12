import { useEffect, useRef } from 'react'
import { useContestList } from '../hooks'
import type { ContestSort } from '../types'
import { ContestCard } from './ContestCard'

interface ContestListProps {
  sort: ContestSort
  category?: string
  onRequireLogin: () => void
}

function StatusMessage({ children }: { children: string }) {
  return <p className="py-12 text-center text-body text-text-sub">{children}</p>
}

// MAIN-01 공모전 목록 + MAIN-03 ① 로딩·빈 화면. 아래로 내리면 다음 20개를 불러온다(무한 스크롤).
export function ContestList({ sort, category, onRequireLogin }: ContestListProps) {
  const { data, isPending, isError, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useContestList({ sort, category })
  const sentinelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasNextPage) return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && !isFetchingNextPage) fetchNextPage()
    })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  if (isPending) return <StatusMessage>현재 진행 중인 공모전을 불러오는 중입니다.</StatusMessage>
  // TODO(확인 필요): 목록을 불러오지 못했을 때의 문구가 기능정의서에 없다. 정해지면 바꾼다.
  if (isError) return <StatusMessage>공모전을 불러오지 못했어요.</StatusMessage>

  const contests = data.pages.flatMap((page) => page.items)
  if (contests.length === 0) return <StatusMessage>조건에 맞는 공모전이 없습니다.</StatusMessage>

  return (
    <>
      <ul className="grid grid-cols-2 gap-6 md:grid-cols-3 xl:grid-cols-4">
        {contests.map((contest) => (
          <li key={contest.contestId}>
            <ContestCard contest={contest} onRequireLogin={onRequireLogin} />
          </li>
        ))}
      </ul>
      <div ref={sentinelRef} aria-hidden="true" className="h-1" />
    </>
  )
}
