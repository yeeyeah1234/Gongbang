import { useCreateNotice, useUpdateNotice } from './hooks'
import type { NoticeCreateRequest, NoticeDetailData } from './types'
import type { NoticeDialog } from './useNoticeDialog'
import { CONTENT_MAX, isValidLength, strip, TITLE_MAX } from './validation'

interface NoticeEditorOptions {
  dialog: NoticeDialog
  basePath: string
  selectedId: number | null
  isWide: boolean
}

const EMPTY: NoticeCreateRequest = { title: '', content: '', pinned: false }

// NTC-02 작성, NTC-03 수정
export function useNoticeEditor({ dialog, basePath, selectedId, isWide }: NoticeEditorOptions) {
  const create = useCreateNotice()
  const update = useUpdateNotice()
  const { form, setForm } = dialog

  const openForm = (noticeId: number | null, values: NoticeCreateRequest) =>
    setForm({ noticeId, initial: values, values, touched: { title: false, content: false } })

  const openCreate = () => openForm(null, EMPTY)

  const openEdit = (notice: NoticeDetailData) =>
    openForm(notice.noticeId, {
      title: notice.title,
      // textarea가 CRLF를 LF로 바꾸므로 맞춰 둬야 손대지 않은 글이 바뀐 것으로 보이지 않는다
      content: notice.content.replace(/\r\n?/g, '\n'),
      pinned: false,
    })

  const change = (values: Partial<NoticeCreateRequest>) =>
    setForm(
      (current) =>
        current && {
          ...current,
          values: { ...current.values, ...values },
          touched: {
            title: current.touched.title || values.title !== undefined,
            content: current.touched.content || values.content !== undefined,
          },
        },
    )

  const saveNew = (values: NoticeCreateRequest) =>
    create.mutate(
      { title: strip(values.title), content: strip(values.content), pinned: values.pinned },
      {
        // PC의 빈 화면(/notice)에서는 뒤로 가기에 남기지 않고, 1024px 미만 목록에서는 뒤로 가기로 목록 복귀 허용
        onSuccess: (noticeId) =>
          dialog.leave(`${basePath}/${noticeId}`, isWide && selectedId === null),
        onError: (error) => dialog.fail(error),
      },
    )

  const submit = () => {
    if (!form) return
    const { noticeId, values } = form
    if (!isValidLength(values.title, TITLE_MAX) || !isValidLength(values.content, CONTENT_MAX))
      return
    if (!dialog.begin()) return

    if (noticeId === null) {
      if (values.pinned) void dialog.checkPinned(null, () => saveNew(values))
      else saveNew(values)
      return
    }
    update.mutate(
      { noticeId, body: { title: strip(values.title), content: strip(values.content) } },
      {
        onSuccess: () => {
          dialog.end()
          dialog.close()
        },
        onError: (error) => dialog.fail(error, noticeId),
      },
    )
  }

  // 「계속」: 폼으로 돌아가 loading
  const confirmReplace = () => {
    if (!form || !dialog.begin()) return
    dialog.setConfirm(null)
    saveNew(form.values)
  }

  return { openCreate, openEdit, change, submit, confirmReplace }
}
