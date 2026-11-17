import { useState } from 'react'
import { Link } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { getErrorMessage } from '../../api/errors'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Modal } from '../../components/ui/Modal'
import { Toast } from '../../components/ui/Toast'
import { useTimedToast } from '../../hooks/useTimedToast'
import { deleteMyPosts } from '../../features/member/api'
import { MyPageLayout } from '../../features/member/components/MyPageLayout'
import { formatDate } from '../../features/member/constants'
import { useMyComments, useMyPosts } from '../../features/member/hooks'

type Tab = 'posts' | 'comments'

// TODO(확인 필요): 모집글 상태값·경로는 김수형 PST와 맞춘다 (지금 /posts/{id})
const STATUS_LABEL: Record<string, string> = {
  RECRUITING: '모집중',
  COMPLETED: '모집완료',
  CLOSED: '마감',
}

// MY-02 내 글·댓글 목록 / Figma 09
export default function MyPostsPage() {
  const toast = useTimedToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>('posts')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<number[]>([])
  const [confirmOpen, setConfirmOpen] = useState(false)
  const posts = useMyPosts(page, tab === 'posts')
  const comments = useMyComments(page, tab === 'comments')
  const current = tab === 'posts' ? posts : comments
  const data = current.data

  const switchTab = (next: Tab) => {
    setTab(next)
    setPage(0)
    setSelected([])
  }

  const removeSelected = async () => {
    try {
      const res = await deleteMyPosts(selected)
      toast.show(`${res.deletedCount}개를 삭제했어요`)
      setSelected([])
      void queryClient.invalidateQueries({ queryKey: ['members', 'me', 'posts'] })
    } catch (e) {
      toast.show(getErrorMessage(e))
    } finally {
      setConfirmOpen(false)
    }
  }

  const totalPages = data ? Math.max(1, Math.ceil(data.totalCount / data.size)) : 1

  return (
    <MyPageLayout
      breadcrumb="마이페이지 / 내 글 관리"
      title="내 글 관리"
      description="내가 쓴 모집글과 댓글을 한곳에서 관리해요."
    >
      <div className="flex gap-10 border-b border-divider" role="tablist">
        {(['posts', 'comments'] as const).map((key) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={tab === key}
            onClick={() => switchTab(key)}
            className={[
              '-mb-px h-[52px] cursor-pointer border-b-2 text-subheading',
              tab === key
                ? 'border-primary font-bold text-text'
                : 'border-transparent text-text-sub',
            ].join(' ')}
          >
            {key === 'posts' ? '모집글' : '댓글'}
            {tab === key && data ? ` ${data.totalCount}` : ''}
          </button>
        ))}
      </div>

      {current.isLoading ? (
        <div className="mt-6 h-48 animate-pulse rounded-card bg-surface-sub" />
      ) : current.isError ? (
        <p className="mt-10 text-center text-small text-text-sub">
          목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
      ) : !data || data.items.length === 0 ? (
        // ⑤ 빈 화면
        <div className="mt-16 flex flex-col items-center gap-4">
          <p className="text-body text-text-sub">아직 작성한 글이 없어요</p>
          <Link to="/posts/new">
            <Button variant="primary">글쓰기</Button>
          </Link>
        </div>
      ) : (
        <>
          <div className="mt-4 flex items-center justify-between">
            <span className="text-small text-text-sub">
              총 {data.totalCount}개{tab === 'posts' ? ` · ${selected.length}개 선택됨` : ''}
            </span>
            {tab === 'posts' && (
              <Button
                variant="danger"
                size="sm"
                disabled={selected.length === 0}
                onClick={() => setConfirmOpen(true)}
              >
                선택 삭제
              </Button>
            )}
          </div>
          <table className="mt-3 w-full border-collapse text-small">
            <thead>
              <tr className="bg-surface-sub text-left text-text-sub">
                {tab === 'posts' && (
                  <th className="w-12 px-4 py-3">
                    <span className="sr-only">선택</span>
                  </th>
                )}
                <th className="px-4 py-3 font-medium">{tab === 'posts' ? '제목' : '댓글'}</th>
                {tab === 'posts' && <th className="px-4 py-3 font-medium">상태</th>}
                <th className="px-4 py-3 font-medium">작성일</th>
                {tab === 'posts' && <th className="px-4 py-3 text-right font-medium">관리</th>}
              </tr>
            </thead>
            <tbody>
              {tab === 'posts'
                ? posts.data?.items.map((post) => (
                    <tr key={post.postId} className="border-b border-divider">
                      <td className="px-4 py-3">
                        <Checkbox
                          aria-label={`${post.title} 선택`}
                          checked={selected.includes(post.postId)}
                          onChange={(e) =>
                            setSelected(
                              e.target.checked
                                ? [...selected, post.postId]
                                : selected.filter((id) => id !== post.postId),
                            )
                          }
                        />
                      </td>
                      <td className="px-4 py-3">
                        <Link to={`/posts/${post.postId}`} className="text-text hover:underline">
                          {post.title}
                        </Link>{' '}
                        <span className="text-text-sub">[{post.commentCount}]</span>
                      </td>
                      <td className="px-4 py-3">
                        <Badge
                          variant={post.status === 'RECRUITING' ? 'primary-soft' : 'surface-sub'}
                        >
                          {STATUS_LABEL[post.status] ?? post.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-text-sub">{formatDate(post.createdAt)}</td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-2">
                          <Link to={`/posts/${post.postId}/edit`}>
                            <Button variant="outline" size="sm">
                              수정
                            </Button>
                          </Link>
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => {
                              setSelected([post.postId])
                              setConfirmOpen(true)
                            }}
                          >
                            삭제
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))
                : comments.data?.items.map((c) => (
                    <tr key={c.commentId} className="border-b border-divider">
                      <td className="px-4 py-3">
                        <Link to={`/posts/${c.postId}`} className="text-text hover:underline">
                          {c.content}
                        </Link>
                        <p className="text-caption text-text-sub">{c.postTitle}</p>
                      </td>
                      <td className="px-4 py-3 text-text-sub">{formatDate(c.createdAt)}</td>
                    </tr>
                  ))}
            </tbody>
          </table>
          {/* ④ 10개씩 페이지 */}
          <nav className="mt-6 flex items-center justify-center gap-2" aria-label="페이지">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
              className="size-8 cursor-pointer rounded-full text-text-sub disabled:cursor-not-allowed disabled:text-text-disabled"
            >
              ‹
            </button>
            {Array.from({ length: totalPages }, (_, i) => (
              <button
                key={i}
                type="button"
                aria-current={i === page ? 'page' : undefined}
                onClick={() => setPage(i)}
                className={[
                  'size-8 cursor-pointer rounded-full text-small',
                  i === page ? 'bg-primary font-bold text-surface' : 'text-text-sub hover:bg-bg',
                ].join(' ')}
              >
                {i + 1}
              </button>
            ))}
            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage(page + 1)}
              className="size-8 cursor-pointer rounded-full text-text-sub disabled:cursor-not-allowed disabled:text-text-disabled"
            >
              ›
            </button>
          </nav>
        </>
      )}

      {/* ⑥ 팀원이 있는 모집글을 지우면 확인 모달 (삭제는 숨김 처리) */}
      <Modal
        open={confirmOpen}
        size="md"
        title={`모집글 ${selected.length}개를 삭제할까요?`}
        description="팀원이 있는 모집글이어도 팀과 워크스페이스는 그대로 남아요. 되돌릴 수 없어요."
        onClose={() => setConfirmOpen(false)}
        footer={
          <>
            <Button variant="outline" size="lg" onClick={() => setConfirmOpen(false)}>
              취소
            </Button>
            <Button variant="danger" size="lg" onClick={removeSelected}>
              삭제
            </Button>
          </>
        }
      />
      <Toast message={toast.message} />
    </MyPageLayout>
  )
}
