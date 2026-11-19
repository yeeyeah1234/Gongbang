import { useEffect, useRef, useState } from 'react'
import { useContestList } from '../hooks'
import type { ContestSort } from '../types'
import { ContestCard } from './ContestCard'

interface ContestListProps {
  sort: ContestSort
  category?: string
  onToast: (message: string) => void
}

function StatusMessage({ children }: { children: string }) {
  return <p className="py-12 text-center text-body text-text-sub">{children}</p>
}

// MAIN-03 ④·STYLE_GUIDE 5-10: 로딩이 1초를 넘으면 스켈레톤을 보여 준다
const SKELETON_DELAY_MS = 1000
const SKELETON_CARD_COUNT = 8

function ContestListSkeleton() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => setVisible(true), SKELETON_DELAY_MS)
    return () => clearTimeout(timer)
  }, [])

  if (!visible) return null

  return (
    <ul aria-hidden="true" className="grid grid-cols-2 gap-6 md:grid-cols-3 xl:grid-cols-4">
      {Array.from({ length: SKELETON_CARD_COUNT }, (_, index) => (
        <li key={index} className="overflow-hidden rounded-card border border-line bg-surface">
          <div className="aspect-3/4 animate-pulse bg-surface-sub" />
          <div className="flex flex-col gap-2 p-6">
            <div className="h-6 w-20 animate-pulse rounded-lg bg-surface-sub" />
            <div className="h-6 w-full animate-pulse rounded-lg bg-surface-sub" />
            <div className="h-5 w-2/3 animate-pulse rounded-lg bg-surface-sub" />
          </div>
        </li>
      ))}
    </ul>
  )
}

// MAIN-01 공모전 목록 + MAIN-03 ① 로딩·빈 화면. 아래로 내리면 다음 20개를 불러온다(무한 스크롤).
export function ContestList({ sort, category, onToast }: ContestListProps) {
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

  if (isPending) {
    return (
      <>
        <StatusMessage>현재 진행 중인 공모전을 불러오는 중입니다.</StatusMessage>
        <ContestListSkeleton />
      </>
    )
  }
  // MAIN-03 ④: 실패하면 TanStack Query 기본값대로 3번 더 시도한 뒤 이 문구를 보여 준다.
  if (isError) {
    return <StatusMessage>공모전을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</StatusMessage>
  }

  const contests = data.pages.flatMap((page) => page.items)
  if (contests.length === 0) return <StatusMessage>조건에 맞는 공모전이 없습니다.</StatusMessage>

  return (
    <>
      <ul className="grid grid-cols-2 gap-6 md:grid-cols-3 xl:grid-cols-4">
        {contests.map((contest) => (
          <li key={contest.contestId}>
            <ContestCard contest={contest} onToast={onToast} />
          </li>
        ))}
      </ul>
      <div ref={sentinelRef} aria-hidden="true" className="h-1" />
    </>
  )
}
