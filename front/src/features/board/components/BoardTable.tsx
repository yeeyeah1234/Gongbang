import { Link, useNavigate } from 'react-router'
import { Badge } from '../../../components/ui/Badge'
import { formatPostDate } from '../../post/format'
import { BOARD_CATEGORY_LABELS } from '../types'
import type { BoardListItem } from '../types'

// 모집글 목록 표(PST-001)와 같은 모양. 제목이 남는 너비를 모두 쓴다.
const COLUMNS = [
  { label: '말머리', className: 'w-[136px]' },
  { label: '제목', className: '' },
  { label: '공모전', className: 'w-[280px]' },
  { label: '작성자', className: 'w-[120px]' },
  { label: '작성일', className: 'w-[96px]' },
  { label: '조회수', className: 'w-[80px]' },
  { label: '좋아요', className: 'w-[80px]' },
  { label: '댓글', className: 'w-[80px]' },
]

// BRD-001 ④⑤ 게시글 목록 표. 줄 어디를 눌러도 상세로 간다. 키보드는 제목 링크로 이동한다.
export function BoardTable({ posts }: { posts: BoardListItem[] }) {
  const navigate = useNavigate()

  return (
    <>
      {/* 좁은 화면(lg 미만)에서는 표 대신 같은 정보를 카드로 보여 준다 */}
      <BoardCards posts={posts} />
      <div className="hidden overflow-x-auto lg:block">
        <table className="w-full min-w-[1180px] table-fixed border-collapse text-left">
          <thead>
            <tr className="bg-surface-sub">
              {COLUMNS.map((column) => (
                <th
                  key={column.label}
                  scope="col"
                  className={['h-14 px-3 text-body font-bold text-text', column.className].join(
                    ' ',
                  )}
                >
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {posts.map((post) => (
              <tr
                key={post.boardPostId}
                onClick={() => navigate(`/board/${post.boardPostId}`)}
                className="h-[68px] cursor-pointer border-b border-line transition-colors hover:bg-bg"
              >
                <td className="px-3">
                  <CategoryBadge category={post.category} />
                </td>
                <td className="px-3">
                  <Link
                    to={`/board/${post.boardPostId}`}
                    // 줄 클릭과 겹쳐 두 번 이동하지 않게 한다
                    onClick={(event) => event.stopPropagation()}
                    className="block truncate rounded-input text-body-lg text-text outline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                  >
                    {post.title}
                  </Link>
                </td>
                <td className="truncate px-3 text-body text-text-sub">{post.contest?.title}</td>
                <td className="truncate px-3 text-body text-text">
                  {post.author.nickname ?? '탈퇴한 사용자'}
                </td>
                <td className="px-3 text-body text-text-sub">{formatPostDate(post.createdAt)}</td>
                <td className="px-3 text-body text-text-sub">{post.viewCount}</td>
                <td className="px-3 text-body text-text-sub">{post.likeCount}</td>
                <td className="px-3 text-body text-text-sub">{post.commentCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

// BRD-001 ④⑤ 좁은 화면용 카드. 카드 전체가 상세로 가는 링크다
function BoardCards({ posts }: { posts: BoardListItem[] }) {
  return (
    <ul className="flex flex-col border-t border-line lg:hidden">
      {posts.map((post) => (
        <li key={post.boardPostId} className="border-b border-line">
          <Link
            to={`/board/${post.boardPostId}`}
            className="flex flex-col gap-2 px-1 py-4 hover:bg-bg focus-visible:outline-2 focus-visible:outline-primary"
          >
            <div className="flex min-w-0 items-center gap-2">
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
            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-small text-text-sub">
              <span className="text-text">{post.author.nickname ?? '탈퇴한 사용자'}</span>
              <span>{formatPostDate(post.createdAt)}</span>
              <span>조회 {post.viewCount}</span>
              <span>좋아요 {post.likeCount}</span>
              <span>댓글 {post.commentCount}</span>
            </p>
          </Link>
        </li>
      ))}
    </ul>
  )
}

export function CategoryBadge({ category }: { category: BoardListItem['category'] }) {
  return (
    <Badge variant={category === 'SHOWCASE' ? 'primary-soft' : 'surface-sub'}>
      {BOARD_CATEGORY_LABELS[category]}
    </Badge>
  )
}
