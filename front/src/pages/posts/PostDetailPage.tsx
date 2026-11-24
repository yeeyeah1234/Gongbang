import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { getApiError } from '../../api/errors'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Toast } from '../../components/ui/Toast'
import { CommentSection } from '../../features/comment/components/CommentSection'
import { DDayBadge, DefaultThumbnail } from '../../features/contest/components/ContestCard'
import { ImageSlider } from '../../features/post/components/ImageSlider'
import { ProfilePhoto } from '../../components/ui/ProfilePhoto'
import { PostDeleteButton } from '../../features/post/components/PostDeleteButton'
import { PostApplyPanel, StatusBadge } from '../../features/post/components/PostApplyPanel'
import { usePostDetail } from '../../features/post/hooks'
import { isHttpUrl } from '../../features/post/postForm'
import type { PostDetail } from '../../features/post/types'
import { MemberActionPopover } from '../../features/report/components/MemberActionPopover'
import { useTimedToast } from '../../hooks/useTimedToast'

const PAGE_CLASS = 'mx-auto max-w-[1680px] px-6 py-12 lg:px-12 xl:px-[120px]'

// 2026-11-03 → 2026.11.03
function formatDate(date: string) {
  return date.slice(0, 10).replaceAll('-', '.')
}

// 2026-11-02T14:20:00 → 2026.11.02 14:20
function formatDateTime(dateTime: string) {
  return `${formatDate(dateTime)} ${dateTime.slice(11, 16)}`
}

// PST-005 모집글 상세 / Figma 03-1~03-7. 비회원도 본다 (PST-001 ⑤).
export default function PostDetailPage() {
  const postId = Number(useParams().postId)
  const { data: post, isPending, isError, error, refetch } = usePostDetail(postId)
  const toast = useTimedToast()
  const location = useLocation()
  const navigate = useNavigate()

  // 작성·수정 화면에서 넘겨준 안내(사진을 다 올리지 못함 등, PST-007 ②)를 한 번 띄우고 지운다
  const notice = (location.state as { notice?: string } | null)?.notice
  const { show } = toast
  useEffect(() => {
    if (!notice) return
    show(notice)
    navigate(location.pathname, { replace: true, state: null })
  }, [notice, show, navigate, location.pathname])

  if (isError || !Number.isInteger(postId) || postId <= 0) {
    // PST-004 ⑤ 삭제되었거나 없는 글
    const notFound = getApiError(error).code === 'POST_NOT_FOUND' || !(postId > 0)
    return (
      <main className={`${PAGE_CLASS} flex flex-col items-center gap-6 py-32 text-center`}>
        <title>모집글 | 공방</title>
        <p className="text-subheading font-bold text-text">
          {notFound
            ? '삭제되었거나 없는 모집글이에요'
            : '모집글을 불러오지 못했어요. 잠시 후 다시 시도해 주세요'}
        </p>
        <div className="flex gap-3">
          {!notFound && <Button onClick={() => refetch()}>다시 시도</Button>}
          <Link
            to="/posts"
            className="inline-flex h-11 items-center rounded-full bg-primary px-5 text-[17px] font-bold text-surface hover:bg-primary-hover"
          >
            목록으로
          </Link>
        </div>
      </main>
    )
  }

  if (isPending) return <DetailSkeleton />

  return (
    <main className={PAGE_CLASS}>
      <title>{`${post.title} | 공방`}</title>
      <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_400px] xl:gap-16">
        <article className="min-w-0">
          <ContestCard contest={post.contest} />

          <div className="mt-8 flex items-start justify-between gap-6">
            <h1 className="min-w-0 text-title font-bold break-words text-text">{post.title}</h1>
            {/* ② 작성자에게만 제목 줄 오른쪽 [수정] [삭제] (PST-003 ①, PST-004 ①, 모집중·모집완료 모두) */}
            {post.myStatus === 'AUTHOR' && (
              <div className="flex shrink-0 gap-2 pt-1">
                <Link
                  to={`/posts/${post.postId}/edit`}
                  className="inline-flex h-9 items-center rounded-full border border-line-strong bg-surface px-4 text-small font-medium text-text hover:bg-bg"
                >
                  수정
                </Link>
                <PostDeleteButton post={post} onError={toast.show} />
              </div>
            )}
          </div>
          <AuthorLine post={post} onReportSuccess={toast.show} />

          <dl className="mt-6 grid grid-cols-[120px_minmax(0,1fr)] gap-x-6 gap-y-3 border-t border-line-strong pt-6 text-body">
            <InfoRow label="모집 인원">
              <span className="sr-only">확정 팀원 </span>
              {post.currentCount} / {post.targetCount}명
            </InfoRow>
            <InfoRow label="모집 상태">
              <StatusBadge status={post.status} recruitEndDate={post.recruitEndDate} />
            </InfoRow>
            <InfoRow label="모집 기간">
              {formatDate(post.recruitStartDate)} ~ {formatDate(post.recruitEndDate)}
            </InfoRow>
            {post.relatedUrl && (
              <InfoRow label="관련 URL">
                <ExternalLink href={post.relatedUrl} />
              </InfoRow>
            )}
          </dl>

          <div className="mt-8 rounded-card bg-surface-sub p-6 text-body break-words whitespace-pre-wrap text-text">
            {post.content}
          </div>

          {/* ⑦ 댓글 영역 (INT-004 · NET-01) */}
          <CommentSection
            targetKind="post"
            targetId={post.postId}
            redirectTo={`/posts/${post.postId}`}
            onNotify={toast.show}
          />
        </article>

        {/* PST-005 ⑤ 사진은 공모전 상세(MAIN-02 ③) 포스터 칸과 같은 세로 3:4로 위에 두고,
            PST-006 ②③ 지원 패널만 GNB(72px) 아래에 고정한다. 둘을 함께 고정하면 화면보다 길어져 사진 아래가 잘린다 */}
        <aside className="flex flex-col gap-6">
          {/* 한 칸 화면(lg 미만)에서는 지원 패널을 먼저, 사진은 공모전 상세 포스터 칸과 같은 최대 400px로 가운데에 */}
          <div className="order-2 mx-auto w-full max-w-100 lg:order-1 lg:max-w-none">
            <RepresentativeImage post={post} />
          </div>
          <div className="order-1 lg:sticky lg:top-24 lg:order-2">
            <PostApplyPanel post={post} onError={toast.show} />
          </div>
        </aside>
      </div>
      <Toast message={toast.message} />
    </main>
  )
}

function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-text-sub">{label}</dt>
      <dd className="min-w-0 text-text">{children}</dd>
    </>
  )
}

// ④ 관련 URL은 새 탭으로. 서버도 검사하지만 http(s)가 아니면 링크로 만들지 않는다 (AGENTS 9).
function ExternalLink({ href }: { href: string }) {
  if (!isHttpUrl(href)) return <span className="break-all">{href}</span>
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex max-w-full items-center gap-1 break-all text-primary hover:underline"
    >
      {href}
      <span aria-hidden="true">↗</span>
      <span className="sr-only">(새 탭)</span>
    </a>
  )
}

// ① 공모전 카드: 서비스 공모전은 공모전 상세로, 외부 공모전은 입력한 링크를 새 탭으로
function ContestCard({ contest }: { contest: PostDetail['contest'] }) {
  const body = (
    <>
      <div className="flex min-w-0 flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          {contest.categoryName && <Badge variant="primary-soft">{contest.categoryName}</Badge>}
          {contest.isExternal && (
            <span className="inline-flex h-[26px] items-center rounded-full border border-line-strong px-2.5 text-caption font-bold text-text-sub">
              외부
            </span>
          )}
          {contest.dDay >= 0 ? (
            <DDayBadge dDay={contest.dDay} />
          ) : (
            <Badge variant="surface-sub">마감</Badge>
          )}
        </div>
        <p className="truncate text-subheading font-bold text-text">{contest.title}</p>
      </div>
    </>
  )
  // 「공모전 보기」는 카드 오른쪽 아래 (제목 줄 높이에 맞춘다)
  const cardClass =
    'flex items-end justify-between gap-6 rounded-card border border-line bg-surface px-6 py-5'
  const more = <span className="shrink-0 text-small text-text-sub">공모전 보기 ›</span>

  if (contest.contestId !== null) {
    return (
      <Link to={`/contests/${contest.contestId}`} className={`${cardClass} hover:bg-bg`}>
        {body}
        {more}
      </Link>
    )
  }
  if (contest.url && isHttpUrl(contest.url)) {
    return (
      <a
        href={contest.url}
        target="_blank"
        rel="noopener noreferrer"
        className={`${cardClass} hover:bg-bg`}
      >
        {body}
        <span className="shrink-0 text-small text-text-sub">
          공모전 보기 ↗<span className="sr-only">(새 탭)</span>
        </span>
      </a>
    )
  }
  // 링크를 넣지 않은 외부 공모전은 누를 곳이 없다
  return <div className={cardClass}>{body}</div>
}

// 작성자, 작성일, 조회수
function AuthorLine({
  post,
  onReportSuccess,
}: {
  post: PostDetail
  onReportSuccess: (message: string) => void
}) {
  const nickname = post.author.nickname ?? '탈퇴한 사용자'
  return (
    <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-2 text-body text-text-sub">
      <MemberActionPopover
        memberId={post.author.memberId}
        nickname={nickname}
        redirectTo={`/posts/${post.postId}`}
        onReportSuccess={onReportSuccess}
      >
        <ProfilePhoto url={post.author.profileImageUrl} name={nickname} size="sm" decorative />
        {nickname}
      </MemberActionPopover>
      <span aria-hidden="true">·</span>
      <time dateTime={post.createdAt}>{formatDateTime(post.createdAt)}</time>
      <span aria-hidden="true">·</span>
      <span>조회 {post.viewCount.toLocaleString()}</span>
    </div>
  )
}

// ⑤ 대표 이미지. 외부 공모전에 올린 사진이 2장 이상이면 슬라이드 (PST-007 ⑥).
// 없거나 불러오지 못하면 기본 이미지 (PST-002 ⑤)
function RepresentativeImage({ post }: { post: PostDetail }) {
  const [failed, setFailed] = useState(false)
  if (post.images.length >= 2)
    return (
      <ImageSlider
        images={post.images}
        label="공모전 사진"
        altPrefix={`${post.contest.title} 사진`}
        aspectClass="aspect-3/4"
      />
    )
  return (
    <section
      aria-label="대표 이미지"
      className="aspect-3/4 overflow-hidden rounded-card border border-line bg-surface-sub"
    >
      {post.imageUrl && !failed ? (
        <img
          src={post.imageUrl}
          alt={`${post.contest.title} 대표 이미지`}
          onError={() => setFailed(true)}
          className="size-full object-cover"
        />
      ) : (
        <DefaultThumbnail />
      )}
    </section>
  )
}

function DetailSkeleton() {
  return (
    <main className={PAGE_CLASS} aria-busy="true">
      <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_400px] xl:gap-16">
        <div className="space-y-6">
          <div className="h-24 animate-pulse rounded-card bg-surface-sub" />
          <div className="h-10 w-2/3 animate-pulse rounded-lg bg-surface-sub" />
          <div className="h-40 animate-pulse rounded-lg bg-surface-sub" />
          <div className="h-60 animate-pulse rounded-card bg-surface-sub" />
        </div>
        <div className="h-72 animate-pulse rounded-card bg-surface-sub" />
      </div>
    </main>
  )
}
