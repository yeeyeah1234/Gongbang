import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { Button } from '../../components/ui/Button'
import { useIsLoggedIn } from '../../features/auth/store'
import { CommunityTabs } from '../../features/board/components/CommunityTabs'
import { LoginRequiredModal } from '../../features/post/components/LoginRequiredModal'
import { PostPagination } from '../../features/post/components/PostPagination'
import { PostTable } from '../../features/post/components/PostTable'
import { usePostList } from '../../features/post/hooks'

const WRITE_PATH = '/posts/new'

// 주소의 ?page=는 1부터. 잘못된 값이면 첫 페이지.
function readPage(value: string | null) {
  const page = Number(value)
  return Number.isInteger(page) && page >= 1 ? page - 1 : 0
}

// PST-001 커뮤니티 모집글 목록 / Figma WF-01. 비회원도 본다 (⑤).
export default function PostListPage() {
  const navigate = useNavigate()
  const isLoggedIn = useIsLoggedIn()
  const [searchParams, setSearchParams] = useSearchParams()
  const [loginModalOpen, setLoginModalOpen] = useState(false)
  // 페이지를 주소에 남겨, 상세에 갔다가 뒤로 오면 보던 페이지로 돌아온다
  const page = readPage(searchParams.get('page'))
  const { data, isLoading, isError, refetch } = usePostList(page)

  const totalPages = data ? Math.max(1, Math.ceil(data.totalCount / data.size)) : 1
  const isEmpty = data !== undefined && data.totalCount === 0

  // ⑥: 회원은 작성 화면으로, 비회원은 「로그인이 필요해요」 모달
  const write = () => {
    if (isLoggedIn) navigate(WRITE_PATH)
    else setLoginModalOpen(true)
  }

  const changePage = (next: number) => {
    setSearchParams(next === 0 ? {} : { page: String(next + 1) })
    window.scrollTo({ top: 0 })
  }

  return (
    <main className="mx-auto max-w-[1680px] px-4 py-8 md:px-6 md:py-12 lg:px-12 xl:px-[120px]">
      <title>커뮤니티 | 공방</title>
      {/* ① 위쪽 탭으로 커뮤니티 게시판(BRD-001)과 오간다 */}
      <CommunityTabs />
      <div className="mt-8 flex items-center justify-between gap-6">
        <h1 className="text-title font-bold text-text">팀원 모집</h1>
        {/* 빈 화면에서는 안내 아래 버튼 하나만 둔다 (한 화면에 Primary 하나) */}
        {!isEmpty && (
          <Button variant="primary" onClick={write}>
            글쓰기
          </Button>
        )}
      </div>

      <section className="mt-8" aria-label="모집글 목록">
        {isLoading ? (
          <div className="flex flex-col gap-2" aria-busy="true">
            <div className="h-14 animate-pulse rounded-input bg-surface-sub" />
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="h-[60px] animate-pulse rounded-input bg-surface-sub" />
            ))}
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center gap-4 py-24">
            <p className="text-body text-text-sub">모집글을 불러오지 못했어요.</p>
            <Button variant="outline" onClick={() => void refetch()}>
              다시 불러오기
            </Button>
          </div>
        ) : isEmpty ? (
          // ⑧ 글이 없을 때
          <div className="flex flex-col items-center gap-4 py-24">
            <p className="text-body text-text-sub">아직 모집글이 없어요</p>
            <Button variant="primary" onClick={write}>
              글쓰기
            </Button>
          </div>
        ) : (
          data && (
            <>
              <PostTable posts={data.items} />
              <PostPagination page={page} totalPages={totalPages} onChange={changePage} />
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
