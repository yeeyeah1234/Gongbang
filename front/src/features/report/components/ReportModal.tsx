import { useEffect, useRef, useState } from 'react'
import { getErrorMessage } from '../../../api/errors'
import { ChevronDownIcon, CloseIcon } from '../../../components/icons/Icons'
import { useCreateReportMutation } from '../hooks'
import { REPORT_REASON_OPTIONS, type ReportReasonCode, type ReportTargetType } from '../types'

interface ReportModalProps {
  open: boolean
  reportedMemberId: number | null
  targetNickname?: string | null
  targetType: ReportTargetType
  targetId?: number | null
  onClose: () => void
  onSubmitted?: (message: string) => void
}

/** NET-02 신고 사유 선택 및 접수 모달 (`POST /api/reports`). */
export function ReportModal({
  open,
  reportedMemberId,
  targetNickname,
  targetType,
  targetId = null,
  onClose,
  onSubmitted,
}: ReportModalProps) {
  const [reasonCode, setReasonCode] = useState<ReportReasonCode | ''>('')
  const [detail, setDetail] = useState('')
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [error, setError] = useState<string | undefined>()
  const dropdownRef = useRef<HTMLDivElement | null>(null)

  const reportMutation = useCreateReportMutation()

  const resetAndClose = () => {
    setReasonCode('')
    setDetail('')
    setDropdownOpen(false)
    setError(undefined)
    reportMutation.reset()
    onClose()
  }

  useEffect(() => {
    if (!open || reportedMemberId === null) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (dropdownOpen) {
          setDropdownOpen(false)
          return
        }
        setReasonCode('')
        setDetail('')
        setDropdownOpen(false)
        setError(undefined)
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, reportedMemberId, dropdownOpen, onClose])

  useEffect(() => {
    if (!dropdownOpen) return
    const handleOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleOutside)
    return () => document.removeEventListener('mousedown', handleOutside)
  }, [dropdownOpen])

  if (!open || reportedMemberId === null) return null

  const selectedLabel =
    REPORT_REASON_OPTIONS.find((option) => option.code === reasonCode)?.label ?? ''

  const handleConfirm = () => {
    if (!reasonCode) {
      setError('신고사유를 선택해 주세요')
      return
    }

    reportMutation.mutate(
      {
        reportedMemberId,
        targetType,
        targetId,
        reasonCode,
        detail: detail.trim() || null,
      },
      {
        onSuccess: () => {
          const subject = targetNickname ? `${targetNickname}님에 대한 ` : ''
          resetAndClose()
          onSubmitted?.(`${subject}신고가 접수되었습니다`)
        },
        onError: (err) => {
          setError(getErrorMessage(err, '신고를 접수하지 못했어요. 잠시 후 다시 시도해 주세요'))
        },
      },
    )
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="report-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-text/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !reportMutation.isPending) {
          resetAndClose()
        }
      }}
    >
      <div className="relative min-h-[140px] w-full max-w-[535px] rounded-[12px] border border-line-strong bg-surface px-4 pt-[52px] pb-[18px] shadow-modal">
        <button
          type="button"
          onClick={resetAndClose}
          disabled={reportMutation.isPending}
          aria-label="닫기"
          className="absolute top-2.5 right-3 flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-text transition-colors hover:bg-bg disabled:cursor-not-allowed disabled:opacity-50"
        >
          <CloseIcon className="h-4 w-4" />
        </button>

        <p id="report-modal-title" className="text-[14px] leading-[17px] text-text">
          {targetNickname
            ? `${targetNickname}님 신고 사유 선택 및 입력`
            : '부적절한 유저 신고 사유 선택 및 입력'}
        </p>

        <div className="mt-3 flex items-start gap-[14px]">
          <div ref={dropdownRef} className="relative flex-1">
            <button
              type="button"
              aria-haspopup="listbox"
              aria-expanded={dropdownOpen}
              disabled={reportMutation.isPending}
              onClick={() => setDropdownOpen((prev) => !prev)}
              className="flex h-[40px] w-full cursor-pointer items-center justify-between rounded-[8px] border border-line-strong bg-surface-sub px-[14px] text-left text-[13px] leading-[16px] transition-colors hover:bg-bg disabled:cursor-not-allowed disabled:opacity-50"
            >
              <span className={selectedLabel ? 'text-text' : 'text-text-sub'}>
                {selectedLabel || '신고사유 선택'}
              </span>
              <ChevronDownIcon
                className={[
                  'h-4 w-4 shrink-0 text-text-sub transition-transform',
                  dropdownOpen ? 'rotate-180' : '',
                ].join(' ')}
              />
            </button>

            {dropdownOpen && (
              <div
                role="listbox"
                aria-label="신고사유 목록"
                className="absolute top-0 left-0 z-20 w-full rounded-[8px] border border-line-strong bg-surface-sub shadow-pop"
              >
                <button
                  type="button"
                  onClick={() => setDropdownOpen(false)}
                  className="flex h-[40px] w-full cursor-pointer items-center justify-between px-[14px] text-left text-[13px] leading-[16px]"
                >
                  <span className={selectedLabel ? 'text-text' : 'text-text-sub'}>
                    {selectedLabel || '신고사유 선택'}
                  </span>
                  <ChevronDownIcon className="h-4 w-4 shrink-0 rotate-180 text-text-sub" />
                </button>

                {REPORT_REASON_OPTIONS.map((option) => (
                  <div key={option.code}>
                    <div className="mx-4 border-t border-line-strong" />
                    <button
                      type="button"
                      role="option"
                      aria-selected={reasonCode === option.code}
                      onClick={() => {
                        setReasonCode(option.code)
                        setDropdownOpen(false)
                        if (error) setError(undefined)
                      }}
                      className={[
                        'flex h-[45px] w-full cursor-pointer items-center px-[14px] text-left text-[13px] leading-[16px] transition-colors hover:bg-bg hover:text-text',
                        reasonCode === option.code ? 'font-bold text-text' : 'text-text-sub',
                      ].join(' ')}
                    >
                      {option.label}
                    </button>
                  </div>
                ))}
              </div>
            )}

            {error && <p className="mt-1.5 text-caption text-danger">{error}</p>}
          </div>

          <button
            type="button"
            disabled={reportMutation.isPending}
            onClick={handleConfirm}
            className="h-[40px] w-[98px] shrink-0 cursor-pointer rounded-[8px] border border-line-strong bg-surface-sub text-[13px] leading-[16px] font-bold text-text transition-colors hover:bg-divider disabled:cursor-not-allowed disabled:opacity-50"
          >
            {reportMutation.isPending ? '접수 중…' : '확인/전송'}
          </button>
        </div>

        {reasonCode && (
          <div className="mt-3">
            <textarea
              id="report-member-detail"
              aria-label="신고 상세 내용"
              rows={3}
              maxLength={500}
              disabled={reportMutation.isPending}
              placeholder="신고 사항을 구체적으로 입력해 주세요 (선택)"
              value={detail}
              onChange={(event) => setDetail(event.target.value)}
              className="w-full resize-none rounded-[8px] border border-line-strong bg-surface-sub px-[14px] py-2.5 text-[13px] leading-[18px] text-text placeholder:text-text-sub focus:border-primary focus:outline-none disabled:opacity-50"
            />
            <div className="mt-1 flex justify-end">
              <span className="text-caption text-text-sub">{detail.length}/500</span>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
