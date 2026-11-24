import { PlusIcon } from '../../../../components/icons/Icons'
import { Button } from '../../../../components/ui/Button'
import { BoardIcon } from './BoardIcon'

interface BoardEmptyStateProps {
  creating: boolean
  onCreate: () => void
}

// WB-01 빈 상태 (DOC-01 ⑤와 같은 모양)
export function BoardEmptyState({ creating, onCreate }: BoardEmptyStateProps) {
  return (
    <section className="flex flex-1 flex-col items-center justify-center gap-4 rounded-card border border-line bg-surface p-12 text-center">
      <BoardIcon className="h-12 w-12 text-text-disabled" />
      <p className="text-body text-text-sub">아직 보드가 없어요</p>
      <Button variant="primary" disabled={creating} loading={creating} onClick={onCreate}>
        <PlusIcon />새 보드
      </Button>
    </section>
  )
}
