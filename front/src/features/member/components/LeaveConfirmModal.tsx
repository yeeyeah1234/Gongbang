import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'

// MY-01 ⑤ / Figma 20-3 저장 전 이탈 경고
export function LeaveConfirmModal({
  open,
  onStay,
  onLeave,
}: {
  open: boolean
  onStay: () => void
  onLeave: () => void
}) {
  return (
    <Modal
      open={open}
      size="md"
      title="저장하지 않은 내용이 있어요. 나갈까요?"
      description="저장하지 않고 나가면 지금까지 바꾼 내용이 사라져요."
      onClose={onStay}
      footer={
        <>
          <Button variant="outline" size="lg" onClick={onStay}>
            계속 수정하기
          </Button>
          <Button variant="primary" size="lg" onClick={onLeave}>
            나가기
          </Button>
        </>
      }
    />
  )
}
