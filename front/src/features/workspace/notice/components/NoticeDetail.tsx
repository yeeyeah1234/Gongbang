import { Badge } from '../../../../components/ui/Badge'
import { formatNoticeDateTime } from '../format'
import { linkify } from '../linkify'
import type { NoticeDetailData } from '../types'

interface NoticeDetailProps {
  notice: NoticeDetailData
}

// NTC-01 ⑥ 상세. 본문은 HTML로 넣지 않고 글자와 링크 조각으로 그린다
export function NoticeDetail({ notice }: NoticeDetailProps) {
  return (
    <article className="p-6 lg:p-10">
      <header className="flex flex-col items-start gap-2 border-b border-divider pb-6">
        {notice.isPinned && <Badge variant="primary-soft">상단 고정</Badge>}
        <h2 className="max-w-full text-title font-bold wrap-anywhere text-text">{notice.title}</h2>
        <p className="text-small text-text-sub">
          {notice.author.nickname} · {formatNoticeDateTime(notice.createdAt)}
          {/* TODO(확인 필요): 상세의 「(수정됨)」은 명세에 없어 목록과 같게 보인다 */}
          {notice.isEdited && ' (수정됨)'}
        </p>
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
