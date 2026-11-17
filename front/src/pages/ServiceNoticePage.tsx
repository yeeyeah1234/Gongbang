import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { fetchServiceNotice } from '../features/notification/api'
import { formatDate } from '../features/member/constants'

// NOTI-07 ③ 운영 공지 상세 (모집글 상세 PST-005에서 오른쪽 컨테이너를 뺀 모양)
export default function ServiceNoticePage() {
  const id = Number(useParams().serviceNoticeId)
  const { data, isLoading, isError } = useQuery({
    queryKey: ['service-notices', id],
    queryFn: () => fetchServiceNotice(id),
    enabled: Number.isFinite(id),
  })

  return (
    <main className="mx-auto w-full max-w-[960px] px-4 py-10">
      {isLoading ? (
        <div className="h-64 animate-pulse rounded-card bg-surface-sub" />
      ) : isError || !data ? (
        <p className="py-20 text-center text-body text-text-sub">삭제되었거나 없는 공지예요</p>
      ) : (
        <article className="rounded-card border border-line bg-surface p-8">
          <span className="inline-flex h-[26px] items-center rounded-full bg-primary-soft px-2.5 text-caption font-bold text-primary">
            운영 공지
          </span>
          <h1 className="mt-3 text-title font-bold text-text">{data.title}</h1>
          <p className="mt-2 text-small text-text-sub">
            공방 운영팀 · {formatDate(data.createdAt)}
          </p>
          <div className="mt-6 border-t border-divider pt-6 text-body-lg whitespace-pre-wrap text-text">
            {data.content}
          </div>
        </article>
      )}
    </main>
  )
}
