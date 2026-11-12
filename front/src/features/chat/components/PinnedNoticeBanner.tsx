import { PinIcon } from '../../../components/icons/Icons'
import { Button } from '../../../components/ui/Button'

interface PinnedNoticeBannerProps {
  message: string
  collapsed: boolean
  onToggleCollapse: () => void
}

/** STYLE_GUIDE.md 4장·5-8 고정 공지 배너 (높이 84px, primary-soft 배경, Outline S 접기 버튼) */
export function PinnedNoticeBanner({
  message,
  collapsed,
  onToggleCollapse,
}: PinnedNoticeBannerProps) {
  if (collapsed) {
    return (
      <div className="shrink-0 border-b border-divider bg-primary-soft">
        <div className="mx-auto flex max-w-[1680px] items-center justify-between gap-4 px-6 py-2 lg:px-12 xl:px-[120px]">
          <div className="flex items-center gap-2">
            <PinIcon className="h-4 w-4 shrink-0 text-primary" />
            <span className="text-small font-bold text-primary">고정 공지</span>
          </div>
          <Button variant="text" size="sm" onClick={onToggleCollapse}>
            펼치기
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-[84px] shrink-0 border-b border-divider bg-primary-soft">
      <div className="mx-auto flex min-h-[84px] max-w-[1680px] items-center justify-between gap-4 px-6 py-4 lg:px-12 xl:px-[120px]">
        <div className="flex min-w-0 items-center gap-3">
          <PinIcon className="h-5 w-5 shrink-0 text-primary" />
          <span className="shrink-0 text-body font-bold text-primary">고정 공지</span>
          <p className="truncate text-body text-text">{message}</p>
        </div>
        <Button variant="outline" size="sm" onClick={onToggleCollapse}>
          접기
        </Button>
      </div>
    </div>
  )
}

