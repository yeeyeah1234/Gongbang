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
    // 768px 미만은 한 줄로 두고 옆으로 넘겨 본다 (줄바꿈하면 목록이 화면 아래로 밀림, #117)
    <div className="-mx-4 flex [scrollbar-width:none] gap-3 overflow-x-auto px-4 md:mx-0 md:flex-wrap md:overflow-visible md:px-0">
      {chips.map((chip) => {
        const isSelected = chip.categoryCode === selected
        return (
          <button
            key={chip.categoryCode ?? 'ALL'}
            type="button"
            aria-pressed={isSelected}
            onClick={() => onSelect(chip.categoryCode)}
            className={`h-12.5 shrink-0 rounded-full px-5 text-body ${
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
