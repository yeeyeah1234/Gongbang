import { Button } from '../../../components/ui/Button'
import { Modal } from '../../../components/ui/Modal'
import type { MyTeam } from '../types'

interface LeaveTeamModalProps {
  team: MyTeam | null
  // 팀장이라 나갈 수 없을 때 true (카드에서 팀장 클릭 또는 서버 409 LEADER_CANNOT_LEAVE)
  leaderBlocked: boolean
  isPending: boolean
  onClose: () => void
  onConfirmLeave: (team: MyTeam) => void
  onGoChat: (teamId: number) => void
}

/** WS-09 ②③ 팀 나가기 확인 모달 및 팀장 위임 안내 모달 */
export function LeaveTeamModal({
  team,
  leaderBlocked,
  isPending,
  onClose,
  onConfirmLeave,
  onGoChat,
}: LeaveTeamModalProps) {
  const handleClose = () => {
    if (!isPending) onClose()
  }

  if (team && leaderBlocked) {
    return (
      <Modal
        open
        size="sm"
        title="팀장은 바로 나갈 수 없어요"
        description="팀장은 다른 팀원에게 팀장 권한을 넘긴 뒤 나갈 수 있어요. 워크스페이스 채팅 탭의 「방 설정 / 팀원 관리」에서 다른 팀원에게 팀장 권한을 위임해 주세요."
        onClose={handleClose}
        footer={
          <>
            <Button variant="outline" size="md" onClick={handleClose}>
              닫기
            </Button>
            <Button variant="primary" size="md" onClick={() => onGoChat(team.teamId)}>
              채팅 탭으로 이동
            </Button>
          </>
        }
      />
    )
  }

  return (
    <Modal
      open={team !== null}
      size="sm"
      title={team ? `‘${team.name}’에서 나갈까요?` : ''}
      description="나가면 이 팀의 채팅 · 문서 · 화이트보드 · 캘린더 · 공지를 더 이상 볼 수 없어요. 내가 작성한 내용은 팀에 그대로 남아요."
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
              if (team) onConfirmLeave(team)
            }}
          >
            나가기
          </Button>
        </>
      }
    />
  )
}
