import { useState } from 'react'
import { Toast } from '../components/ui/Toast'
import { CategoryFilter } from '../features/contest/components/CategoryFilter'
import { ContestList } from '../features/contest/components/ContestList'
import { SortSelect } from '../features/contest/components/SortSelect'
import type { ContestSort } from '../features/contest/types'
import { useTimedToast } from '../hooks/useTimedToast'

// 메인 화면: 진행 중인 공모전 목록 (MAIN-01·03·04·05·06)
export default function HomePage() {
  // MAIN-04 ②: 기본은 최신순 / MAIN-05 ①: 기본은 「전체」
  const [sort, setSort] = useState<ContestSort>('latest')
  const [category, setCategory] = useState<string>()
  const toast = useTimedToast()

  return (
    <main className="mx-auto max-w-[1680px] px-6 py-12 lg:px-12 xl:px-[120px]">
      <title>공모전 | 공방</title>
      <div className="flex items-start justify-between gap-6">
        <CategoryFilter selected={category} onSelect={setCategory} />
        <SortSelect value={sort} onChange={setSort} />
      </div>
      <section className="mt-8">
        <ContestList
          sort={sort}
          category={category}
          onRequireLogin={() => toast.show('로그인이 필요한 서비스입니다')}
        />
      </section>
      <Toast message={toast.message} />
    </main>
  )
}
