import type { SVGProps } from 'react'

// WB-01 빈 상태·임시 캔버스 보드 아이콘 (WorkspaceIcons.tsx의 DocumentIcon과 같은 24px 선 아이콘 형식)
export function BoardIcon({ className = 'h-12 w-12', ...props }: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
      {...props}
    >
      <rect width="20" height="14" x="2" y="3" rx="2" />
      <path d="M8 21h8" />
      <path d="M12 17v4" />
      <path d="m7 12 3-3 2 2 4-4" />
    </svg>
  )
}
