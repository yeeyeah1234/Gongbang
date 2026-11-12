import { useCategories } from '../hooks'

interface CategoryFilterProps {
  selected?: string // 없으면 「전체」
  onSelect: (categoryCode?: string) => void
}

// MAIN-05: [전체](기본) + 분야 목록, 하나만 고른다
export function CategoryFilter({ selected, onSelect }: CategoryFilterProps) {
  const { data: categories = [] } = useCategories()
  const chips = [
    { categoryCode: undefined, name: '전체' },
    ...[...categories].sort((a, b) => a.sortOrder - b.sortOrder),
  ]

  return (
    <div className="flex flex-wrap gap-3">
      {chips.map((chip) => {
        const isSelected = chip.categoryCode === selected
        return (
          <button
            key={chip.categoryCode ?? 'ALL'}
            type="button"
            aria-pressed={isSelected}
            onClick={() => onSelect(chip.categoryCode)}
            className={`h-12.5 rounded-full px-5 text-body ${
              isSelected
                ? 'bg-primary font-bold text-white'
                : 'border border-line-strong bg-surface text-text hover:bg-bg'
            }`}
          >
            {chip.name}
          </button>
        )
      })}
    </div>
  )
}
