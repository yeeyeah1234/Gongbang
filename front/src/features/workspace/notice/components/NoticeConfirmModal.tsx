import { Button } from '../../../../components/ui/Button'
import { Modal } from '../../../../components/ui/Modal'

interface NoticeConfirmModalProps {
  title: string
  // 공지 제목이 들어가면 길어서 모달 제목이 아니라 본문에서 줄바꿈한다
  question: string
  note?: string
  confirmLabel: string
  danger?: boolean
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}

// NTC-02~04 닫기·고정 교체·삭제 확인 (워크스페이스 안 작은 확인 모달 460)
export function NoticeConfirmModal({
  title,
  question,
  note,
  confirmLabel,
  danger = false,
  busy = false,
  onConfirm,
  onCancel,
}: NoticeConfirmModalProps) {
  return (
    <Modal
      open
      size="sm"
      title={title}
      onClose={() => {
        if (!busy) onCancel()
      }}
      footer={
        <>
          <Button size="lg" disabled={busy} onClick={onCancel}>
            취소
          </Button>
          <Button
            variant={danger ? 'danger' : 'primary'}
            size="lg"
            loading={busy}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p className="text-body wrap-anywhere text-text">{question}</p>
      {note && <p className="mt-2 text-body text-text-sub">{note}</p>}
    </Modal>
  )
}
