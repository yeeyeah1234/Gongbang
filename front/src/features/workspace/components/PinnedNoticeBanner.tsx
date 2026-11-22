import { Link } from 'react-router'
import { PinIcon } from '../../../components/icons/Icons'
import { Button } from '../../../components/ui/Button'
import type { PinnedNotice } from '../types'

interface PinnedNoticeBannerProps {
  // 고정 공지가 없거나 조회에 실패하면 null → 배너 자체를 숨긴다
  notice: PinnedNotice | null
  // 접혀 있으면 배너 줄을 아예 그리지 않는다(높이 0). 다시 펼치는 버튼은 탭 바 오른쪽 끝에 있다 (WorkspaceTabs)
  collapsed: boolean
  onCollapse: () => void
  // 있으면 제목이 그 공지 상세로 가는 링크
  to?: string
}

// NTC-04 고정 공지 배너 (높이 84, primary-soft 배경, 「접기」 Outline S).
// 접기 상태는 usePinnedCollapsed(pinnedCollapse.ts)로 레이아웃이 들고 있는다.
export function PinnedNoticeBanner({ notice, collapsed, onCollapse, to }: PinnedNoticeBannerProps) {
  if (!notice || collapsed) return null

  return (
    <div className="shrink-0 border-b border-divider bg-primary-soft">
      <div className="mx-auto flex min-h-[84px] max-w-[1680px] items-center justify-between gap-4 px-6 py-4 lg:px-12 xl:px-[120px]">
        <div className="flex min-w-0 items-center gap-3">
          <PinIcon className="h-5 w-5 shrink-0 text-primary" />
          <span className="shrink-0 text-body font-bold text-primary">고정 공지</span>
          {to ? (
            <Link
              to={to}
              className="truncate text-body text-text hover:underline"
              title={notice.title}
            >
              {notice.title}
            </Link>
          ) : (
            <p className="truncate text-body text-text" title={notice.title}>
              {notice.title}
            </p>
          )}
        </div>
        <Button variant="outline" size="sm" onClick={onCollapse} aria-expanded>
          접기
        </Button>
      </div>
    </div>
  )
}
