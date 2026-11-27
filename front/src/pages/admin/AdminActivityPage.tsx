import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { readAdmin } from '../../features/admin/operations'
import { Button } from '../../components/ui/Button'
import { getErrorMessage } from '../../api/errors'

const actions: Record<string, string> = {
  NOTICE_CREATE: '공지 등록',
  NOTICE_UPDATE: '공지 수정',
  NOTICE_DELETE: '공지 삭제',
  CONTEST_UPDATE: '공모전 수정',
  CATEGORY_UPDATE: '카테고리 수정',
  VERIFY_VERIFIED: '인증 승인',
  VERIFY_REJECTED: '인증 반려',
  POST_HIDE: '모집글 숨김',
  BOARD_POST_HIDE: '게시글 숨김',
  COMMENT_HIDE: '댓글 숨김',
  POST_RESTORE: '모집글 복원',
  BOARD_POST_RESTORE: '게시글 복원',
  COMMENT_RESTORE: '댓글 복원',
  ADMIN_INVITE: '관리자 초대 발송',
  ADMIN_INVITE_ACCEPT: '관리자 초대 수락',
  ADMIN_INVITE_DECLINE: '관리자 초대 거절',
  ADMIN_GRANTED: '관리자 권한 부여',
  WARN: '회원 경고',
  SUSPEND: '기간 정지',
  SUSPEND_PERMANENT: '무기한 정지',
  UNSUSPEND: '정지 해제',
  UNLOCK: '로그인 잠금 해제',
  REQUIRE_NICKNAME: '닉네임 변경 요구',
  REPORT_HIDE_POST: '신고 모집글 숨김',
  REPORT_HIDE_BOARD_POST: '신고 게시글 숨김',
  REPORT_HIDE_COMMENT: '신고 댓글 숨김',
  REPORT_DISMISS: '신고 기각',
  REPORT_CANCEL: '신고 처리 취소',
  REPORT_WARN: '신고 회원 경고',
  REPORT_SUSPEND: '신고 회원 기간 정지',
  REPORT_SUSPEND_PERMANENT: '신고 회원 무기한 정지',
  REPORT_REQUIRE_NICKNAME: '신고 닉네임 변경 요구',
}
interface Activity {
  id: number
  actorId: number
  actorNickname: string
  action: string
  targetType: string
  targetId: number
  reason: string | null
  createdAt: string
}
interface ActivityPage {
  content: Activity[]
  totalElements: number
  totalPages: number
}
export default function AdminActivityPage() {
  const [action, setAction] = useState('')
  const [page, setPage] = useState(0)
  const logs = useQuery({
    queryKey: ['admin', 'activity-logs', action, page],
    queryFn: () => readAdmin<ActivityPage>('activity-logs', { action, page }),
  })
  return (
    <section className="min-w-0 space-y-5">
      <h1 className="text-heading font-bold">관리자 활동 로그</h1>
      <p className="text-small text-text-sub">
        기록 기능 적용 이후 성공한 권한·초대·회원 제재·신고·인증 심사·공지·공모전 관리 이력입니다.
        작업자 닉네임은 현재 정보를 표시합니다.
      </p>
      <div className="flex flex-wrap items-end gap-3">
        <label className="min-w-0">
          작업 종류
          <select
            value={action}
            onChange={(e) => {
              setAction(e.target.value)
              setPage(0)
            }}
            className="mt-1 block w-full rounded-input border border-line-input p-2"
          >
            <option value="">전체</option>
            {Object.entries(actions).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <Button onClick={() => logs.refetch()} disabled={logs.isFetching}>
          새로고침
        </Button>
      </div>
      {logs.isPending ? (
        <p role="status">로그를 불러오는 중입니다…</p>
      ) : logs.isError ? (
        <div role="alert">
          <p>{getErrorMessage(logs.error, '로그를 불러오지 못했습니다.')}</p>
          <Button onClick={() => logs.refetch()}>다시 시도</Button>
        </div>
      ) : (
        <>
          <p>총 {logs.data.totalElements}건 · 최신순</p>
          {logs.data.content.length === 0 ? (
            <p className="rounded-card border border-line p-6">조회된 활동 로그가 없습니다.</p>
          ) : (
            <ul className="space-y-3">
              {logs.data.content.map((log) => (
                <li
                  key={log.id}
                  className="min-w-0 rounded-card border border-line p-4 break-words"
                >
                  <div className="flex flex-wrap justify-between gap-2">
                    <strong>{actions[log.action] ?? log.action}</strong>
                    <time className="text-small text-text-sub">
                      {log.createdAt.replace('T', ' ')} (한국 시간)
                    </time>
                  </div>
                  <p className="mt-2">
                    작업자: {log.actorNickname} (회원 #{log.actorId})
                  </p>
                  <p>
                    대상:{' '}
                    {(
                      {
                        MEMBER: '회원',
                        REPORT: '신고',
                        ADMIN_INVITATION: '관리자 초대',
                        SERVICE_NOTICE: '공지',
                        CONTEST: '공모전',
                        CATEGORY: '카테고리',
                        VERIFICATION: '인증 요청',
                        POST: '모집글',
                        BOARD_POST: '게시글',
                        COMMENT: '댓글',
                      } as Record<string, string>
                    )[log.targetType] ?? log.targetType}{' '}
                    #{log.targetId}
                  </p>
                  {log.reason && <p className="mt-2 whitespace-pre-wrap">사유: {log.reason}</p>}
                </li>
              ))}
            </ul>
          )}
          <div className="flex items-center justify-center gap-4">
            <Button disabled={page === 0} onClick={() => setPage(page - 1)}>
              이전
            </Button>
            <span>
              {page + 1} / {Math.max(1, logs.data.totalPages)}
            </span>
            <Button disabled={page + 1 >= logs.data.totalPages} onClick={() => setPage(page + 1)}>
              다음
            </Button>
          </div>
        </>
      )}
    </section>
  )
}
