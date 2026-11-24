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
]
// TODO(확인 필요): 댓글 수 열은 BRD-005(김건우 댓글 연결) 뒤에 붙인다 (BRD-001 ④)

// BRD-001 ④⑤ 게시글 목록 표. 줄 어디를 눌러도 상세로 간다. 키보드는 제목 링크로 이동한다.
export function BoardTable({ posts }: { posts: BoardListItem[] }) {
  const navigate = useNavigate()

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[1100px] table-fixed border-collapse text-left">
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
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function CategoryBadge({ category }: { category: BoardListItem['category'] }) {
  return (
    <Badge variant={category === 'SHOWCASE' ? 'primary-soft' : 'surface-sub'}>
      {BOARD_CATEGORY_LABELS[category]}
    </Badge>
  )
}
