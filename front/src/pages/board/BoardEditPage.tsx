import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { getApiError } from '../../api/errors'
import {
  boardServerErrorToField,
  toBoardFormValues,
  toBoardRequest,
  validateBoardForm,
} from '../../features/board/boardForm'
import type { BoardFormErrors, BoardFormValues } from '../../features/board/boardForm'
import { BoardForm } from '../../features/board/components/BoardForm'
import { useBoardDetail, useSaveBoardImages, useUpdateBoardPost } from '../../features/board/hooks'
import type { BoardDetail } from '../../features/board/types'
import { POST_IMAGE_MESSAGES, toDrafts } from '../../features/post/postImages'
import type { PostImageDraft } from '../../features/post/postImages'

const PAGE_CLASS = 'mx-auto max-w-[1680px] px-4 py-8 md:px-6 md:py-12 lg:px-12 xl:px-[120px]'

// BRD-003 게시글 수정. 로그인 회원만 들어오고(RequireLogin), 작성자가 아니면 막는다.
// 서버도 작성자인지 다시 확인한다 (403 NOT_BOARD_POST_AUTHOR).
export default function BoardEditPage() {
  const boardPostId = Number(useParams().boardPostId)
  const { data: post, isPending, isError, error } = useBoardDetail(boardPostId)

  if (isError || !(boardPostId > 0)) {
    const notFound = getApiError(error).code === 'BOARD_POST_NOT_FOUND' || !(boardPostId > 0)
    return (
      <Blocked
        message={
          notFound
            ? '삭제되었거나 없는 글이에요'
            : '글을 불러오지 못했어요. 잠시 후 다시 시도해 주세요'
        }
        to="/board"
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
  // ① 작성자가 아니면 서버 문구와 같은 안내
  if (!post.isAuthor) {
    return (
      <Blocked
        message="글 작성자만 할 수 있어요"
        to={`/board/${post.boardPostId}`}
        label="상세로"
      />
    )
  }
  return <BoardEditForm key={post.boardPostId} post={post} />
}

function BoardEditForm({ post }: { post: BoardDetail }) {
  const navigate = useNavigate()
  const updatePost = useUpdateBoardPost()
  const saveImages = useSaveBoardImages()
  const [values, setValues] = useState<BoardFormValues>(() => toBoardFormValues(post))
  const [images, setImages] = useState<PostImageDraft[]>(() => toDrafts(post.images))
  const [submitted, setSubmitted] = useState(false)
  const [serverErrors, setServerErrors] = useState<BoardFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const detailPath = `/board/${post.boardPostId}`

  const errors: BoardFormErrors = submitted ? { ...validateBoardForm(values), ...serverErrors } : {}

  const update = (patch: Partial<BoardFormValues>) => {
    setValues((prev) => ({ ...prev, ...patch }))
    setServerErrors({})
    setFormError(null)
  }

  // ③ 결과물 자랑에서 다른 말머리로 바꾸면 저장할 때 링크·사진이 지워진다
  const showcaseWillClear =
    values.category !== null &&
    values.category !== 'SHOWCASE' &&
    (post.linkUrl !== null ||
      post.images.length > 0 ||
      values.linkUrl.trim() !== '' ||
      images.length > 0)

  const submit = () => {
    setSubmitted(true)
    setServerErrors({})
    setFormError(null)
    if (Object.keys(validateBoardForm(values)).length > 0) return

    updatePost.mutate(
      { boardPostId: post.boardPostId, body: toBoardRequest(values) },
      {
        // 저장하면 상세로. 결과물 자랑이고 사진이 바뀌었으면 반영한 뒤에 간다
        onSuccess: () => {
          if (values.category !== 'SHOWCASE' || !imagesChanged(post.images, images)) {
            navigate(detailPath, { replace: true })
            return
          }
          const goDetail = (failed: number) =>
            navigate(detailPath, {
              replace: true,
              state: failed > 0 ? { notice: POST_IMAGE_MESSAGES.failed } : undefined,
            })
          saveImages.mutate(
            { postId: post.boardPostId, saved: post.images, drafts: images },
            { onSuccess: ({ failed }) => goDetail(failed), onError: () => goDetail(1) },
          )
        },
        onError: (error) => {
          const { code, message } = getApiError(error)
          const fieldErrors = boardServerErrorToField(code, message)
          if (fieldErrors) setServerErrors(fieldErrors)
          else setFormError(message ?? '잠시 후 다시 시도해 주세요')
        },
      },
    )
  }

  return (
    <main className={PAGE_CLASS}>
      <title>글 수정 | 공방</title>
      <h1 className="text-title font-bold text-text">글 수정</h1>
      <BoardForm
        values={values}
        errors={errors}
        onChange={update}
        images={images}
        onImagesChange={setImages}
        showcaseWillClear={showcaseWillClear}
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
function imagesChanged(saved: BoardDetail['images'], drafts: PostImageDraft[]) {
  return (
    saved.length !== drafts.length ||
    drafts.some((draft, i) => draft.kind !== 'saved' || draft.imageId !== saved[i].imageId)
  )
}

function Blocked({ message, to, label }: { message: string; to: string; label: string }) {
  return (
    <main className={`${PAGE_CLASS} flex flex-col items-center gap-6 py-32 text-center md:py-32`}>
      <title>글 수정 | 공방</title>
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
