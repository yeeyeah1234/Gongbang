import { useCallback, useState } from 'react'
import { PlusIcon } from '../../../components/icons/Icons'
import { Button } from '../../../components/ui/Button'
import { NoticeError, NoticeSkeleton } from '../notice/components/NoticeStates'
import { useMinuteTick } from './relativeTime'
import type { WorkspaceSidebarItem, WorkspaceSidebarLabels } from './types'
import { WorkspaceItemRow } from './WorkspaceItemRow'

interface WorkspaceItemSidebarProps {
  items: WorkspaceSidebarItem[] | undefined
  isError: boolean
  basePath: string
  selectedId: number | null
  renamingId: number | null
  creating: boolean
  renaming: boolean
  labels: WorkspaceSidebarLabels
  maxTitleLength?: number
  onRetry: () => void
  onCreate: () => void
  onStartRename: (id: number) => void
  onSubmitRename: (id: number, title: string) => void
  onCancelRename: () => void
  onRequestDelete: (item: WorkspaceSidebarItem) => void
}

// DOC-01·WB-01 왼쪽 사이드바 공통 틀 (280px). 라벨과 API 동작은 바깥에서 주입한다
export function WorkspaceItemSidebar({
  items,
  isError,
  basePath,
  selectedId,
  renamingId,
  creating,
  renaming,
  labels,
  maxTitleLength,
  onRetry,
  onCreate,
  onStartRename,
  onSubmitRename,
  onCancelRename,
  onRequestDelete,
}: WorkspaceItemSidebarProps) {
  const [openMenuId, setOpenMenuId] = useState<number | null>(null)
  const now = useMinuteTick()

  const closeMenu = useCallback(() => setOpenMenuId(null), [])
  const toggleMenu = useCallback(
    (id: number) => setOpenMenuId((current) => (current === id ? null : id)),
    [],
  )

  return (
    <aside
      aria-label={labels.navAriaLabel}
      className="flex flex-1 flex-col bg-surface lg:min-h-0 lg:w-[280px] lg:flex-none lg:shrink-0 lg:border-r lg:border-line"
    >
      <div className="flex shrink-0 items-center justify-between gap-2 border-b border-divider px-4 py-3.5">
        <h2 className="text-subheading font-bold text-text">{labels.heading}</h2>
        <Button
          variant="primary"
          size="sm"
          disabled={creating}
          loading={creating}
          onClick={onCreate}
        >
          <PlusIcon />
          {labels.createButton}
        </Button>
      </div>

      <div onScroll={closeMenu} className="relative min-h-0 flex-1 overflow-y-auto lg:p-2">
        {items ? (
          <ul className="flex flex-col lg:gap-1">
            {items.map((item) => (
              <WorkspaceItemRow
                key={item.id}
                item={item}
                to={`${basePath}/${item.id}`}
                selected={item.id === selectedId}
                editing={item.id === renamingId}
                renaming={renaming && item.id === renamingId}
                menuOpen={openMenuId === item.id && item.id !== renamingId}
                now={now}
                labels={labels}
                maxTitleLength={maxTitleLength}
                onToggleMenu={toggleMenu}
                onCloseMenu={closeMenu}
                onStartRename={onStartRename}
                onSubmitRename={onSubmitRename}
                onCancelRename={onCancelRename}
                onRequestDelete={onRequestDelete}
              />
            ))}
          </ul>
        ) : isError ? (
          <NoticeError onRetry={onRetry} />
        ) : (
          <NoticeSkeleton />
        )}
      </div>
    </aside>
  )
}
