import { Badge } from '../../../../components/ui/Badge'
import { Button } from '../../../../components/ui/Button'
import { formatNoticeDateTime } from '../format'
import { linkify } from '../linkify'
import type { NoticeDetailData } from '../types'

interface NoticeDetailProps {
  notice: NoticeDetailData
  // 팀장일 때만 넘긴다
  actions?: {
    busy: boolean
    pinning: boolean
    onTogglePin: () => void
    onEdit: () => void
    onDelete: () => void
  }
}

// NTC-01 ⑥ 상세. 본문은 HTML로 넣지 않고 글자와 링크 조각으로 그린다
export function NoticeDetail({ notice, actions }: NoticeDetailProps) {
  return (
    <article className="p-6 lg:p-10">
      <header className="flex flex-col-reverse gap-4 border-b border-divider pb-6 xl:flex-row xl:justify-between">
        <div className="flex min-w-0 flex-col items-start gap-2">
          {notice.isPinned && <Badge variant="primary-soft">상단 고정</Badge>}
          <h2 className="max-w-full text-title font-bold wrap-anywhere text-text">
            {notice.title}
          </h2>
          <p className="text-small text-text-sub">
            {notice.author.nickname} · {formatNoticeDateTime(notice.createdAt)}
            {/* TODO(확인 필요): 상세의 「(수정됨)」은 명세에 없어 목록과 같게 보인다 */}
            {notice.isEdited && ' (수정됨)'}
          </p>
        </div>
        {actions && (
          <div className="flex shrink-0 flex-wrap items-start justify-end gap-2">
            <Button
              size="sm"
              loading={actions.pinning}
              disabled={actions.busy}
              onClick={actions.onTogglePin}
            >
              {notice.isPinned ? '고정 해제' : '고정'}
            </Button>
            <Button size="sm" disabled={actions.busy} onClick={actions.onEdit}>
              수정
            </Button>
            <Button variant="danger" size="sm" disabled={actions.busy} onClick={actions.onDelete}>
              삭제
            </Button>
          </div>
        )}
      </header>
      <p className="pt-6 text-body-lg wrap-anywhere whitespace-pre-wrap text-text">
        {linkify(notice.content).map(({ text, href }, index) =>
          href ? (
            <a
              key={index}
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary underline"
            >
              {text}
            </a>
          ) : (
            text
          ),
        )}
      </p>
    </article>
  )
}
