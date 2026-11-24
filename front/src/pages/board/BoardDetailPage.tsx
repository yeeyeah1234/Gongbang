import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { getApiError, getErrorMessage } from '../../api/errors'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { Toast } from '../../components/ui/Toast'
import { CategoryBadge } from '../../features/board/components/BoardTable'
import { LikeButton } from '../../features/board/components/LikeButton'
import { useBoardDetail, useDeleteBoardPost } from '../../features/board/hooks'
import type { BoardDetail } from '../../features/board/types'
import { CommentSection } from '../../features/comment/components/CommentSection'
import { DDayBadge } from '../../features/contest/components/ContestCard'
import { ImageSlider } from '../../features/post/components/ImageSlider'
import { MemberPhoto } from '../../features/post/components/MemberPhoto'
import { isHttpUrl } from '../../features/post/postForm'
import { MemberActionPopover } from '../../features/report/components/MemberActionPopover'
import { useTimedToast } from '../../hooks/useTimedToast'

const PAGE_CLASS = 'mx-auto max-w-[1100px] px-6 py-12 lg:px-12'

// 2026-11-02T14:20:00 → 2026.11.02 14:20
function formatDateTime(dateTime: string) {
  return `${dateTime.slice(0, 10).replaceAll('-', '.')} ${dateTime.slice(11, 16)}`
}

// BRD-004 게시글 상세. 비회원도 본다 (BRD-001 ②).
export default function BoardDetailPage() {
  const boardPostId = Number(useParams().boardPostId)
  const { data: post, isPending, isError, error, refetch } = useBoardDetail(boardPostId)
  const toast = useTimedToast()
  const location = useLocation()
  const navigate = useNavigate()

  // 작성·수정 화면에서 넘겨준 안내(사진을 다 올리지 못함 등)를 한 번 띄우고 지운다
  const notice = (location.state as { notice?: string } | null)?.notice
  const { show } = toast
  useEffect(() => {
    if (!notice) return
    show(notice)
    navigate(location.pathname, { replace: true, state: null })
  }, [notice, show, navigate, location.pathname])

  if (isError || !(boardPostId > 0)) {
    // BRD-003 ⑤ 삭제되었거나 없는 글
    const notFound = getApiError(error).code === 'BOARD_POST_NOT_FOUND' || !(boardPostId > 0)
    return (
      <main className={`${PAGE_CLASS} flex flex-col items-center gap-6 py-32 text-center`}>
        <title>커뮤니티 게시판 | 공방</title>
        <p className="text-subheading font-bold text-text">
          {notFound
            ? '삭제되었거나 없는 글이에요'
            : '글을 불러오지 못했어요. 잠시 후 다시 시도해 주세요'}
        </p>
        <div className="flex gap-3">
          {!notFound && <Button onClick={() => refetch()}>다시 시도</Button>}
          <Link
            to="/board"
            className="inline-flex h-11 items-center rounded-full bg-primary px-5 text-[17px] font-bold text-surface hover:bg-primary-hover"
          >
            목록으로
          </Link>
        </div>
      </main>
    )
  }

  if (isPending) {
    return (
      <main className={PAGE_CLASS} aria-busy="true">
        <div className="h-8 w-32 animate-pulse rounded-lg bg-surface-sub" />
        <div className="mt-4 h-10 w-2/3 animate-pulse rounded-lg bg-surface-sub" />
        <div className="mt-8 h-60 animate-pulse rounded-card bg-surface-sub" />
      </main>
    )
  }

  return (
    <main className={PAGE_CLASS}>
      <title>{`${post.title} | 공방`}</title>
      <Link to="/board" className="text-small text-text-sub hover:underline">
        ‹ 커뮤니티 게시판
      </Link>

      <article className="mt-6">
        <CategoryBadge category={post.category} />
        <div className="mt-3 flex items-start justify-between gap-6">
          <h1 className="min-w-0 text-title font-bold break-words text-text">{post.title}</h1>
          {/* ③ 작성자에게만 제목 줄 오른쪽 [수정] [삭제] (BRD-003 ①) */}
          {post.isAuthor && (
            <div className="flex shrink-0 gap-2 pt-1">
              <Link
                to={`/board/${post.boardPostId}/edit`}
                className="inline-flex h-9 items-center rounded-full border border-line-strong bg-surface px-4 text-small font-medium text-text hover:bg-bg"
              >
                수정
              </Link>
              <DeleteButton post={post} onError={toast.show} />
            </div>
          )}
        </div>
        <AuthorLine post={post} onReportSuccess={toast.show} />

        {post.contest && <ContestCard contest={post.contest} />}

        {/* BRD-006 ⑤ 사진: 2장 이상이면 슬라이드 */}
        {post.images.length >= 2 ? (
          <div className="mt-8">
            <ImageSlider
              images={post.images}
              label="결과물 사진"
              altPrefix={`${post.title} 사진`}
            />
          </div>
        ) : (
          post.images.length === 1 && <SingleImage post={post} />
        )}

        <div className="mt-8 text-body-lg break-words whitespace-pre-wrap text-text">
          {post.content}
        </div>

        {/* ① 결과물 링크는 새 탭으로. http(s)가 아니면 링크로 만들지 않는다 (AGENTS 9) */}
        {post.linkUrl && (
          <p className="mt-8 flex flex-wrap items-center gap-3 rounded-card bg-surface-sub px-6 py-4 text-body">
            <span className="font-bold text-text">결과물 링크</span>
            {isHttpUrl(post.linkUrl) ? (
              <a
                href={post.linkUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="min-w-0 break-all text-primary hover:underline"
              >
                {post.linkUrl}
                <span aria-hidden="true"> ↗</span>
                <span className="sr-only">(새 탭)</span>
              </a>
            ) : (
              <span className="break-all">{post.linkUrl}</span>
            )}
          </p>
        )}

        {/* ⑥ 내용 아래 좋아요 (BRD-007) */}
        <div className="mt-10 flex justify-center">
          <LikeButton post={post} onError={toast.show} />
        </div>

        {/* ④ 댓글 영역 (BRD-005 · NET-01) */}
        <CommentSection
          targetKind="board-post"
          targetId={post.boardPostId}
          redirectTo={`/board/${post.boardPostId}`}
          onNotify={toast.show}
        />
      </article>
      <Toast message={toast.message} />
    </main>
  )
}

// 작성자, 작성일, 조회수. ② 탈퇴한 회원은 「탈퇴한 사용자」
function AuthorLine({
  post,
  onReportSuccess,
}: {
  post: BoardDetail
  onReportSuccess: (message: string) => void
}) {
  const nickname = post.author.nickname ?? '탈퇴한 사용자'
  return (
    <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-line pb-6 text-body text-text-sub">
      {/* ① 닉네임을 누르면 NET-02 팝오버 (프로필 보기·신고) */}
      <MemberActionPopover
        memberId={post.author.memberId}
        nickname={nickname}
        redirectTo={`/board/${post.boardPostId}`}
        onReportSuccess={onReportSuccess}
      >
        <MemberPhoto
          url={post.author.profileImageUrl}
          nickname={nickname}
          className="size-8 text-caption"
        />
        {nickname}
      </MemberActionPopover>
      <span aria-hidden="true">·</span>
      <time dateTime={post.createdAt}>{formatDateTime(post.createdAt)}</time>
      <span aria-hidden="true">·</span>
      <span>조회 {post.viewCount.toLocaleString()}</span>
    </div>
  )
}

// ① 연결된 공모전 카드: 누르면 공모전 상세
function ContestCard({ contest }: { contest: NonNullable<BoardDetail['contest']> }) {
  return (
    <Link
      to={`/contests/${contest.contestId}`}
      // 모집글 상세 공모전 카드와 같은 모양: 배지 줄 + 제목 줄, 「공모전 보기」는 오른쪽 아래
      className="mt-6 flex items-end justify-between gap-6 rounded-card border border-line bg-surface px-6 py-4 hover:bg-bg"
    >
      <div className="flex min-w-0 flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="primary-soft">{contest.categoryName}</Badge>
          {contest.dDay >= 0 ? <DDayBadge dDay={contest.dDay} /> : <Badge>마감</Badge>}
        </div>
        <span className="truncate text-body-lg font-bold text-text">{contest.title}</span>
      </div>
      <span className="shrink-0 text-small text-text-sub">공모전 보기 ›</span>
    </Link>
  )
}

function SingleImage({ post }: { post: BoardDetail }) {
  const [failed, setFailed] = useState(false)
  if (failed) return null
  return (
    <div className="mt-8 overflow-hidden rounded-card border border-line bg-surface-sub">
      <img
        src={post.images[0].url}
        alt={`${post.title} 사진`}
        onError={() => setFailed(true)}
        className="mx-auto max-h-[640px] w-full object-contain"
      />
    </div>
  )
}

// BRD-003 ④ 삭제 확인 후 목록으로
function DeleteButton({ post, onError }: { post: BoardDetail; onError: (m: string) => void }) {
  const navigate = useNavigate()
  const deletePost = useDeleteBoardPost()
  const [open, setOpen] = useState(false)

  const confirm = () => {
    if (deletePost.isPending) return
    deletePost.mutate(post.boardPostId, {
      onSuccess: () => navigate('/board', { replace: true }),
      onError: (error) => {
        setOpen(false)
        onError(getErrorMessage(error))
      },
    })
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex h-9 cursor-pointer items-center rounded-full border border-line-strong bg-surface px-4 text-small font-medium text-text hover:bg-bg"
      >
        삭제
      </button>
      <Modal
        open={open}
        title={`‘${post.title}’ 글을 삭제할까요?`}
        onClose={() => setOpen(false)}
        footer={
          <>
            <Button size="lg" onClick={() => setOpen(false)}>
              취소
            </Button>
            <Button variant="danger" size="lg" loading={deletePost.isPending} onClick={confirm}>
              삭제
            </Button>
          </>
        }
      >
        <p className="text-body text-text-sub">삭제하면 되돌릴 수 없어요</p>
      </Modal>
    </>
  )
}
