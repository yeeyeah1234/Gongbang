import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement>

// 권한 없음 화면 자물쇠 (components/icons/Icons.tsx와 같은 24px 선 아이콘 형식)
export function LockIcon({ className = 'h-6 w-6', ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
      {...props}
    >
      <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  )
}
