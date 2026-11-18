import { useState } from 'react'
import { useNavigate } from 'react-router'
import { getApiError } from '../../api/errors'
import { ContestPicker } from '../../features/post/components/ContestPicker'
import { PostForm } from '../../features/post/components/PostForm'
import { useCreatePost } from '../../features/post/hooks'
import {
  EMPTY_POST_FORM,
  MESSAGES,
  toCreateRequest,
  validatePostForm,
} from '../../features/post/postForm'
import type { PostFormErrors, PostFormValues } from '../../features/post/postForm'

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
  const [values, setValues] = useState<PostFormValues>(EMPTY_POST_FORM)
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
      // ⑦ 등록하면 상세 화면으로
      onSuccess: ({ postId }) => navigate(`/posts/${postId}`, { replace: true }),
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
        values={values}
        errors={errors}
        onChange={update}
        formError={formError}
        submitLabel="등록"
        submitting={createPost.isPending}
        onSubmit={submit}
        onCancel={() => navigate('/posts')}
      />
    </main>
  )
}
