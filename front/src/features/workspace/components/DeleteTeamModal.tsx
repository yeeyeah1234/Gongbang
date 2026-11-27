import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import type { MyTeam } from '../types'

interface DeleteTeamModalProps {
  team: MyTeam | null
  isPending: boolean
  onClose: () => void
  onConfirmDelete: (team: MyTeam) => void
}

/** WS-08 ⑨ 팀 삭제 확인 모달 (팀장 혼자 남은 팀만 삭제 가능) */
export function DeleteTeamModal({
  team,
  isPending,
  onClose,
  onConfirmDelete,
}: DeleteTeamModalProps) {
  const handleClose = () => {
    if (!isPending) onClose()
  }

  return (
    <Modal
      open={team !== null}
      size="sm"
      title={team ? `‘${team.name}’을 삭제할까요?` : ''}
      description="팀 워크스페이스의 문서·화이트보드·일정·공지를 더 이상 볼 수 없어요. 연결된 모집글도 함께 마감돼요. 삭제하면 되돌릴 수 없어요."
      onClose={handleClose}
      footer={
        <>
          <Button variant="outline" size="md" disabled={isPending} onClick={handleClose}>
            취소
          </Button>
          <Button
            variant="danger"
            size="md"
            loading={isPending}
            onClick={() => {
              if (team) onConfirmDelete(team)
            }}
          >
            삭제
          </Button>
        </>
      }
    />
  )
}
