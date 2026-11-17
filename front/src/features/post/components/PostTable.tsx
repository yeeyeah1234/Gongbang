import { Link, useNavigate } from 'react-router'
import { Badge } from '../../../components/ui/Badge'
import { formatPostDate } from '../format'
import type { PostListItem } from '../types'

interface PostTableProps {
  posts: PostListItem[]
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
export function PostTable({ posts }: PostTableProps) {
  const navigate = useNavigate()

  return (
    // 좁은 화면에서는 제목 열이 사라지지 않게 표만 가로로 스크롤한다
    <div className="overflow-x-auto">
      <table className="w-full min-w-[1240px] table-fixed border-collapse text-left">
        <thead>
          <tr className="bg-surface-sub">
            {COLUMNS.map((column) => (
              <th
                key={column.label}
                scope="col"
                className={['h-14 px-3 text-body font-bold text-text', column.className].join(' ')}
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
                  {post.title}
                </Link>
              </td>
              <td className="truncate px-3 text-body text-text">
                {post.author.nickname ?? '탈퇴한 사용자'}
              </td>
              <td className="px-3 text-body text-text-sub">{formatPostDate(post.createdAt)}</td>
              <td className="px-3 text-body text-text-sub">{post.viewCount}</td>
              <td className="px-3 text-body-lg font-bold text-text">
                <span className="sr-only">확정 팀원 </span>
                {post.currentCount}/{post.targetCount}
              </td>
              <td className="px-3">
                {post.status === 'RECRUITING' ? (
                  <Badge variant="primary-soft">모집중</Badge>
                ) : (
                  <Badge variant="surface-sub">모집완료</Badge>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
