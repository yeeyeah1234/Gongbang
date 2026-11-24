import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { getApiError } from '../../api/errors'
import { Badge } from '../../components/ui/Badge'
import { PostForm } from '../../features/post/components/PostForm'
import { PostImagePicker } from '../../features/post/components/PostImagePicker'
import { usePostDetail, useSavePostImages, useUpdatePost } from '../../features/post/hooks'
import {
  MESSAGES,
  toEditFormValues,
  toUpdateRequest,
  validatePostEditForm,
} from '../../features/post/postForm'
import type { PostFormErrors, PostFormValues } from '../../features/post/postForm'
import { POST_IMAGE_MESSAGES, toDrafts } from '../../features/post/postImages'
import type { PostImageDraft } from '../../features/post/postImages'
import type { PostDetail } from '../../features/post/types'

const PAGE_CLASS = 'mx-auto max-w-[1680px] px-6 py-12 lg:px-12 xl:px-[120px]'

// 서버가 막은 이유를 해당 칸 아래에 보여 준다 (PST-003 ④⑦)
function serverErrorToField(code: string | null): PostFormErrors | null {
  switch (code) {
    case 'TARGET_BELOW_CURRENT':
      return { targetCount: MESSAGES.targetBelowCurrent }
    case 'TARGET_EQUALS_CURRENT':
      return { targetCount: MESSAGES.targetEqualsCurrent }
    case 'INVALID_PERIOD':
      return { period: MESSAGES.invalidPeriod }
    default:
      return null
  }
}

// PST-003 모집글 수정 / Figma 02-4·02-5. 로그인 회원만 들어오고(RequireLogin), 작성자가 아니면 막는다.
// 서버도 작성자인지 다시 확인한다 (403 NOT_POST_AUTHOR).
export default function PostEditPage() {
  const postId = Number(useParams().postId)
  const { data: post, isPending, isError, error } = usePostDetail(postId)

  if (isError || !(postId > 0)) {
    const notFound = getApiError(error).code === 'POST_NOT_FOUND' || !(postId > 0)
    return (
      <Blocked
        message={
          notFound
            ? '삭제되었거나 없는 모집글이에요'
            : '모집글을 불러오지 못했어요. 잠시 후 다시 시도해 주세요'
        }
        to="/posts"
        label="목록으로"
      />
    )
  }
  if (isPending) {
    return (
      <main className={PAGE_CLASS} aria-busy="true">
        <div className="h-10 w-48 animate-pulse rounded-lg bg-surface-sub" />
        <div className="mt-8 h-[480px] max-w-[1100px] animate-pulse rounded-card bg-surface-sub" />
      </main>
    )
  }
  // PST-006 ⑧: 작성자가 아니면 서버 문구와 같은 안내
  if (post.myStatus !== 'AUTHOR') {
    return (
      <Blocked message="모집글 작성자만 할 수 있어요" to={`/posts/${post.postId}`} label="상세로" />
    )
  }
  // 글이 바뀌면 폼을 새로 채운다
  return <PostEditForm key={post.postId} post={post} />
}

function PostEditForm({ post }: { post: PostDetail }) {
  const navigate = useNavigate()
  const updatePost = useUpdatePost()
  const saveImages = useSavePostImages()
  // PST-003 ②, PST-007 ③ 외부 공모전 사진 추가·삭제·순서 (「수정 완료」 때 반영)
  const [images, setImages] = useState<PostImageDraft[]>(() => toDrafts(post.images))
  const [initial] = useState(() => toEditFormValues(post))
  const [values, setValues] = useState<PostFormValues>(initial)
  const [submitted, setSubmitted] = useState(false)
  const [serverErrors, setServerErrors] = useState<PostFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const detailPath = `/posts/${post.postId}`

  // 한 번 「수정 완료」를 누른 뒤부터는 고칠 때마다 다시 검사한다.
  // 목표 인원은 확정 인원보다 적어지는 순간 바로 알려 준다 (Figma 02-5).
  const checked = validatePostEditForm(values, post)
  const errors: PostFormErrors = submitted
    ? { ...checked, ...serverErrors }
    : { targetCount: checked.targetCount, ...serverErrors }

  const update = (patch: Partial<PostFormValues>) => {
    setValues((prev) => ({ ...prev, ...patch }))
    setServerErrors({})
    setFormError(null)
  }

  const submit = () => {
    setSubmitted(true)
    setServerErrors({})
    setFormError(null)
    if (Object.keys(validatePostEditForm(values, post)).length > 0) return

    updatePost.mutate(
      { postId: post.postId, body: toUpdateRequest(values, initial) },
      {
        // ⑥ 저장하면 상세 화면으로. 사진이 바뀌었으면 반영한 뒤에 간다 (PST-007)
        onSuccess: () => {
          if (!post.contest.isExternal || !imagesChanged(post.images, images)) {
            navigate(detailPath, { replace: true })
            return
          }
          const goDetail = (failed: number) =>
            navigate(detailPath, {
              replace: true,
              state: failed > 0 ? { notice: POST_IMAGE_MESSAGES.failed } : undefined,
            })
          saveImages.mutate(
            { postId: post.postId, saved: post.images, drafts: images },
            { onSuccess: ({ failed }) => goDetail(failed), onError: () => goDetail(1) },
          )
        },
        onError: (error) => {
          const { code, message } = getApiError(error)
          const fieldErrors = serverErrorToField(code)
          if (fieldErrors) setServerErrors(fieldErrors)
          else setFormError(message ?? '잠시 후 다시 시도해 주세요')
        },
      },
    )
  }

  return (
    <main className={PAGE_CLASS}>
      <title>모집글 수정 | 공방</title>
      <h1 className="text-title font-bold text-text">모집글 수정</h1>
      <PostForm
        contestField={<LockedContest contest={post.contest} />}
        imageField={
          post.contest.isExternal && <PostImagePicker drafts={images} onChange={setImages} />
        }
        values={values}
        errors={errors}
        onChange={update}
        formError={formError}
        submitLabel="수정 완료"
        submitting={updatePost.isPending || saveImages.isPending}
        onSubmit={submit}
        onCancel={() => navigate(detailPath)}
      />
    </main>
  )
}

// 사진을 하나라도 더하거나 빼거나 순서를 바꿨는지
function imagesChanged(saved: PostDetail['images'], drafts: PostImageDraft[]) {
  return (
    saved.length !== drafts.length ||
    drafts.some((draft, i) => draft.kind !== 'saved' || draft.imageId !== saved[i].imageId)
  )
}

// ② 연결된 공모전은 바꿀 수 없다 (회색 잠금)
function LockedContest({ contest }: { contest: PostDetail['contest'] }) {
  return (
    <div className="flex flex-col">
      <span id="post-contest-label" className="mb-2 text-body font-bold text-text">
        공모전<span className="ml-1 font-normal text-text-sub">(필수)</span>
      </span>
      <div
        aria-labelledby="post-contest-label"
        aria-disabled="true"
        className="flex min-h-14 items-center gap-3 rounded-input border border-line bg-surface-sub px-4 py-2 text-text-sub"
      >
        <svg aria-hidden="true" viewBox="0 0 20 20" className="size-5 shrink-0" fill="none">
          <rect x="4" y="9" width="12" height="8" rx="2" stroke="currentColor" strokeWidth="1.5" />
          <path d="M7 9V6.5a3 3 0 0 1 6 0V9" stroke="currentColor" strokeWidth="1.5" />
        </svg>
        {contest.categoryName && <Badge className="shrink-0">{contest.categoryName}</Badge>}
        {contest.isExternal && (
          <span className="inline-flex h-[26px] shrink-0 items-center rounded-full border border-line-strong px-2.5 text-caption font-bold">
            외부
          </span>
        )}
        <span className="min-w-0 flex-1 truncate text-body">{contest.title}</span>
        {/* 좁은 화면에서는 글자를 숨기고 자물쇠만 보여 준다 (화면 낭독기는 그대로 읽는다) */}
        <span className="sr-only shrink-0 text-small sm:not-sr-only">변경할 수 없어요</span>
      </div>
    </div>
  )
}

function Blocked({ message, to, label }: { message: string; to: string; label: string }) {
  return (
    <main className={`${PAGE_CLASS} flex flex-col items-center gap-6 py-32 text-center`}>
      <title>모집글 수정 | 공방</title>
      <p className="text-subheading font-bold text-text">{message}</p>
      <Link
        to={to}
        className="inline-flex h-11 items-center rounded-full bg-primary px-5 text-[17px] font-bold text-surface hover:bg-primary-hover"
      >
        {label}
      </Link>
    </main>
  )
}
