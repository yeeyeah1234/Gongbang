import { Link } from 'react-router'
import { formatPostDate } from '../../post/format'
import { usePopularBoardPosts } from '../hooks'
import type { BoardCategory } from '../types'
import { CategoryBadge } from './BoardTable'

// BRD-008 인기글: 최근 7일, 좋아요 1개 이상, 최대 3개. 없거나 불러오지 못하면 칸을 숨긴다 (③)
export function PopularPosts({ category }: { category: BoardCategory | null }) {
  const { data } = usePopularBoardPosts(category)
  if (!data || data.length === 0) return null

  return (
    <section aria-labelledby="board-popular-title" className="mt-8">
      <h2 id="board-popular-title" className="text-subheading font-bold text-text">
        인기글
      </h2>
      <ol className="mt-4 grid gap-4 md:grid-cols-3">
        {data.map((post) => (
          <li key={post.boardPostId} className="min-w-0">
            <Link
              to={`/board/${post.boardPostId}`}
              className="flex h-full flex-col gap-3 rounded-card border border-line bg-surface p-5 hover:bg-bg focus-visible:outline-2 focus-visible:outline-primary"
            >
              <div className="flex items-center gap-2">
                <CategoryBadge category={post.category} />
                {post.contest && (
                  <span className="min-w-0 truncate text-small text-text-sub">
                    {post.contest.title}
                  </span>
                )}
              </div>
              <p className="line-clamp-2 text-body-lg font-bold break-words text-text">
                {post.title}
              </p>
              <p className="mt-auto flex gap-3 text-small text-text-sub">
                <span className="min-w-0 truncate">{post.author.nickname ?? '탈퇴한 사용자'}</span>
                <span>{formatPostDate(post.createdAt)}</span>
                <span>조회 {post.viewCount}</span>
                <span>좋아요 {post.likeCount}</span>
              </p>
            </Link>
          </li>
        ))}
      </ol>
    </section>
  )
}
