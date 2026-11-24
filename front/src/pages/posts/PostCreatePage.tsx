import { useState } from 'react'
import { useNavigate } from 'react-router'
import { getApiError } from '../../api/errors'
import { ContestPicker } from '../../features/post/components/ContestPicker'
import { PostForm } from '../../features/post/components/PostForm'
import { PostImagePicker } from '../../features/post/components/PostImagePicker'
import { useCreatePost, useSavePostImages } from '../../features/post/hooks'
import {
  EMPTY_POST_FORM,
  MESSAGES,
  toCreateRequest,
  todayString,
  validatePostForm,
} from '../../features/post/postForm'
import type { PostFormErrors, PostFormValues } from '../../features/post/postForm'
import { POST_IMAGE_MESSAGES } from '../../features/post/postImages'
import type { PostImageDraft } from '../../features/post/postImages'

// 서버가 막은 이유를 해당 칸 아래에 보여 준다 (PST-002 ⑥)
function serverErrorToField(code: string | null, message: string | null): PostFormErrors | null {
  switch (code) {
    case 'CONTEST_REQUIRED':
      return { contest: MESSAGES.contestRequired }
    case 'CONTEST_ENDED':
      return { contest: MESSAGES.contestEnded, externalDeadline: MESSAGES.contestEnded }
    case 'CONTEST_NOT_FOUND':
      return { contest: message ?? MESSAGES.contestRequired }
    case 'INVALID_PERIOD':
      return { period: MESSAGES.invalidPeriod }
    default:
      return null
  }
}

// PST-002 모집글 작성 / Figma 02-1·02-2·02-3. 로그인 회원만 들어온다 (RequireLogin).
export default function PostCreatePage() {
  const navigate = useNavigate()
  const createPost = useCreatePost()
  const saveImages = useSavePostImages()
  // PST-007 외부 공모전 사진 (등록한 뒤 차례로 올린다)
  const [images, setImages] = useState<PostImageDraft[]>([])
  // PST-002 ① 모집 시작일은 오늘로 채워 둔다 (오늘부터 모집하면 입력을 건너뛸 수 있게)
  const [values, setValues] = useState<PostFormValues>(() => ({
    ...EMPTY_POST_FORM,
    recruitStartDate: todayString(),
  }))
  const [submitted, setSubmitted] = useState(false)
  const [serverErrors, setServerErrors] = useState<PostFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  // 한 번 「등록」을 누른 뒤부터는 고칠 때마다 다시 검사해 안내를 바로 지운다
  const errors: PostFormErrors = submitted ? { ...validatePostForm(values), ...serverErrors } : {}

  const update = (patch: Partial<PostFormValues>) => {
    setValues((prev) => ({ ...prev, ...patch }))
    setServerErrors({})
    setFormError(null)
  }

  const submit = () => {
    setSubmitted(true)
    setServerErrors({})
    setFormError(null)
    if (Object.keys(validatePostForm(values)).length > 0) return

    createPost.mutate(toCreateRequest(values), {
      // ⑦ 등록하면 상세 화면으로. 외부 공모전 사진이 있으면 다 올린 뒤에 간다 (PST-007)
      onSuccess: ({ postId }) => {
        const detailPath = `/posts/${postId}`
        if (values.contestMode !== 'external' || images.length === 0) {
          navigate(detailPath, { replace: true })
          return
        }
        // 글은 이미 등록됐으므로 사진을 일부 못 올려도 상세로 가서 알린다
        const goDetail = (failed: number) =>
          navigate(detailPath, {
            replace: true,
            state: failed > 0 ? { notice: POST_IMAGE_MESSAGES.failed } : undefined,
          })
        saveImages.mutate(
          { postId, saved: [], drafts: images },
          { onSuccess: ({ failed }) => goDetail(failed), onError: () => goDetail(1) },
        )
      },
      onError: (error) => {
        const { code, message } = getApiError(error)
        const fieldErrors = serverErrorToField(code, message)
        if (fieldErrors) setServerErrors(fieldErrors)
        else setFormError(message ?? '잠시 후 다시 시도해 주세요')
      },
    })
  }

  return (
    <main className="mx-auto max-w-[1680px] px-6 py-12 lg:px-12 xl:px-[120px]">
      <title>모집글 작성 | 공방</title>
      <h1 className="text-title font-bold text-text">모집글 작성</h1>
      <PostForm
        contestField={<ContestPicker values={values} errors={errors} onChange={update} />}
        imageField={
          values.contestMode === 'external' && (
            <PostImagePicker drafts={images} onChange={setImages} />
          )
        }
        values={values}
        errors={errors}
        onChange={update}
        formError={formError}
        submitLabel="등록"
        submitting={createPost.isPending || saveImages.isPending}
        onSubmit={submit}
        onCancel={() => navigate('/posts')}
      />
    </main>
  )
}
