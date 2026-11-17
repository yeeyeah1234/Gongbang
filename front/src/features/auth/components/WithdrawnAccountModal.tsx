import { useNavigate } from 'react-router'
import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'

interface WithdrawnAccountModalProps {
  open: boolean
  email: string
  message: string | null // 서버 문구: 「탈퇴 신청한 계정이에요. 복원할까요? (N일 남음)」
  onClose: () => void
  // [새로 가입하기] (AUTH-05 ⑦). 로그인 화면처럼 새 가입이 필요 없으면 생략
  onSignupNew?: () => void
}

// Figma 20-1 공통 알럿 · 탈퇴 계정 복원 / 새로 가입 (AUTH-01 ⑤, AUTH-05 ⑦, MY-11 ⑤)
export function WithdrawnAccountModal({
  open,
  email,
  message,
  onClose,
  onSignupNew,
}: WithdrawnAccountModalProps) {
  const navigate = useNavigate()
  return (
    <Modal
      open={open}
      size="md"
      title="탈퇴한 계정이 있어요"
      onClose={onClose}
      footer={
        <>
          {onSignupNew && (
            <Button variant="outline" size="lg" onClick={onSignupNew}>
              새로 가입하기
            </Button>
          )}
          <Button
            variant="primary"
            size="lg"
            onClick={() => navigate(`/restore?email=${encodeURIComponent(email)}`)}
          >
            계정 복원하기
          </Button>
        </>
      }
    >
      <div className="flex flex-col items-center text-center">
        <span className="flex size-12 items-center justify-center rounded-full bg-surface-sub text-heading font-bold text-text">
          !
        </span>
        <p className="mt-4 text-body text-text-sub">
          {message ?? '탈퇴 신청한 계정이에요. 복원할까요?'}
        </p>
        {onSignupNew && (
          <p className="mt-2 text-small text-danger">
            새로 가입하면 이전 계정의 프로필·이력·북마크가 모두 지워져요
          </p>
        )}
      </div>
    </Modal>
  )
}
