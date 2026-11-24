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
import {
  useMyApplications,
  useMyBoardPosts,
  useMyComments,
  useMyPosts,
} from '../../features/member/hooks'

const TABS = {
  posts: '모집글',
  applications: '지원한 모집글',
  board: '게시판',
  comments: '댓글',
} as const
type Tab = keyof typeof TABS
const STATUS: Record<string, string> = {
  RECRUITING: '모집 중',
  COMPLETED: '모집 완료',
  CLOSED: '종료',
  PENDING: '지원 대기',
  ACCEPTED: '수락',
  REJECTED: '거절',
}
const EMPTY: Record<Tab, string> = {
  posts: '아직 작성한 모집글이 없어요',
  applications: '아직 지원한 모집글이 없어요',
  board: '아직 작성한 게시글이 없어요',
  comments: '아직 표시할 댓글이 없어요',
}
interface Row {
  id: number
  title: string
  href: string | null
  createdAt: string
  status?: string
  subtitle?: string
}

export default function MyPostsPage() {
  const toast = useTimedToast()
  const client = useQueryClient()
  const [tab, setTab] = useState<Tab>('posts')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<number[]>([])
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const posts = useMyPosts(page, tab === 'posts')
  const applications = useMyApplications(page, tab === 'applications')
  const board = useMyBoardPosts(page, tab === 'board')
  const comments = useMyComments(page, tab === 'comments')
  const current = { posts, applications, board, comments }[tab]
  const data = current.data
  const rows: Row[] =
    tab === 'posts'
      ? (posts.data?.items ?? []).map((p) => ({
          id: p.postId,
          title: p.title,
          href: `/posts/${p.postId}`,
          createdAt: p.createdAt,
          status: p.status,
          subtitle: `댓글 ${p.commentCount}개`,
        }))
      : tab === 'applications'
        ? (applications.data?.items ?? []).map((a) => ({
            id: a.applicationId,
            title: a.title,
            href: a.deletedAt ? null : `/posts/${a.postId}`,
            createdAt: a.createdAt,
            status: a.status,
            subtitle: a.deletedAt ? '삭제된 모집글은 열 수 없어요' : '지원한 모집글',
          }))
        : tab === 'board'
          ? (board.data?.items ?? []).map((b) => ({
              id: b.boardPostId,
              title: b.title,
              href: `/board/${b.boardPostId}`,
              createdAt: b.createdAt,
            }))
          : (comments.data?.items ?? []).map((c) => ({
              id: c.commentId,
              title: c.content,
              href: c.boardPostId != null ? `/board/${c.boardPostId}` : `/posts/${c.postId}`,
              createdAt: c.createdAt,
              subtitle: `${c.boardPostId != null ? '게시판' : '모집글'} · ${c.postTitle}`,
            }))
  const totalPages = data ? Math.max(1, Math.ceil(data.totalCount / data.size)) : 1
  function switchTab(next: Tab) {
    setTab(next)
    setPage(0)
    setSelected([])
  }
  function changePage(next: number) {
    setPage(next)
    setSelected([])
  }
  async function removeSelected() {
    if (deleting) return
    setDeleting(true)
    try {
      const result = await deleteMyPosts(selected)
      toast.show(`${result.deletedCount}개를 삭제했어요`)
      setSelected([])
      setPage(0)
      await client.invalidateQueries({ queryKey: ['members', 'me'] })
    } catch (e) {
      toast.show(getErrorMessage(e))
    } finally {
      setDeleting(false)
      setConfirmOpen(false)
    }
  }
  function rowTitle(row: Row) {
    return (
      <div className="min-w-0">
        {row.href ? (
          <Link to={row.href} className="block min-h-10 break-words text-text hover:underline">
            {row.title}
          </Link>
        ) : (
          <p className="break-words text-text-sub">{row.title}</p>
        )}
        {row.subtitle && (
          <p className="mt-1 text-caption break-words text-text-sub">{row.subtitle}</p>
        )}
      </div>
    )
  }
  function select(row: Row) {
    return (
      <Checkbox
        aria-label={`${row.title} 선택`}
        checked={selected.includes(row.id)}
        onChange={(e) =>
          setSelected(
            e.target.checked ? [...selected, row.id] : selected.filter((id) => id !== row.id),
          )
        }
      />
    )
  }
  function edit(row: Row) {
    return (
      <div className="flex flex-wrap justify-end gap-2">
        <Link to={`${row.href}/edit`}>
          <Button variant="outline" size="sm">
            수정
          </Button>
        </Link>
        {tab === 'posts' && (
          <Button
            size="sm"
            onClick={() => {
              setSelected([row.id])
              setConfirmOpen(true)
            }}
          >
            삭제
          </Button>
        )}
      </div>
    )
  }
  const editable = tab === 'posts' || tab === 'board'
  return (
    <MyPageLayout
      breadcrumb="마이페이지 / 내 글 관리"
      title="내 글 관리"
      description="작성한 글·댓글과 지원한 모집글을 한곳에서 확인해요."
    >
      <div
        className="flex flex-wrap gap-x-6 border-b border-divider"
        role="tablist"
        aria-label="내 활동"
      >
        {(Object.keys(TABS) as Tab[]).map((key) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={tab === key}
            onClick={() => switchTab(key)}
            className={`min-h-[52px] cursor-pointer border-b-2 text-body ${tab === key ? 'border-primary font-bold text-text' : 'border-transparent text-text-sub'}`}
          >
            {TABS[key]}
            {tab === key && data ? ` ${data.totalCount}` : ''}
          </button>
        ))}
      </div>
      {current.isLoading ? (
        <div
          className="mt-6 h-48 animate-pulse rounded-card bg-surface-sub"
          aria-label="목록 불러오는 중"
        />
      ) : current.isError ? (
        <div role="alert" className="mt-10 text-center">
          <p>목록을 불러오지 못했어요.</p>
          <Button className="mt-3" onClick={() => current.refetch()}>
            다시 시도
          </Button>
        </div>
      ) : rows.length === 0 ? (
        <div className="my-16 text-center">
          <p className="text-text-sub">{EMPTY[tab]}</p>
          <Link
            to={tab === 'board' ? '/board/new' : tab === 'posts' ? '/posts/new' : '/'}
            className="mt-4 inline-flex min-h-10 items-center text-primary"
          >
            {editable ? '글쓰기' : '공모전 둘러보기'}
          </Link>
        </div>
      ) : (
        <>
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <span className="text-small text-text-sub">총 {data?.totalCount ?? 0}개</span>
            {tab === 'posts' && (
              <Button
                variant="danger"
                size="sm"
                disabled={selected.length === 0 || current.isFetching}
                onClick={() => setConfirmOpen(true)}
              >
                선택 삭제
              </Button>
            )}
          </div>
          <ul className="mt-3 divide-y divide-divider lg:hidden">
            {rows.map((row) => (
              <li key={row.id} className="space-y-3 py-4">
                <div className="flex items-start gap-3">
                  {tab === 'posts' && select(row)}
                  {rowTitle(row)}
                </div>
                <div className="flex flex-wrap items-center justify-between gap-3">
                  {row.status && (
                    <Badge variant="surface-sub">{STATUS[row.status] ?? row.status}</Badge>
                  )}
                  <span className="text-small text-text-sub">
                    {tab === 'applications' ? '지원일 ' : ''}
                    {formatDate(row.createdAt)}
                  </span>
                </div>
                {editable && edit(row)}
              </li>
            ))}
          </ul>
          <table className="mt-3 hidden w-full table-fixed border-collapse text-small lg:table">
            <thead>
              <tr className="bg-surface-sub text-left text-text-sub">
                {tab === 'posts' && (
                  <th className="w-12 p-3">
                    <span className="sr-only">선택</span>
                  </th>
                )}
                <th className="p-3">{tab === 'comments' ? '댓글' : '제목'}</th>
                {(tab === 'posts' || tab === 'applications') && <th className="w-24 p-3">상태</th>}
                <th className="w-28 p-3">{tab === 'applications' ? '지원일' : '작성일'}</th>
                {editable && <th className="w-36 p-3 text-right">관리</th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-b border-divider">
                  {tab === 'posts' && <td className="p-3">{select(row)}</td>}
                  <td className="p-3">{rowTitle(row)}</td>
                  {row.status && (
                    <td className="p-3">
                      <Badge variant="surface-sub">{STATUS[row.status] ?? row.status}</Badge>
                    </td>
                  )}
                  <td className="p-3 text-text-sub">{formatDate(row.createdAt)}</td>
                  {editable && <td className="p-3">{edit(row)}</td>}
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
      {!current.isError && data && data.totalCount > 0 && (
        <nav className="mt-6 flex items-center justify-center gap-4" aria-label="페이지">
          <Button disabled={page === 0 || current.isFetching} onClick={() => changePage(page - 1)}>
            이전
          </Button>
          <span className="text-small">
            {page + 1} / {totalPages}
          </span>
          <Button
            disabled={!data.hasNext || current.isFetching}
            onClick={() => changePage(page + 1)}
          >
            다음
          </Button>
        </nav>
      )}
      <Modal
        open={confirmOpen}
        size="md"
        title={`모집글 ${selected.length}개를 삭제할까요?`}
        description="팀과 워크스페이스는 그대로 남아요. 되돌릴 수 없어요."
        onClose={() => {
          if (!deleting) setConfirmOpen(false)
        }}
        footer={
          <>
            <Button disabled={deleting} onClick={() => setConfirmOpen(false)}>
              취소
            </Button>
            <Button variant="danger" disabled={deleting} onClick={removeSelected}>
              {deleting ? '삭제 중' : '삭제'}
            </Button>
          </>
        }
      />
      <Toast message={toast.message} />
    </MyPageLayout>
  )
}
