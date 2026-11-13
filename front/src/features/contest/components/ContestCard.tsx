import { useState, type MouseEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Badge } from '../../../components/ui/Badge'
import { Highlight } from '../../search/components/Highlight'
import { useIsLoggedIn, useToggleBookmark } from '../hooks'
import type { ContestCardItem } from '../types'

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
  // MAIN-06 ③: 비로그인 사용자가 북마크를 누르면 부르는 함수 (토스트는 페이지가 띄운다)
  onRequireLogin: () => void
  // SRCH-02 ③: 검색 결과에서 제목·주최의 검색어를 강조한다
  highlight?: string
}

function BookmarkButton({ contest, onRequireLogin }: ContestCardProps) {
  const toggleBookmark = useToggleBookmark()
  const isLoggedIn = useIsLoggedIn()

  const handleClick = () => {
    if (!isLoggedIn) {
      onRequireLogin()
      return
    }
    toggleBookmark.mutate({ contestId: contest.contestId, isBookmarked: contest.isBookmarked })
  }

  return (
    <button
      type="button"
      aria-label="북마크"
      aria-pressed={contest.isBookmarked}
      disabled={toggleBookmark.isPending}
      onClick={handleClick}
      className="absolute top-3 right-3 flex size-10 items-center justify-center rounded-full bg-surface shadow-pop disabled:cursor-not-allowed"
    >
      <svg
        viewBox="0 0 24 24"
        className={`size-5 ${contest.isBookmarked ? 'text-primary' : 'text-text-sub'}`}
        fill={contest.isBookmarked ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
      >
        <path d="M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9z" />
      </svg>
    </button>
  )
}

// MAIN-06 공모전 카드. 마감된 공모전은 MAIN-03 ③대로 「마감됨」 배지 + 어둡게.
export function ContestCard({ contest, onRequireLogin, highlight }: ContestCardProps) {
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
      className={`relative h-full cursor-pointer overflow-hidden rounded-card border border-line bg-surface ${contest.isClosed ? 'opacity-50' : ''}`}
    >
      {/* 키보드 이동·새 탭 열기는 포스터 링크로 한다. 제목·주최는 링크 밖에 둬서 드래그로 선택할 수 있다 */}
      <Link to={detailPath} aria-label={contest.title} draggable={false} className="block">
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
            <span className="absolute top-3 left-3">
              <Badge>마감됨</Badge>
            </span>
          )}
        </div>
      </Link>
      <div className="flex flex-col gap-2 p-6">
        <div className="flex items-center gap-2">
          <Badge variant="primary-soft">{contest.categoryName}</Badge>
          {!contest.isClosed && <DDayBadge dDay={contest.dDay} />}
        </div>
        <h3 className="line-clamp-2 text-subheading font-bold">
          <Highlight text={contest.title} keyword={highlight} />
        </h3>
        <p className="truncate text-small text-text-sub">
          <Highlight text={contest.host} keyword={highlight} />
        </p>
      </div>
      <BookmarkButton contest={contest} onRequireLogin={onRequireLogin} />
    </article>
  )
}
