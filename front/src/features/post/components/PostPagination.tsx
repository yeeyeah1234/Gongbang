interface PostPaginationProps {
  page: number // 0부터
  totalPages: number
  onChange: (page: number) => void
}

// 한 번에 보여 줄 페이지 번호 수 (Figma WF-01: ‹ 1 2 3 4 5 ›)
const WINDOW = 5

// PST-001 ⑦ 페이지 번호. 지금 페이지가 가운데 오도록 5개씩 보여 준다.
export function PostPagination({ page, totalPages, onChange }: PostPaginationProps) {
  if (totalPages <= 1) return null

  const start = Math.max(0, Math.min(page - Math.floor(WINDOW / 2), totalPages - WINDOW))
  const pages = Array.from({ length: Math.min(WINDOW, totalPages) }, (_, i) => start + i)
  const arrowClass =
    'flex size-10 cursor-pointer items-center justify-center rounded-full text-subheading text-text-sub hover:bg-surface-sub disabled:cursor-not-allowed disabled:text-text-disabled disabled:hover:bg-transparent'

  return (
    <nav className="mt-10 flex items-center justify-center gap-2" aria-label="페이지">
      <button
        type="button"
        aria-label="이전 페이지"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        className={arrowClass}
      >
        ‹
      </button>
      {pages.map((index) => (
        <button
          key={index}
          type="button"
          aria-label={`${index + 1}페이지`}
          aria-current={index === page ? 'page' : undefined}
          onClick={() => onChange(index)}
          className={[
            'size-10 cursor-pointer rounded-full text-body',
            index === page ? 'bg-primary font-bold text-surface' : 'text-text hover:bg-surface-sub',
          ].join(' ')}
        >
          {index + 1}
        </button>
      ))}
      <button
        type="button"
        aria-label="다음 페이지"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
        className={arrowClass}
      >
        ›
      </button>
    </nav>
  )
}
