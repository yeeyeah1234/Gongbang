import { useState } from 'react'
import { Button } from '../../../components/ui/Button'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'
import type { TeamMember } from '../types'

interface InviteMemberModalProps {
  open: boolean
  onClose: () => void
  onInvite: (payload: { name: string; department: string }) => void
}

export function InviteMemberModal({ open, onClose, onInvite }: InviteMemberModalProps) {
  const [name, setName] = useState('')
  const [department, setDepartment] = useState('')
  const [error, setError] = useState<string | undefined>()

  const handleClose = () => {
    setName('')
    setDepartment('')
    setError(undefined)
    onClose()
  }

  const handleConfirm = () => {
    const trimmedName = name.trim()
    if (!trimmedName) {
      setError('초대할 팀원의 이름을 입력해 주세요')
      return
    }
    onInvite({
      name: trimmedName,
      department: department.trim() || '팀원',
    })
    handleClose()
  }

  return (
    <Modal
      open={open}
      size="md"
      title="새 팀원 초대하기"
      description="워크스페이스 채팅방에 참여할 팀원 정보를 입력해 주세요."
      onClose={handleClose}
      footer={
        <>
          <Button variant="outline" size="lg" onClick={handleClose}>
            취소
          </Button>
          <Button variant="primary" size="lg" onClick={handleConfirm}>
            초대하기
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Input
          id="invite-member-name"
          label="팀원 이름"
          required
          placeholder="예: 정진우"
          value={name}
          onChange={(event) => {
            setName(event.target.value)
            if (error) setError(undefined)
          }}
          errorText={error}
        />
        <Input
          id="invite-member-role"
          label="담당 역할"
          placeholder="예: 백엔드 개발"
          value={department}
          onChange={(event) => setDepartment(event.target.value)}
        />
      </div>
    </Modal>
  )
}

interface KickMemberModalProps {
  member: TeamMember | null
  onClose: () => void
  onConfirmKick: (member: TeamMember) => void
}

export function KickMemberModal({ member, onClose, onConfirmKick }: KickMemberModalProps) {
  return (
    <Modal
      open={member !== null}
      size="sm"
      title={`${member?.name ?? ''}님을 강제 퇴장할까요?`}
      description="채팅방에서 즉시 내보내며, 되돌릴 수 없어요."
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" size="lg" onClick={onClose}>
            취소
          </Button>
          <Button
            variant="danger"
            size="lg"
            onClick={() => {
              if (member) {
                onConfirmKick(member)
              }
            }}
          >
            강제 퇴장
          </Button>
        </>
      }
    />
  )
}

interface DeleteRoomModalProps {
  open: boolean
  roomName: string
  onClose: () => void
  onConfirmDelete: () => void
}

export function DeleteRoomModal({
  open,
  roomName,
  onClose,
  onConfirmDelete,
}: DeleteRoomModalProps) {
  return (
    <Modal
      open={open}
      size="sm"
      title="채팅방을 삭제할까요?"
      description={`「${roomName}」 대화 내역이 모두 사라지며 되돌릴 수 없어요.`}
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" size="lg" onClick={onClose}>
            취소
          </Button>
          <Button variant="danger" size="lg" onClick={onConfirmDelete}>
            삭제
          </Button>
        </>
      }
    />
  )
}

