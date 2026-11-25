export type ReportTargetType = 'MEMBER' | 'COMMENT' | 'MESSAGE' | 'POST' | 'BOARD_POST'

export type ReportReasonCode = 'ABUSE' | 'HATE' | 'BAD_NICKNAME' | 'SPAM' | 'INAPPROPRIATE'

export const REPORT_REASON_OPTIONS: ReadonlyArray<{
  code: ReportReasonCode
  label: string
}> = [
  { code: 'ABUSE', label: '욕설 / 비방 / 모욕' },
  { code: 'HATE', label: '차별 / 혐오 표현' },
  { code: 'BAD_NICKNAME', label: '부적절한 닉네임' },
  { code: 'SPAM', label: '광고 / 도배' },
  { code: 'INAPPROPRIATE', label: '부적절한 내용' },
]

export interface CreateReportRequest {
  reportedMemberId: number
  targetType: ReportTargetType
  targetId?: number | null
  reasonCode: ReportReasonCode
  detail?: string | null
}

export interface CreateReportResponse {
  reportId: number
}
