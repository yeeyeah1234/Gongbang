import { Button } from '../../../components/ui/Button'
import { LockIcon } from './WorkspaceIcons'

interface WorkspaceNoAccessProps {
  onGoList: () => void
}

// WS-01 ② 403 NOT_TEAM_MEMBER: 팀 이름 등 팀 정보는 보여 주지 않는다 (헤더·탭도 없음)
export function WorkspaceNoAccess({ onGoList }: WorkspaceNoAccessProps) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-bg px-6">
      <section className="flex w-full max-w-[480px] flex-col items-center gap-4 rounded-card border border-line bg-surface px-8 py-12 text-center">
        <span className="flex h-16 w-16 items-center justify-center rounded-full bg-surface-sub text-text-sub">
          <LockIcon className="h-8 w-8" />
        </span>
        <h1 className="text-heading font-bold text-text">이 워크스페이스에 접근할 권한이 없어요</h1>
        <p className="text-body text-text-sub">
          팀원만 들어올 수 있어요. 팀에서 나갔거나 초대받지 않은 워크스페이스일 수 있어요.
        </p>
        <Button variant="primary" size="md" className="mt-2" onClick={onGoList}>
          워크스페이스 목록으로
        </Button>
      </section>
    </main>
  )
}
