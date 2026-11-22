import { Button } from '../../../../components/ui/Button'
import { Checkbox } from '../../../../components/ui/Checkbox'
import { Input } from '../../../../components/ui/Input'
import { Modal } from '../../../../components/ui/Modal'
import type { NoticeCreateRequest } from '../types'
import { CONTENT_MAX, isValidLength, textLength, TITLE_MAX } from '../validation'

interface NoticeFormModalProps {
  editing: boolean
  // 확인 모달과 바꿔 그려도 남도록 값과 touched는 부모가 들고 있는다
  values: NoticeCreateRequest
  touched: { title: boolean; content: boolean }
  busy: boolean
  onChange: (change: Partial<NoticeCreateRequest>) => void
  onSubmit: () => void
  onClose: () => void
}

// NTC-02 작성, NTC-03 수정 (작성 모달 600). 수정에는 「상단에 고정」이 없다
export function NoticeFormModal({
  editing,
  values,
  touched,
  busy,
  onChange,
  onSubmit,
  onClose,
}: NoticeFormModalProps) {
  const titleValid = isValidLength(values.title, TITLE_MAX)
  const contentValid = isValidLength(values.content, CONTENT_MAX)
  const titleError = touched.title && !titleValid
  const contentError = touched.content && !contentValid

  return (
    <Modal
      open
      size="lg"
      title={editing ? '공지 수정' : '공지 작성'}
      onClose={onClose}
      footer={
        <>
          <Button size="lg" disabled={busy} onClick={onClose}>
            취소
          </Button>
          <Button
            variant="primary"
            size="lg"
            loading={busy}
            disabled={!titleValid || !contentValid}
            onClick={onSubmit}
          >
            {editing ? '저장' : '등록'}
          </Button>
        </>
      }
    >
      {/* 공통 Modal에는 스크롤이 없어 낮은 화면에서 버튼이 밖으로 나간다. 본문만 스크롤 */}
      <div className="-mx-1 flex max-h-[calc(100dvh-16rem)] flex-col gap-4 overflow-y-auto px-1">
        <div className="relative shrink-0">
          <Input
            id="notice-title"
            label="제목"
            required
            autoFocus
            readOnly={busy}
            value={values.title}
            aria-invalid={titleError}
            aria-describedby="notice-title-count"
            errorText={titleError ? '제목은 1~50자로 입력해 주세요' : undefined}
            // 오류가 없어도 한 줄을 잡아 두어 글자 수 자리와 버튼이 움직이지 않게
            helperText={' '}
            onChange={(event) => onChange({ title: event.target.value })}
          />
          <span
            id="notice-title-count"
            className={[
              'absolute right-0 bottom-0 text-small',
              titleError ? 'text-danger' : 'text-text-sub',
            ].join(' ')}
          >
            {textLength(values.title)} / {TITLE_MAX}
          </span>
        </div>

        <div className="flex shrink-0 flex-col">
          <label htmlFor="notice-content" className="mb-2 text-body font-bold text-text">
            내용<span className="ml-1 font-normal text-text-sub">(필수)</span>
          </label>
          <textarea
            id="notice-content"
            required
            readOnly={busy}
            value={values.content}
            aria-invalid={contentError}
            aria-describedby="notice-content-count"
            onChange={(event) => onChange({ content: event.target.value })}
            className={[
              'h-40 w-full resize-none rounded-input bg-surface px-4 py-3 text-body text-text focus:outline-none',
              contentError
                ? 'border-2 border-danger'
                : 'border border-line-input focus:border-2 focus:border-primary',
            ].join(' ')}
          />
          <p className="mt-2 flex justify-between gap-2 text-small text-danger">
            <span>{contentError && '내용은 1~2,000자로 입력해 주세요'}</span>
            <span id="notice-content-count" className={contentError ? '' : 'text-text-sub'}>
              {textLength(values.content).toLocaleString()} / {CONTENT_MAX.toLocaleString()}
            </span>
          </p>
        </div>

        {!editing && (
          <>
            <div className="shrink-0">
              <Checkbox
                label="상단에 고정"
                checked={values.pinned}
                disabled={busy}
                onChange={(event) => onChange({ pinned: event.target.checked })}
              />
              <p className="mt-1 pl-7 text-small text-text-sub">
                이미 고정된 공지가 있으면 바꿀지 한 번 더 물어봐요
              </p>
            </div>
            {/* TODO(정진우): NoticeCreatedEvent 알림 리스너가 생겨야 아래 문구대로 알림이 간다 */}
            <p className="shrink-0 text-small text-text-sub">
              등록하면 팀장을 뺀 팀원 모두에게 알림이 가요.
            </p>
          </>
        )}
      </div>
    </Modal>
  )
}
