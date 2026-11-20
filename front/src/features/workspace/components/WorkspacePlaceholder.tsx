import { WORKSPACE_TABS, type WorkspaceTabKey } from '../types'

interface WorkspacePlaceholderProps {
  tab: WorkspaceTabKey
}

// 아직 화면이 붙지 않은 탭의 빈 자리. 담당자가 router.tsx에서 이 element를 자기 화면으로 바꾼다
export function WorkspacePlaceholder({ tab }: WorkspacePlaceholderProps) {
  const label = WORKSPACE_TABS.find((item) => item.key === tab)?.label ?? ''
  return (
    <section className="flex flex-1 flex-col items-center justify-center gap-3 rounded-card border border-line bg-surface p-12 text-center">
      <h2 className="text-heading font-bold text-text">{label}</h2>
      <p className="text-body text-text-sub">{label} 화면은 준비 중이에요.</p>
    </section>
  )
}
