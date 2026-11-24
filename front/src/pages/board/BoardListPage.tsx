import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../components/ui/Button'
import { Chip } from '../../components/ui/Chip'
import { useIsLoggedIn } from '../../features/auth/store'
import { BoardTable } from '../../features/board/components/BoardTable'
import { CommunityTabs } from '../../features/board/components/CommunityTabs'
import { PopularPosts } from '../../features/board/components/PopularPosts'
import { useBoardList } from '../../features/board/hooks'
import { BOARD_CATEGORIES, BOARD_CATEGORY_LABELS } from '../../features/board/types'
import type { BoardCategory, BoardSort } from '../../features/board/types'
import { LoginRequiredModal } from '../../features/post/components/LoginRequiredModal'
import { PostPagination } from '../../features/post/components/PostPagination'

const WRITE_PATH = '/board/new'

const SORTS: { value: BoardSort; label: string }[] = [
  { value: 'latest', label: '최신순' },
  { value: 'popular', label: '인기순' },
]

// 주소의 ?page=는 1부터. 잘못된 값이면 첫 페이지.
function readPage(value: string | null) {
  const page = Number(value)
  return Number.isInteger(page) && page >= 1 ? page - 1 : 0
}

function readCategory(value: string | null): BoardCategory | null {
  return BOARD_CATEGORIES.find((category) => category === value) ?? null
}

// BRD-001 커뮤니티 게시판 목록. 비회원도 본다 (②).
// 말머리·정렬·페이지를 주소에 남겨, 상세에 갔다가 뒤로 오면 보던 목록으로 돌아온다.
export default function BoardListPage() {
  const navigate = useNavigate()
  const isLoggedIn = useIsLoggedIn()
  const [searchParams, setSearchParams] = useSearchParams()
  const [loginModalOpen, setLoginModalOpen] = useState(false)
  const category = readCategory(searchParams.get('category'))
  const sort: BoardSort = searchParams.get('sort') === 'popular' ? 'popular' : 'latest'
  const page = readPage(searchParams.get('page'))
  const { data, isLoading, isError, refetch } = useBoardList(category, sort, page)

  const totalPages = data ? Math.max(1, Math.ceil(data.totalCount / data.size)) : 1
  const isEmpty = data !== undefined && data.totalCount === 0

  // 기본값(전체·최신순·첫 페이지)은 주소에 적지 않는다
  const move = (next: { category?: BoardCategory | null; sort?: BoardSort; page?: number }) => {
    const nextCategory = next.category !== undefined ? next.category : category
    const nextSort = next.sort ?? sort
    const nextPage = next.page ?? 0
    const params: Record<string, string> = {}
    if (nextCategory) params.category = nextCategory
    if (nextSort !== 'latest') params.sort = nextSort
    if (nextPage > 0) params.page = String(nextPage + 1)
    setSearchParams(params)
  }

  // ⑥: 회원은 작성 화면으로, 비회원은 「로그인이 필요해요」 모달
  const write = () => {
    if (isLoggedIn) navigate(WRITE_PATH)
    else setLoginModalOpen(true)
  }

  return (
    <main className="mx-auto max-w-[1680px] px-6 py-12 lg:px-12 xl:px-[120px]">
      <title>커뮤니티 게시판 | 공방</title>
      <CommunityTabs />
      <div className="mt-8 flex items-center justify-between gap-6">
        <h1 className="text-title font-bold text-text">커뮤니티 게시판</h1>
        {!isEmpty && (
          <Button variant="primary" onClick={write}>
            글쓰기
          </Button>
        )}
      </div>

      {/* ③ 말머리 탭 */}
      <div role="group" aria-label="말머리" className="mt-6 flex flex-wrap gap-2">
        <Chip selected={category === null} onClick={() => move({ category: null })}>
          전체
        </Chip>
        {BOARD_CATEGORIES.map((value) => (
          <Chip key={value} selected={category === value} onClick={() => move({ category: value })}>
            {BOARD_CATEGORY_LABELS[value]}
          </Chip>
        ))}
      </div>

      {/* ⑩ 인기글 (BRD-008, 말머리 안에서) */}
      <PopularPosts category={category} />

      <section className="mt-10" aria-labelledby="board-list-title">
        <div className="mb-4 flex items-center justify-between gap-4">
          <h2 id="board-list-title" className="text-subheading font-bold text-text">
            {category ? BOARD_CATEGORY_LABELS[category] : '전체 글'}
          </h2>
          {/* ⑨ 정렬 */}
          <div role="group" aria-label="정렬" className="flex gap-1">
            {SORTS.map((option) => (
              <button
                key={option.value}
                type="button"
                aria-pressed={sort === option.value}
                onClick={() => move({ sort: option.value })}
                className={[
                  'h-9 cursor-pointer rounded-full px-3 text-small hover:bg-bg',
                  sort === option.value ? 'font-bold text-text' : 'text-text-sub',
                ].join(' ')}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        {isLoading ? (
          <div className="flex flex-col gap-2" aria-busy="true">
            <div className="h-14 animate-pulse rounded-input bg-surface-sub" />
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="h-[60px] animate-pulse rounded-input bg-surface-sub" />
            ))}
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center gap-4 py-24">
            <p className="text-body text-text-sub">글을 불러오지 못했어요.</p>
            <Button variant="outline" onClick={() => void refetch()}>
              다시 불러오기
            </Button>
          </div>
        ) : isEmpty ? (
          // ⑦ 글이 없을 때
          <div className="flex flex-col items-center gap-4 py-24">
            <p className="text-body text-text-sub">아직 글이 없어요</p>
            <Button variant="primary" onClick={write}>
              글쓰기
            </Button>
          </div>
        ) : (
          data && (
            <>
              <BoardTable posts={data.items} />
              <PostPagination
                page={page}
                totalPages={totalPages}
                onChange={(next) => {
                  move({ page: next })
                  window.scrollTo({ top: 0 })
                }}
              />
            </>
          )
        )}
      </section>

      <LoginRequiredModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        redirectTo={WRITE_PATH}
      />
    </main>
  )
}
