import { useState, type MouseEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Badge } from '../../../components/ui/Badge'
import { Highlight } from '../../search/components/Highlight'
import { bookmarkErrorMessage, useIsLoggedIn, useToggleBookmark } from '../hooks'
import type { ContestCardItem } from '../types'
import { BookmarkIcon } from './BookmarkIcon'

// D-day 배지는 마감 7일 이내면 빨갛게 (STYLE_GUIDE 5-3)
const DDAY_EMPHASIS_DAYS = 7

export function DDayBadge({ dDay }: { dDay: number }) {
  return (
    <Badge variant={dDay <= DDAY_EMPHASIS_DAYS ? 'danger-soft' : 'surface-sub'}>
      {dDay === 0 ? 'D-Day' : `D-${dDay}`}
    </Badge>
  )
}

// MAIN-06 ①: 썸네일이 없거나 불러오지 못하면 기본 이미지 (로고 글자 칸)
export function DefaultThumbnail() {
  return (
    <div className="flex size-full items-center justify-center text-subheading font-bold text-text-disabled">
      공방
    </div>
  )
}

interface ContestCardProps {
  contest: ContestCardItem
  // MAIN-06 ③④: 비로그인 안내·북마크 실패 문구를 토스트로 띄우는 함수 (토스트는 페이지가 띄운다)
  onToast: (message: string) => void
  // SRCH-02 ③: 검색 결과에서 제목·주최의 검색어를 강조한다
  highlight?: string
}

function BookmarkButton({ contest, onToast }: ContestCardProps) {
  const toggleBookmark = useToggleBookmark()
  const isLoggedIn = useIsLoggedIn()

  const handleClick = () => {
    if (!isLoggedIn) {
      onToast('로그인이 필요한 서비스입니다')
      return
    }
    // 요청이 끝나기 전에 다시 누르면 무시한다 (화면은 이미 바뀌어 있음)
    if (toggleBookmark.isPending) return
    toggleBookmark.mutate(
      { contestId: contest.contestId, isBookmarked: contest.isBookmarked },
      {
        onError: (error) => {
          const message = bookmarkErrorMessage(error, contest.isBookmarked)
          if (message) onToast(message)
        },
      },
    )
  }

  return (
    <button
      type="button"
      aria-label="북마크"
      aria-pressed={contest.isBookmarked}
      onClick={handleClick}
      className={`absolute top-3 right-3 flex size-10 cursor-pointer items-center justify-center rounded-full bg-surface shadow-pop ${contest.isBookmarked ? 'text-primary' : 'text-text-sub'}`}
    >
      <BookmarkIcon filled={contest.isBookmarked} />
    </button>
  )
}

// MAIN-06 공모전 카드. 마감된 공모전은 MAIN-03 ③대로 「마감됨」 배지 + 어둡게.
// 640px 미만(휴대폰)에서는 한 칸 목록형: 왼쪽 작은 포스터 + 오른쪽 글 (#117 2차)
export function ContestCard({ contest, onToast, highlight }: ContestCardProps) {
  const navigate = useNavigate()
  const [imageFailed, setImageFailed] = useState(false)
  const showImage = contest.thumbnailUrl && !imageFailed
  const detailPath = `/contests/${contest.contestId}`

  // MAIN-06 ②: 카드 어디를 눌러도 상세(MAIN-02)로 간다.
  // 단, 링크·북마크 버튼은 자기 동작을 하고, 제목·주최 글자를 드래그로 선택한 직후에는 이동하지 않는다.
  const handleCardClick = (event: MouseEvent<HTMLElement>) => {
    if ((event.target as Element).closest('a, button')) return
    const selection = window.getSelection()
    if (selection && !selection.isCollapsed && event.currentTarget.contains(selection.anchorNode)) {
      return
    }
    navigate(detailPath)
  }

  return (
    <article
      onClick={handleCardClick}
      className={`relative flex h-full cursor-pointer overflow-hidden rounded-card border border-line bg-surface sm:block ${contest.isClosed ? 'opacity-50' : ''}`}
    >
      {/* 키보드 이동·새 탭 열기는 포스터 링크로 한다. 제목·주최는 링크 밖에 둬서 드래그로 선택할 수 있다 */}
      <Link
        to={detailPath}
        aria-label={contest.title}
        draggable={false}
        className="block w-24 shrink-0 sm:w-auto"
      >
        <div className="relative aspect-3/4 bg-surface-sub">
          {showImage ? (
            <img
              src={contest.thumbnailUrl ?? undefined}
              alt=""
              draggable={false}
              loading="lazy"
              onError={() => setImageFailed(true)}
              className="size-full object-cover"
            />
          ) : (
            <DefaultThumbnail />
          )}
          {contest.isClosed && (
            <span className="absolute top-2 left-2 sm:top-3 sm:left-3">
              <Badge>마감됨</Badge>
            </span>
          )}
        </div>
      </Link>
      {/* 휴대폰 목록형에서는 오른쪽 위 북마크 버튼 자리(pr-14)를 비운다 */}
      <div className="flex min-w-0 flex-1 flex-col gap-2 p-4 pr-14 sm:pr-4 md:p-6">
        {/* 좁은 카드에서는 분야·D-day 배지가 줄을 바꿔도 되게 */}
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="primary-soft">{contest.categoryName}</Badge>
          {!contest.isClosed && <DDayBadge dDay={contest.dDay} />}
        </div>
        <h3 className="line-clamp-2 text-body font-bold md:text-subheading">
          <Highlight text={contest.title} keyword={highlight} />
        </h3>
        <p className="truncate text-small text-text-sub">
          <Highlight text={contest.host} keyword={highlight} />
        </p>
      </div>
      <BookmarkButton contest={contest} onToast={onToast} />
    </article>
  )
}
