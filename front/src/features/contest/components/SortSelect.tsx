import { useState } from 'react'
import { ChevronDownIcon } from '../../../components/icons/Icons'
import { Modal } from '../../../components/ui/Modal'
import type { ContestSort } from '../types'

// MAIN-04 ② 정렬 옵션 3종
const SORT_OPTIONS: { value: ContestSort; label: string }[] = [
  { value: 'latest', label: '최신순' },
  { value: 'deadline', label: '마감 임박순' },
  { value: 'views', label: '조회수순' },
]

interface SortSelectProps {
  value: ContestSort
  onChange: (sort: ContestSort) => void
}

// MAIN-04 ①: 정렬 기준 버튼을 누르면 정렬 기준 선택 팝업(모달)
export function SortSelect({ value, onChange }: SortSelectProps) {
  const [open, setOpen] = useState(false)
  const currentLabel = SORT_OPTIONS.find((option) => option.value === value)?.label

  const handleSelect = (sort: ContestSort) => {
    onChange(sort)
    setOpen(false)
  }

  return (
    <>
      <button
        type="button"
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
        className="flex h-11 w-39 shrink-0 items-center justify-between rounded-full border border-line-strong bg-surface px-5 text-body hover:bg-bg"
      >
        {currentLabel}
        <ChevronDownIcon className="size-5" />
      </button>

      <Modal open={open} onClose={() => setOpen(false)} title="정렬 기준 선택">
        <ul className="flex flex-col gap-1">
          {SORT_OPTIONS.map((option) => {
            const isSelected = option.value === value
            return (
              <li key={option.value}>
                <button
                  type="button"
                  aria-pressed={isSelected}
                  onClick={() => handleSelect(option.value)}
                  className={`flex h-11 w-full items-center rounded-popover px-4 text-body ${
                    isSelected ? 'bg-primary-soft font-bold text-primary' : 'hover:bg-bg'
                  }`}
                >
                  {option.label}
                </button>
              </li>
            )
          })}
        </ul>
      </Modal>
    </>
  )
}
