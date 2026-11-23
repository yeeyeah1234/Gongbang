import { useEffect, useRef, useState } from 'react'
import { ChevronDownIcon, CloseIcon } from '../../../components/icons/Icons'
import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import type { TeamMember } from '../types'

interface KickMemberModalProps {
  member: TeamMember | null
  isPending?: boolean
  onClose: () => void
  onConfirmKick: (member: TeamMember) => void
}

export function KickMemberModal({
  member,
  isPending = false,
  onClose,
  onConfirmKick,
}: KickMemberModalProps) {
  return (
    <Modal
      open={member !== null}
      size="sm"
      title={`${member?.name ?? ''}님을 강제 퇴장할까요?`}
      description="팀 및 모든 채팅방·문서에서 즉시 내보내며, 되돌릴 수 없어요."
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" size="lg" disabled={isPending} onClick={onClose}>
            취소
          </Button>
          <Button
            variant="danger"
            size="lg"
            disabled={isPending}
            onClick={() => {
              if (member) {
                onConfirmKick(member)
              }
            }}
          >
            {isPending ? '처리 중…' : '강제 퇴장'}
          </Button>
        </>
      }
    />
  )
}

interface DelegateLeaderModalProps {
  member: TeamMember | null
  isPending?: boolean
  onClose: () => void
  onConfirmDelegate: (member: TeamMember) => void
}

export function DelegateLeaderModal({
  member,
  isPending = false,
  onClose,
  onConfirmDelegate,
}: DelegateLeaderModalProps) {
  return (
    <Modal
      open={member !== null}
      size="sm"
      title={`${member?.name ?? ''}님에게 팀장 권한을 위임할까요?`}
      description="권한을 위임하면 본인은 일반 팀원으로 변경됩니다."
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" size="lg" disabled={isPending} onClick={onClose}>
            취소
          </Button>
          <Button
            variant="primary"
            size="lg"
            disabled={isPending}
            onClick={() => {
              if (member) {
                onConfirmDelegate(member)
              }
            }}
          >
            {isPending ? '처리 중…' : '위임하기'}
          </Button>
        </>
      }
    />
  )
}

const REPORT_REASONS = ['욕설 / 비방 / 모욕', '차별 / 혐오 표현', '부적절한 닉네임'] as const

interface ReportMemberModalProps {
  member: TeamMember | null
  onClose: () => void
  onConfirmReport: (member: TeamMember, reason: string, detail: string) => void
}

export function ReportMemberModal({ member, onClose, onConfirmReport }: ReportMemberModalProps) {
  const [selectedReason, setSelectedReason] = useState('')
  const [detail, setDetail] = useState('')
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [error, setError] = useState<string | undefined>()
  const dropdownRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!member) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (dropdownOpen) {
          setDropdownOpen(false)
          return
        }
        setSelectedReason('')
        setDetail('')
        setDropdownOpen(false)
        setError(undefined)
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [member, dropdownOpen, onClose])

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

  if (!member) return null

  const handleClose = () => {
    setSelectedReason('')
    setDetail('')
    setDropdownOpen(false)
    setError(undefined)
    onClose()
  }

  const handleConfirm = () => {
    if (!selectedReason) {
      setError('신고사유를 선택해 주세요')
      return
    }
    onConfirmReport(member, selectedReason, detail.trim())
    handleClose()
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="report-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-text/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          handleClose()
        }
      }}
    >
      <div className="relative min-h-[140px] w-full max-w-[535px] rounded-[12px] border border-line-strong bg-surface px-4 pt-[52px] pb-[18px] shadow-modal">
        <button
          type="button"
          onClick={handleClose}
          aria-label="닫기"
          className="absolute top-2.5 right-3 flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-text transition-colors hover:bg-bg"
        >
          <CloseIcon className="h-4 w-4" />
        </button>

        <p id="report-modal-title" className="text-[14px] leading-[17px] text-text">
          부적절한 유저 신고 사유 선택 및 입력
        </p>

        <div className="mt-3 flex items-start gap-[14px]">
          <div ref={dropdownRef} className="relative flex-1">
            <button
              type="button"
              aria-haspopup="listbox"
              aria-expanded={dropdownOpen}
              onClick={() => setDropdownOpen((prev) => !prev)}
              className="flex h-[40px] w-full cursor-pointer items-center justify-between rounded-[8px] border border-line-strong bg-surface-sub px-[14px] text-left text-[13px] leading-[16px] transition-colors hover:bg-bg"
            >
              <span className={selectedReason ? 'text-text' : 'text-text-sub'}>
                {selectedReason || '신고사유 선택'}
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
                  <span className={selectedReason ? 'text-text' : 'text-text-sub'}>
                    {selectedReason || '신고사유 선택'}
                  </span>
                  <ChevronDownIcon className="h-4 w-4 shrink-0 rotate-180 text-text-sub" />
                </button>

                {REPORT_REASONS.map((option) => (
                  <div key={option}>
                    <div className="mx-4 border-t border-line-strong" />
                    <button
                      type="button"
                      role="option"
                      aria-selected={selectedReason === option}
                      onClick={() => {
                        setSelectedReason(option)
                        setDropdownOpen(false)
                        if (error) setError(undefined)
                      }}
                      className={[
                        'flex h-[45px] w-full cursor-pointer items-center px-[14px] text-left text-[13px] leading-[16px] transition-colors hover:bg-bg hover:text-text',
                        selectedReason === option ? 'font-bold text-text' : 'text-text-sub',
                      ].join(' ')}
                    >
                      {option}
                    </button>
                  </div>
                ))}
              </div>
            )}

            {error && <p className="mt-1.5 text-caption text-danger">{error}</p>}
          </div>

          <button
            type="button"
            onClick={handleConfirm}
            className="h-[40px] w-[98px] shrink-0 cursor-pointer rounded-[8px] border border-line-strong bg-surface-sub text-[13px] leading-[16px] font-bold text-text transition-colors hover:bg-divider"
          >
            확인/전송
          </button>
        </div>

        {selectedReason && (
          <div className="mt-3">
            <textarea
              id="report-member-detail"
              aria-label="신고 상세 내용"
              rows={3}
              maxLength={500}
              placeholder="신고 사항을 구체적으로 입력해 주세요 (선택)"
              value={detail}
              onChange={(event) => setDetail(event.target.value)}
              className="w-full resize-none rounded-[8px] border border-line-strong bg-surface-sub px-[14px] py-2.5 text-[13px] leading-[18px] text-text placeholder:text-text-sub focus:border-primary focus:outline-none"
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
