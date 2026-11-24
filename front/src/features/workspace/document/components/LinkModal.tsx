import { useState, type FormEvent } from 'react'
import { Button } from '../../../../components/ui/Button'
import { Input } from '../../../../components/ui/Input'
import { Modal } from '../../../../components/ui/Modal'
import { normalizeSafeHttpUrl } from '../urlSafety'

interface LinkModalProps {
  open: boolean
  initialUrl: string
  hasExistingLink: boolean
  onSubmit: (url: string) => void
  onRemove: () => void
  onClose: () => void
}

// DOC-06 / AGENTS.md 9: http:// 또는 https:// 주소만 허용하는 링크 입력 모달
export function LinkModal({
  open,
  initialUrl,
  hasExistingLink,
  onSubmit,
  onRemove,
  onClose,
}: LinkModalProps) {
  if (!open) return null

  return (
    <LinkModalContent
      initialUrl={initialUrl}
      hasExistingLink={hasExistingLink}
      onSubmit={onSubmit}
      onRemove={onRemove}
      onClose={onClose}
    />
  )
}

function LinkModalContent({
  initialUrl,
  hasExistingLink,
  onSubmit,
  onRemove,
  onClose,
}: Omit<LinkModalProps, 'open'>) {
  const [value, setValue] = useState(initialUrl)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const safeUrl = normalizeSafeHttpUrl(value)
    if (!safeUrl) {
      setError('http:// 또는 https://로 시작하는 올바른 주소를 입력해 주세요.')
      return
    }
    onSubmit(safeUrl)
  }

  return (
    <Modal
      open
      size="sm"
      title={hasExistingLink ? '링크 수정' : '링크 삽입'}
      onClose={onClose}
      footer={
        <>
          {hasExistingLink && (
            <Button variant="danger" size="sm" onClick={onRemove} className="mr-auto">
              링크 해제
            </Button>
          )}
          <Button variant="outline" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" form="doc-link-form" variant="primary" size="sm">
            적용
          </Button>
        </>
      }
    >
      <form id="doc-link-form" onSubmit={handleSubmit}>
        <Input
          id="doc-link-url"
          label="링크 주소"
          type="url"
          autoFocus
          placeholder="https://example.com"
          value={value}
          errorText={error ?? undefined}
          helperText="http:// 또는 https:// 주소만 넣을 수 있어요."
          onChange={(event) => {
            setValue(event.target.value)
            if (error) setError(null)
          }}
        />
      </form>
    </Modal>
  )
}
