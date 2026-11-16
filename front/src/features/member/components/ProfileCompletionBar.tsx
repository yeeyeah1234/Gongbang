// MY-01 ⑥ / Figma 08-1 상단 「프로필 완성도 70%」 게이지
export function ProfileCompletionBar({ value, hint }: { value: number; hint?: string }) {
  return (
    <div className="rounded-card bg-surface-sub px-5 py-4">
      <div className="flex items-center justify-between">
        <span className="text-small font-bold text-text">프로필 완성도 {value}%</span>
        {hint && <span className="text-caption text-text-sub">{hint}</span>}
      </div>
      <progress
        aria-label="프로필 완성도"
        value={value}
        max={100}
        className="mt-3 block h-2 w-full appearance-none overflow-hidden rounded-full [&::-moz-progress-bar]:rounded-full [&::-moz-progress-bar]:bg-primary [&::-webkit-progress-bar]:bg-surface-sub [&::-webkit-progress-value]:rounded-full [&::-webkit-progress-value]:bg-primary"
      />
    </div>
  )
}
