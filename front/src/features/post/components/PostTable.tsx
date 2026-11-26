import { Highlight } from '../../search/components/Highlight'
import { Link, useNavigate } from 'react-router'
import { Badge } from '../../../components/ui/Badge'
import { formatPostDate } from '../format'
import { StatusBadge } from './PostApplyPanel'
import type { PostListItem } from '../types'

interface PostTableProps {
  posts: PostListItem[]
  // SRCH-02 ③ 통합 검색에서 넘기면 제목·작성자 닉네임 안의 검색어를 강조한다 (목록 화면은 넘기지 않음)
  highlight?: string
}

// Figma WF-01 열 너비 (1680px 기준). 모집글 제목이 남는 너비를 모두 쓴다.
const COLUMNS = [
  { label: '분야', className: 'w-[136px]' },
  { label: '공모전', className: 'w-[320px]' },
  { label: '모집글 제목', className: '' },
  { label: '작성자', className: 'w-[120px]' },
  { label: '작성일', className: 'w-[96px]' },
  { label: '조회수', className: 'w-[80px]' },
  { label: '모집 인원', className: 'w-[104px]' },
  { label: '상태', className: 'w-[104px]' },
]

// PST-001 ②③④ 모집글 목록 표. 줄 어디를 눌러도 상세로 간다 (⑤). 키보드는 제목 링크로 이동한다.
export function PostTable({ posts, highlight }: PostTableProps) {
  const navigate = useNavigate()

  return (
    <>
      {/* 좁은 화면(lg 미만)에서는 표 대신 같은 정보를 카드로 보여 준다 */}
      <PostCards posts={posts} highlight={highlight} />
      {/* 표가 화면보다 넓어지면 표만 가로로 스크롤한다 */}
      <div className="hidden overflow-x-auto lg:block">
        <table className="w-full min-w-[1240px] table-fixed border-collapse text-left">
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
                key={post.postId}
                onClick={() => navigate(`/posts/${post.postId}`)}
                className="h-[68px] cursor-pointer border-b border-line transition-colors hover:bg-bg"
              >
                <td className="px-3">
                  {post.contest?.categoryName && (
                    <Badge className="max-w-full truncate">{post.contest.categoryName}</Badge>
                  )}
                </td>
                <td className="px-3">
                  <div className="flex min-w-0 items-center gap-2">
                    {post.contest?.isExternal && (
                      <span className="inline-flex h-[26px] shrink-0 items-center rounded-full border border-line-strong px-2.5 text-caption font-bold text-text-sub">
                        외부
                      </span>
                    )}
                    <span className="truncate text-body text-text">{post.contest?.title}</span>
                  </div>
                </td>
                <td className="px-3">
                  <Link
                    to={`/posts/${post.postId}`}
                    // 줄 클릭과 겹쳐 두 번 이동하지 않게 한다
                    onClick={(event) => event.stopPropagation()}
                    className="block truncate rounded-input text-body-lg text-text outline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                  >
                    <Highlight text={post.title} keyword={highlight} />
                  </Link>
                </td>
                <td className="truncate px-3 text-body text-text">
                  <Highlight text={post.author.nickname ?? '탈퇴한 사용자'} keyword={highlight} />
                </td>
                <td className="px-3 text-body text-text-sub">{formatPostDate(post.createdAt)}</td>
                <td className="px-3 text-body text-text-sub">{post.viewCount}</td>
                <td className="px-3 text-body-lg font-bold text-text">
                  <span className="sr-only">확정 팀원 </span>
                  {post.currentCount}/{post.targetCount}
                </td>
                <td className="px-3">
                  <StatusBadge status={post.status} recruitEndDate={post.recruitEndDate} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

// PST-001 ②③④ 좁은 화면용 카드. 카드 전체가 상세로 가는 링크다 (⑤)
function PostCards({ posts, highlight }: PostTableProps) {
  return (
    <ul className="flex flex-col border-t border-line lg:hidden">
      {posts.map((post) => (
        <li key={post.postId} className="border-b border-line">
          <Link
            to={`/posts/${post.postId}`}
            className="flex flex-col gap-2 px-1 py-4 hover:bg-bg focus-visible:outline-2 focus-visible:outline-primary"
          >
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge status={post.status} recruitEndDate={post.recruitEndDate} />
              {post.contest?.categoryName && <Badge>{post.contest.categoryName}</Badge>}
              {post.contest?.isExternal && (
                <span className="inline-flex h-[26px] items-center rounded-full border border-line-strong px-2.5 text-caption font-bold text-text-sub">
                  외부
                </span>
              )}
            </div>
            <p className="line-clamp-2 text-body-lg font-bold break-words text-text">
              <Highlight text={post.title} keyword={highlight} />
            </p>
            {post.contest && (
              <p className="truncate text-small text-text-sub">{post.contest.title}</p>
            )}
            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-small text-text-sub">
              <span className="text-text">
                <Highlight text={post.author.nickname ?? '탈퇴한 사용자'} keyword={highlight} />
              </span>
              <span>{formatPostDate(post.createdAt)}</span>
              <span>조회 {post.viewCount}</span>
              <span className="font-bold text-text">
                <span className="sr-only">확정 팀원 </span>
                {post.currentCount}/{post.targetCount}명
              </span>
            </p>
          </Link>
        </li>
      ))}
    </ul>
  )
}
