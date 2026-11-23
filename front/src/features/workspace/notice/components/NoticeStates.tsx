import { useEffect, useState } from 'react'
import { PinIcon, PlusIcon } from '../../../../components/icons/Icons'
import { Button } from '../../../../components/ui/Button'

// STYLE_GUIDE 5-10: 1초가 넘으면 스켈레톤
const SKELETON_DELAY_MS = 1000

export function NoticeSkeleton() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => setVisible(true), SKELETON_DELAY_MS)
    return () => clearTimeout(timer)
  }, [])

  return (
    <div aria-busy="true" className="flex flex-col gap-3 p-5">
      {visible && (
        <>
          <div className="h-6 w-2/3 animate-pulse rounded-lg bg-surface-sub" />
          <div className="h-5 w-1/3 animate-pulse rounded-lg bg-surface-sub" />
          <div className="h-24 animate-pulse rounded-lg bg-surface-sub" />
        </>
      )}
    </div>
  )
}

export function NoticeError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 px-5 py-12 text-center">
      <p className="text-body text-text-sub">불러오지 못했어요</p>
      <Button onClick={onRetry}>다시 시도</Button>
    </div>
  )
}

// NTC-02 「+ 공지 작성」 (목록 위, 빈 화면)
export function NoticeCreateButton({
  disabled,
  onClick,
}: {
  disabled?: boolean
  onClick: () => void
}) {
  return (
    <Button variant="primary" disabled={disabled} onClick={onClick}>
      <PlusIcon />
      공지 작성
    </Button>
  )
}

// onCreate는 팀장일 때만 넘긴다
export function NoticeEmpty({ onCreate }: { onCreate?: () => void }) {
  return (
    <section className="flex flex-1 flex-col items-center justify-center gap-4 rounded-card border border-line bg-surface p-12 text-center">
      <PinIcon className="h-12 w-12 text-text-disabled" />
      <p className="text-body text-text-sub">아직 공지가 없어요</p>
      {onCreate && <NoticeCreateButton onClick={onCreate} />}
    </section>
  )
}
