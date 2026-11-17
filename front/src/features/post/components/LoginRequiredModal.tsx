import { useNavigate } from 'react-router'
import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import { loginPathWithRedirect } from '../../auth/redirect'

interface LoginRequiredModalProps {
  open: boolean
  onClose: () => void
  // 로그인한 뒤 돌아올 주소 (AUTH-01 ⑥)
  redirectTo: string
}

// AUTH-12 ②: 비회원이 글쓰기·지원하기 등을 누르면 「로그인이 필요해요」 모달 + [로그인] (PST-001 ⑥, PST-006 ①)
export function LoginRequiredModal({ open, onClose, redirectTo }: LoginRequiredModalProps) {
  const navigate = useNavigate()

  return (
    <Modal
      open={open}
      title="로그인이 필요해요"
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" size="lg" onClick={onClose}>
            취소
          </Button>
          <Button
            variant="primary"
            size="lg"
            onClick={() => navigate(loginPathWithRedirect(redirectTo))}
          >
            로그인
          </Button>
        </>
      }
    />
  )
}
