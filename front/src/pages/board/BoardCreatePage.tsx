import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { getApiError } from '../../api/errors'
import {
  EMPTY_BOARD_FORM,
  boardServerErrorToField,
  toBoardRequest,
  validateBoardForm,
} from '../../features/board/boardForm'
import type { BoardFormErrors, BoardFormValues } from '../../features/board/boardForm'
import { BoardForm } from '../../features/board/components/BoardForm'
import { useCreateBoardPost, useSaveBoardImages } from '../../features/board/hooks'
import { useContestDetail } from '../../features/contest/hooks'
import { POST_IMAGE_MESSAGES } from '../../features/post/postImages'
import type { PostImageDraft } from '../../features/post/postImages'

const PAGE_CLASS = 'mx-auto max-w-[1680px] px-4 py-8 md:px-6 md:py-12 lg:px-12 xl:px-[120px]'

// BRD-002 게시글 작성. 로그인 회원만 들어온다 (RequireLogin).
// ③ 공모전 상세의 「이 공모전 이야기 쓰기」는 ?contestId=로 들어와 말머리·공모전이 미리 골라져 있다
export default function BoardCreatePage() {
  const contestId = Number(useSearchParams()[0].get('contestId'))
  if (Number.isInteger(contestId) && contestId > 0) return <PrefilledCreate contestId={contestId} />
  return <BoardCreateForm initial={EMPTY_BOARD_FORM} />
}

function PrefilledCreate({ contestId }: { contestId: number }) {
  const { data, isPending } = useContestDetail(contestId)
  if (isPending) {
    return (
      <main className={PAGE_CLASS} aria-busy="true">
        <div className="h-10 w-48 animate-pulse rounded-lg bg-surface-sub" />
      </main>
    )
  }
  // 공모전을 찾지 못하면 빈 폼으로 연다
  const initial: BoardFormValues = data
    ? {
        ...EMPTY_BOARD_FORM,
        category: 'CONTEST',
        contest: {
          contestId: data.contestId,
          title: data.title,
          categoryName: data.categoryName,
          deadline: data.deadline,
        },
      }
    : EMPTY_BOARD_FORM
  return <BoardCreateForm initial={initial} />
}

function BoardCreateForm({ initial }: { initial: BoardFormValues }) {
  const navigate = useNavigate()
  const createPost = useCreateBoardPost()
  const saveImages = useSaveBoardImages()
  const [values, setValues] = useState<BoardFormValues>(initial)
  const [images, setImages] = useState<PostImageDraft[]>([])
  const [submitted, setSubmitted] = useState(false)
  const [serverErrors, setServerErrors] = useState<BoardFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  // 한 번 「등록」을 누른 뒤부터는 고칠 때마다 다시 검사해 안내를 바로 지운다
  const errors: BoardFormErrors = submitted ? { ...validateBoardForm(values), ...serverErrors } : {}

  const update = (patch: Partial<BoardFormValues>) => {
    setValues((prev) => ({ ...prev, ...patch }))
    setServerErrors({})
    setFormError(null)
  }

  // 결과물 자랑이 아니면 링크·사진은 보내지 않으므로, 넣어 둔 것이 있으면 알린다
  const showcaseWillClear =
    values.category !== null &&
    values.category !== 'SHOWCASE' &&
    (values.linkUrl.trim() !== '' || images.length > 0)

  const submit = () => {
    setSubmitted(true)
    setServerErrors({})
    setFormError(null)
    if (Object.keys(validateBoardForm(values)).length > 0) return

    createPost.mutate(toBoardRequest(values), {
      // ⑤ 등록하면 상세로. 결과물 사진이 있으면 다 올린 뒤에 간다 (BRD-006)
      onSuccess: ({ boardPostId }) => {
        const detailPath = `/board/${boardPostId}`
        if (values.category !== 'SHOWCASE' || images.length === 0) {
          navigate(detailPath, { replace: true })
          return
        }
        const goDetail = (failed: number) =>
          navigate(detailPath, {
            replace: true,
            state: failed > 0 ? { notice: POST_IMAGE_MESSAGES.failed } : undefined,
          })
        saveImages.mutate(
          { postId: boardPostId, saved: [], drafts: images },
          { onSuccess: ({ failed }) => goDetail(failed), onError: () => goDetail(1) },
        )
      },
      onError: (error) => {
        const { code, message } = getApiError(error)
        const fieldErrors = boardServerErrorToField(code, message)
        if (fieldErrors) setServerErrors(fieldErrors)
        else setFormError(message ?? '잠시 후 다시 시도해 주세요')
      },
    })
  }

  return (
    <main className={PAGE_CLASS}>
      <title>글쓰기 | 공방</title>
      <h1 className="text-title font-bold text-text">글쓰기</h1>
      <BoardForm
        values={values}
        errors={errors}
        onChange={update}
        images={images}
        onImagesChange={setImages}
        showcaseWillClear={showcaseWillClear}
        formError={formError}
        submitLabel="등록"
        submitting={createPost.isPending || saveImages.isPending}
        onSubmit={submit}
        onCancel={() => navigate('/board')}
      />
    </main>
  )
}
