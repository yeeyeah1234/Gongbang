import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import type { TermsType } from '../types'

// AUTH-04 ③ / Figma 13: 약관 전문 보기 모달
// TODO(확인 필요): 약관 전문은 법무 검토 전 임시 문구 (Figma 13 그대로). ⑤ 개인정보 수집 항목·30일 보관은 반영
const TERMS: Record<TermsType, { title: string; body: { heading: string; text: string }[] }> = {
  SERVICE: {
    title: '서비스 이용약관 (필수)',
    body: [
      {
        heading: '제1조 (목적)',
        text: "이 약관은 공방이 제공하는 공모전 정보 및 팀 빌딩 서비스(이하 '서비스')의 이용과 관련하여 공방과 회원 간의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.",
      },
      {
        heading: '제2조 (정의)',
        text: "'회원'이란 이 약관에 동의하고 회원가입을 완료한 자를 말합니다. '팀'이란 공모전 참가를 위해 회원이 구성한 그룹을 말합니다.",
      },
      {
        heading: '제3조 (약관의 효력 및 변경)',
        text: '공방은 약관을 변경할 경우 적용일 7일 전부터 서비스 내 공지사항을 통해 고지합니다. 회원에게 불리한 변경은 30일 전에 고지합니다.',
      },
      {
        heading: '제4조 (회원가입)',
        text: '회원가입은 이용자가 약관에 동의하고 이메일 인증을 완료한 뒤 공방이 승낙함으로써 성립합니다.',
      },
    ],
  },
  PRIVACY: {
    title: '개인정보 수집·이용 동의 (필수)',
    body: [
      {
        heading: '1. 수집 항목',
        text: '이메일, 비밀번호, 닉네임, 관심 분야(필수) / 희망 역할, 학교·전공·학년, 한 줄 소개, 기술 태그, 포트폴리오 링크, 이력(경력·자격증·수상), 증빙 파일(선택). 자격증·수상 인증 요청 때만 실명을 받으며 인증이 끝나면 지웁니다.',
      },
      {
        heading: '2. 이용 목적',
        text: '회원 식별, 팀원 모집·초대, 알림·메일 발송, 자격증·수상 인증.',
      },
      {
        heading: '3. 보관 기간',
        text: '탈퇴 신청 후 30일 동안 계정 복원을 위해 보관하고, 30일이 지나면 개인정보를 완전히 지웁니다.',
      },
    ],
  },
  MARKETING: {
    title: '마케팅 정보 수신 동의 (선택)',
    body: [
      {
        heading: '수신 내용',
        text: '새 기능·이벤트·추천 공모전 소식을 이메일로 보내 드립니다. 동의하지 않아도 가입할 수 있어요.',
      },
    ],
  },
}

export const TERMS_VERSION = '1.2'

interface TermsModalProps {
  type: TermsType | null
  onClose: () => void
  onAgree: (type: TermsType) => void
}

export function TermsModal({ type, onClose, onAgree }: TermsModalProps) {
  if (!type) return null
  const terms = TERMS[type]
  return (
    <Modal
      open
      title={terms.title}
      size="lg"
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" size="lg" onClick={onClose}>
            닫기
          </Button>
          <Button variant="primary" size="lg" onClick={() => onAgree(type)}>
            동의하고 닫기
          </Button>
        </>
      }
    >
      <p className="text-small text-text-sub">시행일 2026.11.01 · 버전 {TERMS_VERSION}</p>
      <div className="mt-4 max-h-[320px] overflow-y-auto rounded-input border border-line bg-bg p-5">
        {terms.body.map((section) => (
          <section key={section.heading} className="mb-4 last:mb-0">
            <h3 className="text-small font-bold text-text">{section.heading}</h3>
            <p className="mt-1 text-small text-text-sub">{section.text}</p>
          </section>
        ))}
      </div>
    </Modal>
  )
}
