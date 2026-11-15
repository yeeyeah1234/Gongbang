import { Badge, type BadgeVariant } from '../../../components/ui/Badge'
import type { VerifyStatus } from '../types'

// MY-08 ③: 미인증(회색) → 확인 중 → 인증됨(초록 체크) / 반려. 모양은 공통 Badge(STYLE_GUIDE 5-3)
const LABELS: Record<VerifyStatus, { text: string; variant: BadgeVariant; className?: string }> = {
  NONE: { text: '미인증', variant: 'surface-sub' },
  PENDING: { text: '확인 중', variant: 'primary-soft' },
  // STYLE_GUIDE 1-2 success 색 (Badge에 success 종류가 없어 글자색만 바꾼다)
  VERIFIED: { text: '✓ 인증됨', variant: 'surface-sub', className: 'text-success' },
  REJECTED: { text: '반려', variant: 'danger-soft' },
}

export function VerifyBadge({ status }: { status: VerifyStatus }) {
  const label = LABELS[status]
  return (
    <Badge variant={label.variant} className={label.className}>
      {label.text}
    </Badge>
  )
}
