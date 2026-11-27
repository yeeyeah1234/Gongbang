import axios from 'axios'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Modal } from '../components/ui/Modal'
import { Toast } from '../components/ui/Toast'
import { BookmarkIcon } from '../features/contest/components/BookmarkIcon'
import { DDayBadge, DefaultThumbnail } from '../features/contest/components/ContestCard'
import {
  useContestDetail,
  useIsLoggedIn,
  useToggleBookmark,
  bookmarkErrorMessage,
} from '../features/contest/hooks'
import { sanitizeDescription } from '../features/contest/sanitizeDescription'
import type { ContestDetail } from '../features/contest/types'
import { ContestRecruitingPosts } from '../features/post/components/ContestRecruitingPosts'
import { useContestRecruitingPosts } from '../features/post/hooks'
import { useTimedToast } from '../hooks/useTimedToast'

const PAGE_CLASS = 'mx-auto max-w-[1680px] px-4 py-8 md:px-6 md:py-12 lg:px-12 xl:px-[120px]'

// 2026-12-06 → 2026.12.06
function formatDate(date: string | null) {
  return date ? date.replaceAll('-', '.') : ''
}

// MAIN-02 ④: [바로가기] 주소가 없어 비활성일 때 마우스를 올리면 보이는 안내
const NO_HOMEPAGE_MESSAGE = '바로가기 주소가 없는 공모전입니다.'

function errorMessageOf(error: unknown) {
  if (axios.isAxiosError(error) && error.response?.data?.error?.message) {
    return error.response.data.error.message as string
  }
  // MAIN-02 ⑧
  return '공모전을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
}

function Poster({ contest }: { contest: ContestDetail }) {
  const [imageFailed, setImageFailed] = useState(false)
  // MAIN-02 ③: 포스터가 없거나 오류면 기본 이미지
  return contest.thumbnailUrl && !imageFailed ? (
    <img
      src={contest.thumbnailUrl}
      alt={`${contest.title} 포스터`}
      onError={() => setImageFailed(true)}
      className="size-full object-cover"
    />
  ) : (
    <DefaultThumbnail />
  )
}

// 넓은 화면에서 오른쪽 칸이 GNB 아래(top-24, 96px)에 붙어 따라올 때, 칸 전체가 화면 안에 들어가는지 본다.
// 「팀원 모집 중」 카드(MAIN-02 ⑨)까지 붙어 화면보다 길어지면 아래가 잘려 볼 수 없으므로 따라오지 않게 한다 (#180).
const STICKY_TOP_PX = 96
const STICKY_BOTTOM_GAP_PX = 24

function useFitsViewport<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [fits, setFits] = useState(true)
  useEffect(() => {
    const element = ref.current
    if (!element) return
    const check = () =>
      setFits(element.offsetHeight + STICKY_TOP_PX + STICKY_BOTTOM_GAP_PX <= window.innerHeight)
    check()
    const observer = new ResizeObserver(check)
    observer.observe(element)
    window.addEventListener('resize', check)
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', check)
    }
  })
  return [ref, fits] as const
}

function DetailSkeleton() {
  return (
    <main className={PAGE_CLASS} aria-busy="true">
      <div className="flex flex-col gap-10 lg:flex-row">
        <div className="flex-1 space-y-4">
          <div className="h-6.5 w-40 animate-pulse rounded-lg bg-surface-sub" />
          <div className="h-10 w-3/4 animate-pulse rounded-lg bg-surface-sub" />
          <div className="h-60 animate-pulse rounded-lg bg-surface-sub" />
        </div>
        <div className="order-first mx-auto aspect-3/4 w-full max-w-100 animate-pulse rounded-card bg-surface-sub lg:order-none lg:w-100 lg:max-w-none" />
      </div>
    </main>
  )
}

// MAIN-02 공모전 상세
export default function ContestDetailPage() {
  const contestId = Number(useParams().contestId)
  const { data: contest, isPending, isError, error } = useContestDetail(contestId)
  const isLoggedIn = useIsLoggedIn()
  const toggleBookmark = useToggleBookmark()
  const toast = useTimedToast()
  const [loginModalOpen, setLoginModalOpen] = useState(false)
  // MAIN-02 ⑨: 이 공모전의 지원할 수 있는 모집글. 불러오는 중·실패·0개면 카드를 그리지 않는다 (지금 화면과 같음)
  const recruiting = useContestRecruitingPosts(contestId)
  const recruitingPosts = recruiting.data?.items ?? []
  const [asideRef, asideFits] = useFitsViewport<HTMLElement>()

  const safeDescription = useMemo(
    () => (contest?.description ? sanitizeDescription(contest.description, contest.sourceUrl) : ''),
    [contest],
  )

  if (isPending) return <DetailSkeleton />
  if (isError) {
    return (
      <main className={PAGE_CLASS}>
        <p className="py-12 text-center text-body text-text-sub">{errorMessageOf(error)}</p>
      </main>
    )
  }

  // MAIN-02 ④ [북마크]: 회원만 켜고 끌 수 있고, 비회원은 「로그인이 필요한 서비스입니다」 모달
  const handleBookmark = () => {
    if (!isLoggedIn) {
      setLoginModalOpen(true)
      return
    }
    // 요청이 끝나기 전에 다시 누르면 무시한다 (화면은 이미 바뀌어 있음)
    if (toggleBookmark.isPending) return
    toggleBookmark.mutate(
      { contestId: contest.contestId, isBookmarked: contest.isBookmarked },
      {
        onError: (error) => {
          const message = bookmarkErrorMessage(error, contest.isBookmarked)
          if (message) toast.show(message)
        },
      },
    )
  }

  // MAIN-02 ④ [복사]: 상세 페이지 링크를 복사하고 토스트
  const handleCopyLink = async () => {
    await navigator.clipboard.writeText(window.location.href)
    toast.show('링크를 복사했어요')
  }

  // MAIN-02 ④ 버튼. [바로가기]는 위비티가 아닌 주최 측 원래 페이지로, 주소가 없으면 비활성
  const actionButtons = (
    <>
      {/* 비활성 버튼은 마우스 이벤트를 받지 않아서 안내 문구(title)는 감싼 span에 둔다 */}
      <span className="flex flex-1" title={contest.homepageUrl ? undefined : NO_HOMEPAGE_MESSAGE}>
        <Button
          variant="primary"
          className="flex-1"
          disabled={!contest.homepageUrl}
          onClick={() => {
            if (contest.homepageUrl) {
              window.open(contest.homepageUrl, '_blank', 'noopener,noreferrer')
            }
          }}
        >
          바로가기
        </Button>
      </span>
      <Button variant="outline" aria-pressed={contest.isBookmarked} onClick={handleBookmark}>
        <span
          className={`inline-flex items-center gap-1.5 ${contest.isBookmarked ? 'text-primary' : ''}`}
        >
          <BookmarkIcon filled={contest.isBookmarked} />
          북마크
        </span>
      </Button>
      <Button variant="outline" onClick={handleCopyLink}>
        복사
      </Button>
    </>
  )

  return (
    // 768px 미만은 아래 고정 버튼 줄(높이 약 68px)에 본문 끝이 가리지 않게 아래 여백을 더 둔다
    <main className={`${PAGE_CLASS} max-md:pb-28`}>
      <title>{`${contest.title} | 공방`}</title>
      <div className="flex flex-col gap-8 lg:flex-row lg:items-start lg:gap-10">
        {/* 1024px 미만에서는 article을 contents로 풀어 「제목·기간 → 포스터·버튼 → 본문」 순서로 놓는다 (#117) */}
        <article className="contents lg:block lg:min-w-0 lg:flex-1">
          <header className="order-1 lg:order-none">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="primary-soft">{contest.categoryName}</Badge>
              {/* MAIN-02 ⑥: 마감된 공모전은 「마감됨」 */}
              {contest.isClosed ? <Badge>마감됨</Badge> : <DDayBadge dDay={contest.dDay} />}
            </div>
            <h1 className="mt-4 text-heading font-bold break-keep md:text-title">
              {contest.title}
            </h1>

            {/* MAIN-02 ②: 주최/주관, 접수기간 */}
            <dl className="mt-6 grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-body">
              <dt className="text-text-sub">주최/주관</dt>
              <dd>{contest.host}</dd>
              <dt className="text-text-sub">접수기간</dt>
              <dd>
                {formatDate(contest.startDate)} ~ {formatDate(contest.deadline)}
              </dd>
            </dl>
          </header>

          {/* MAIN-02 ②: 크롤링한 상세 본문 (DOMPurify로 정리한 HTML) */}
          <section className="order-3 border-t border-divider pt-8 lg:order-none lg:mt-10 lg:pt-10">
            <div
              className="text-body-lg break-words [&_a]:text-primary [&_a]:underline [&_img]:mx-auto [&_img]:my-4 [&_img]:h-auto [&_img]:max-w-full"
              dangerouslySetInnerHTML={{ __html: safeDescription }}
            />

            {/* MAIN-02 ⑦: 출처와 안내 (CRW-01 ⑨) */}
            <div className="mt-10 rounded-card bg-surface-sub p-6 text-small text-text-sub">
              <p>
                출처:{' '}
                <a
                  href={contest.sourceUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-primary underline"
                >
                  위비티
                </a>
              </p>
              <p className="mt-1">
                공모전 정보는 참고용이에요. 정확한 일정과 내용은 주최사 홈페이지에서 확인해 주세요.
              </p>
            </div>
          </section>
        </article>

        {/* MAIN-02 ③: 포스터와 조회수. 넓은 화면에서는 본문을 내려도 GNB(72px) 아래에 고정된다.
            1024px 미만에서는 긴 본문 아래로 밀리지 않게 제목과 본문 사이에, 최대 400px(휴대폰 288px)로 가운데 둔다 (#117)
            ⑨ 「팀원 모집 중」 카드는 포스터 카드 아래에 두고 칸 전체가 함께 따라온다. 화면보다 길면 따라오지 않는다 (#180) */}
        <aside
          ref={asideRef}
          className={`order-2 flex w-full flex-col gap-6 lg:order-none lg:w-100 lg:shrink-0 lg:self-start ${asideFits ? 'lg:sticky lg:top-24' : ''}`}
        >
          {/* 포스터 카드만 휴대폰 288px·태블릿 400px로 줄이고, 모집글 카드는 가로를 꽉 채운다 (#180) */}
          <div className="mx-auto w-full max-w-72 overflow-hidden rounded-card border border-line bg-surface sm:max-w-100 lg:max-w-none">
            <div className="aspect-3/4 bg-surface-sub">
              <Poster contest={contest} />
            </div>
            <p className="px-4 py-4 text-small text-text-sub md:px-6 md:pb-0">
              조회수 {contest.viewCount.toLocaleString()}
            </p>

            {/* MAIN-02 ④ 버튼. 768px 미만(휴대폰)에서는 화면 아래 고정 줄로 옮긴다 */}
            <div className="hidden gap-2 p-6 pt-4 md:flex">{actionButtons}</div>
          </div>

          {recruitingPosts.length > 0 && (
            <ContestRecruitingPosts
              contestId={contestId}
              posts={recruitingPosts}
              totalCount={recruiting.data?.totalCount ?? recruitingPosts.length}
            />
          )}
        </aside>
      </div>

      {/* 휴대폰: 본문을 내려도 바로가기·북마크·복사를 누를 수 있게 화면 아래에 고정 (#117 2차) */}
      <div className="fixed inset-x-0 bottom-0 z-20 flex gap-2 border-t border-divider bg-surface px-4 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))] md:hidden">
        {actionButtons}
      </div>

      <Modal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        title="로그인이 필요한 서비스입니다"
      />
      <Toast message={toast.message} />
    </main>
  )
}
