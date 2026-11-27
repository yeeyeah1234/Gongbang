import { Link } from 'react-router'
import { todayString } from '../postForm'
import type { PostListItem } from '../types'
import { StatusBadge } from './PostApplyPanel'

// 모집 종료일까지 남은 날 (D-14, 당일은 D-Day). 날짜만 비교한다 (서버·기기 모두 한국 시간 전제)
function dDayLabel(recruitEndDate: string, today: string = todayString()) {
  const days = Math.round((Date.parse(recruitEndDate) - Date.parse(today)) / 86_400_000)
  return days <= 0 ? 'D-Day' : `D-${days}`
}

/**
 * MAIN-02 ⑨ 공모전 상세 오른쪽 칸의 「팀원 모집 중」 카드.
 * 이 공모전에서 지금 지원할 수 있는 모집글을 최신 등록순으로 최대 3개 보여 주고, 제목줄에 전체 개수를 둔다.
 * 「더 보기」는 이 공모전 모집글 목록(PST-001 ⑩, 모집완료 포함)으로 간다.
 * 글이 없으면 부르는 쪽에서 이 카드를 그리지 않는다.
 */
export function ContestRecruitingPosts({
  contestId,
  posts,
  totalCount,
}: {
  contestId: number
  posts: PostListItem[]
  totalCount: number
}) {
  return (
    <section
      aria-labelledby="contest-recruiting-title"
      className="w-full overflow-hidden rounded-card border border-line bg-surface"
    >
      <div className="flex items-center justify-between gap-4 px-6 pt-5 pb-3">
        <h2 id="contest-recruiting-title" className="text-body font-bold text-text">
          팀원 모집 중 <span className="text-primary">{totalCount}</span>
        </h2>
        <Link
          to={`/posts?contestId=${contestId}`}
          className="shrink-0 rounded-input text-small text-primary outline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary"
        >
          더 보기
        </Link>
      </div>
      <ul>
        {posts.map((post) => (
          <li key={post.postId} className="border-t border-divider">
            <Link
              to={`/posts/${post.postId}`}
              className="block px-6 py-4 outline-offset-[-2px] transition-colors hover:bg-bg focus-visible:outline-2 focus-visible:outline-primary"
            >
              <StatusBadge status={post.status} recruitEndDate={post.recruitEndDate} />
              <p className="mt-2 truncate text-body font-bold text-text">{post.title}</p>
              <p className="mt-1 text-small text-text-sub">
                {post.author.nickname ?? '탈퇴한 사용자'} · {post.currentCount}/{post.targetCount}명
                · {dDayLabel(post.recruitEndDate)}
              </p>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
