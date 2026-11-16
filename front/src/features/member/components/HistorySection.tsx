import type { ReactNode } from 'react'
import { Button } from '../../../components/ui/Button'

interface HistoryRow {
  key: number
  title: ReactNode
  sub: string
  onEdit?: () => void
  onDelete?: () => void
}

// Figma 08-1 오른쪽 카드(경력·자격증·수상 경력): 제목 + [+ 추가], 항목마다 [수정] [삭제]
export function HistorySection({
  title,
  rows,
  onAdd,
  emptyText = '아직 등록한 이력이 없어요. [+ 추가]로 입력해 보세요.',
}: {
  title: string
  rows: HistoryRow[]
  onAdd?: () => void
  emptyText?: string
}) {
  return (
    <section className="rounded-card border border-line bg-surface">
      <header className="flex items-center justify-between border-b border-divider px-6 py-4">
        <h2 className="text-body font-bold text-text">{title}</h2>
        {onAdd && (
          <Button variant="outline" size="sm" onClick={onAdd}>
            + 추가
          </Button>
        )}
      </header>
      {rows.length === 0 ? (
        <p className="px-6 py-6 text-center text-small text-text-sub">{emptyText}</p>
      ) : (
        <ul>
          {rows.map((row) => (
            <li
              key={row.key}
              className="flex items-center justify-between gap-4 border-b border-divider px-6 py-4 last:border-b-0"
            >
              <div className="min-w-0">
                <p className="flex flex-wrap items-center gap-2 text-small font-bold text-text">
                  {row.title}
                </p>
                <p className="mt-1 text-caption text-text-sub">{row.sub}</p>
              </div>
              {(row.onEdit || row.onDelete) && (
                <div className="flex shrink-0 gap-2 text-caption text-text-sub">
                  {row.onEdit && (
                    <button
                      type="button"
                      className="cursor-pointer hover:text-text"
                      onClick={row.onEdit}
                    >
                      수정
                    </button>
                  )}
                  {row.onDelete && (
                    <button
                      type="button"
                      className="cursor-pointer hover:text-danger"
                      onClick={row.onDelete}
                    >
                      삭제
                    </button>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
