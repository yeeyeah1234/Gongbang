import { PlusIcon } from '../../../../components/icons/Icons'
import { Button } from '../../../../components/ui/Button'
import { DocumentIcon } from '../../components/WorkspaceIcons'

interface DocumentEmptyStateProps {
  creating: boolean
  onCreate: () => void
}

// DOC-01 빈 상태
export function DocumentEmptyState({ creating, onCreate }: DocumentEmptyStateProps) {
  return (
    <section className="flex flex-1 flex-col items-center justify-center gap-4 rounded-card border border-line bg-surface p-12 text-center">
      <DocumentIcon className="h-12 w-12 text-text-disabled" />
      <p className="text-body text-text-sub">아직 문서가 없어요. 첫 문서를 만들어 보세요</p>
      <Button variant="primary" disabled={creating} loading={creating} onClick={onCreate}>
        <PlusIcon />새 문서
      </Button>
    </section>
  )
}
