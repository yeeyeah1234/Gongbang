import { Link } from 'react-router'
import { PinIcon } from '../../../../components/icons/Icons'
import { formatNoticeDate } from '../format'
import type { NoticeListItemData } from '../types'

interface NoticeListItemProps {
  notice: NoticeListItemData
  to: string
  selected: boolean
}

// NTC-01 목록 한 칸
export function NoticeListItem({ notice, to, selected }: NoticeListItemProps) {
  return (
    <li>
      <Link
        to={to}
        aria-current={selected ? 'page' : undefined}
        className={[
          'flex flex-col gap-1 rounded-popover px-5 py-4 transition-colors',
          selected ? 'bg-primary-soft' : 'hover:bg-bg',
        ].join(' ')}
      >
        {notice.isPinned && (
          <span className="flex items-center gap-1 text-caption font-bold text-primary">
            <PinIcon className="h-4 w-4" />
            고정
          </span>
        )}
        <span className="flex min-w-0 items-center gap-2">
          {notice.isUnread && (
            <span className="h-2 w-2 shrink-0 rounded-full bg-primary">
              <span className="sr-only">안 읽음</span>
            </span>
          )}
          <span className="truncate text-body font-bold text-text">{notice.title}</span>
          {notice.isEdited && <span className="shrink-0 text-small text-text-sub">(수정됨)</span>}
        </span>
        <span className="truncate text-small text-text">{notice.preview}</span>
        <span className="truncate text-small text-text-sub">
          {notice.author.nickname} · {formatNoticeDate(notice.createdAt)}
        </span>
      </Link>
    </li>
  )
}
