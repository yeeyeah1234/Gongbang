import { useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { useMemo, useState } from 'react'
import { useParams } from 'react-router'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Modal } from '../components/ui/Modal'
import { Toast } from '../components/ui/Toast'
import { DDayBadge, DefaultThumbnail } from '../features/contest/components/ContestCard'
import { useContestDetail, useIsLoggedIn, useToggleBookmark } from '../features/contest/hooks'
import { sanitizeDescription } from '../features/contest/sanitizeDescription'
import type { ContestDetail } from '../features/contest/types'
import { useTimedToast } from '../hooks/useTimedToast'

const PAGE_CLASS = 'mx-auto max-w-[1680px] px-6 py-12 lg:px-12 xl:px-[120px]'

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

function DetailSkeleton() {
  return (
    <main className={PAGE_CLASS} aria-busy="true">
      <div className="flex flex-col gap-10 lg:flex-row">
        <div className="flex-1 space-y-4">
          <div className="h-6.5 w-40 animate-pulse rounded-lg bg-surface-sub" />
          <div className="h-10 w-3/4 animate-pulse rounded-lg bg-surface-sub" />
          <div className="h-60 animate-pulse rounded-lg bg-surface-sub" />
        </div>
        <div className="aspect-3/4 w-full animate-pulse rounded-card bg-surface-sub lg:w-100" />
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
  const queryClient = useQueryClient()
  const toast = useTimedToast()
  const [loginModalOpen, setLoginModalOpen] = useState(false)

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
    toggleBookmark.mutate(
      { contestId: contest.contestId, isBookmarked: contest.isBookmarked },
      {
        onSuccess: (result) =>
          queryClient.setQueryData<ContestDetail>(['contest-detail', contest.contestId], (old) =>
            old ? { ...old, isBookmarked: result.isBookmarked } : old,
          ),
      },
    )
  }

  // MAIN-02 ④ [복사]: 상세 페이지 링크를 복사하고 토스트
  const handleCopyLink = async () => {
    await navigator.clipboard.writeText(window.location.href)
    toast.show('링크를 복사했어요')
  }

  return (
    <main className={PAGE_CLASS}>
      <title>{`${contest.title} | 공방`}</title>
      <div className="flex flex-col gap-10 lg:flex-row lg:items-start">
        <article className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="primary-soft">{contest.categoryName}</Badge>
            {/* MAIN-02 ⑥: 마감된 공모전은 「마감됨」 */}
            {contest.isClosed ? <Badge>마감됨</Badge> : <DDayBadge dDay={contest.dDay} />}
          </div>
          <h1 className="mt-4 text-title font-bold break-keep">{contest.title}</h1>

          {/* MAIN-02 ②: 주최/주관, 접수기간 */}
          <dl className="mt-6 grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-body">
            <dt className="text-text-sub">주최/주관</dt>
            <dd>{contest.host}</dd>
            <dt className="text-text-sub">접수기간</dt>
            <dd>
              {formatDate(contest.startDate)} ~ {formatDate(contest.deadline)}
            </dd>
          </dl>

          {/* MAIN-02 ②: 크롤링한 상세 본문 (DOMPurify로 정리한 HTML) */}
          <section className="mt-10 border-t border-divider pt-10">
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

        {/* MAIN-02 ③: 포스터와 조회수. 넓은 화면에서는 본문을 내려도 GNB(72px) 아래에 고정된다 */}
        <aside className="w-full lg:sticky lg:top-24 lg:w-100 lg:shrink-0 lg:self-start">
          <div className="overflow-hidden rounded-card border border-line bg-surface">
            <div className="aspect-3/4 bg-surface-sub">
              <Poster contest={contest} />
            </div>
            <p className="px-6 pt-4 text-small text-text-sub">
              조회수 {contest.viewCount.toLocaleString()}
            </p>

            {/* MAIN-02 ④ 버튼. [바로가기]는 위비티가 아닌 주최 측 원래 페이지로, 주소가 없으면 비활성 */}
            <div className="flex gap-2 p-6 pt-4">
              {/* 비활성 버튼은 마우스 이벤트를 받지 않아서 안내 문구(title)는 감싼 span에 둔다 */}
              <span
                className="flex flex-1"
                title={contest.homepageUrl ? undefined : NO_HOMEPAGE_MESSAGE}
              >
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
              <Button
                variant="outline"
                aria-pressed={contest.isBookmarked}
                loading={toggleBookmark.isPending}
                onClick={handleBookmark}
              >
                북마크
              </Button>
              <Button variant="outline" onClick={handleCopyLink}>
                복사
              </Button>
            </div>
          </div>
        </aside>
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
