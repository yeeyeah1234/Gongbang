import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { getErrorMessage } from '../../../api/errors'
import { Button } from '../../../components/ui/Button'
import { loadLoginMember } from '../../auth/session'
import { respondAdminInvitation, respondInvitation } from '../api'
import { CATEGORY_LABEL, timeAgo } from '../format'
import { useDeleteNotification, useMarkRead, notificationKeys } from '../hooks'
import type { NotificationItem } from '../types'

interface NotificationRowProps {
  item: NotificationItem
  compact?: boolean // 드롭다운
  onNavigate?: () => void
  onError?: (message: string) => void
}

// NOTI-02 ①③④ / NOTI-01: 아이콘, 내용, 몇 분 전, 안 읽음 점. 누르면 읽음 + 그 화면으로
export function NotificationRow({
  item,
  compact = false,
  onNavigate,
  onError,
}: NotificationRowProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const markRead = useMarkRead()
  const remove = useDeleteNotification()
  const [inviteResult, setInviteResult] = useState<string | null>(null)

  const responding = useRef(false)
  const [busy, setBusy] = useState(false)

  const open = async () => {
    if (markRead.isPending) return
    try {
      if (!item.isRead) await markRead.mutateAsync(item.notificationId)
      onNavigate?.()
      navigate(item.linkUrl)
    } catch (error) {
      onError?.(getErrorMessage(error, '알림을 읽음 처리하지 못했어요. 다시 시도해 주세요'))
    }
  }

  // ④ 팀 초대: [수락] [거절] [모집글 보기]
  const respond = async (accept: boolean) => {
    if (
      !item.invitation ||
      item.invitation.status !== 'PENDING' ||
      inviteResult ||
      responding.current
    )
      return
    responding.current = true
    setBusy(true)
    try {
      if (item.type === 'ADMIN_INVITE') {
        await respondAdminInvitation(item.invitation.invitationId, accept)
        if (accept) {
          void loadLoginMember().catch(() => undefined)
          void queryClient.invalidateQueries({ queryKey: ['account-status'] })
        }
      } else {
        await respondInvitation(item.invitation.invitationId, accept)
      }
      setInviteResult(accept ? '수락했어요' : '거절했어요')
      if (!item.isRead) markRead.mutate(item.notificationId)
      void queryClient.invalidateQueries({ queryKey: notificationKeys.all })
    } catch (e) {
      onError?.(getErrorMessage(e, '초대를 처리하지 못했어요. 상태를 확인해 주세요'))
      void queryClient.invalidateQueries({ queryKey: notificationKeys.all })
    } finally {
      responding.current = false
      setBusy(false)
    }
  }

  const invitationStatus =
    inviteResult ??
    (item.invitation?.status === 'ACCEPTED'
      ? '수락했어요'
      : item.invitation?.status === 'DECLINED'
        ? '거절했어요'
        : item.invitation?.status === 'CANCELED'
          ? '취소된 초대예요'
          : item.invitation?.status === 'PENDING'
            ? null
            : '초대 상태를 확인할 수 없어요')

  return (
    <li
      className={[
        'group relative flex items-start gap-3 border-b border-divider last:border-b-0',
        compact ? 'px-4 py-3' : 'px-5 py-4',
        item.isRead ? '' : 'bg-primary-soft/40',
      ].join(' ')}
    >
      {!item.isRead && (
        <span
          className="absolute top-1/2 left-1.5 size-1.5 -translate-y-1/2 rounded-full bg-danger"
          aria-label="안 읽음"
        />
      )}
      <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-surface-sub text-caption text-text-sub">
        {CATEGORY_LABEL[item.category]}
      </span>
      <div className="min-w-0 flex-1">
        <button
          type="button"
          onClick={open}
          disabled={markRead.isPending}
          className="block w-full cursor-pointer text-left"
        >
          <p
            className={[
              'text-small text-text',
              item.isRead ? '' : 'font-bold',
              compact ? 'line-clamp-2' : '',
            ].join(' ')}
          >
            {item.message}
          </p>
          <p className="mt-0.5 text-caption text-text-sub">{timeAgo(item.createdAt)}</p>
        </button>
        {(item.type === 'TEAM_INVITE' || item.type === 'ADMIN_INVITE') && (
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {invitationStatus ? (
              <span className="text-caption text-text-sub">{invitationStatus}</span>
            ) : (
              <>
                <Button variant="outline" size="sm" disabled={busy} onClick={() => respond(true)}>
                  수락
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={busy}
                  onClick={() => respond(false)}
                >
                  거절
                </Button>
              </>
            )}
            {item.type === 'TEAM_INVITE' && (
              <Button variant="text" size="sm" onClick={open}>
                모집글 보기
              </Button>
            )}
            {item.type === 'ADMIN_INVITE' &&
              (item.invitation?.status === 'ACCEPTED' || inviteResult === '수락했어요') && (
                <Button
                  variant="text"
                  size="sm"
                  onClick={() => {
                    onNavigate?.()
                    navigate('/admin/members')
                  }}
                >
                  관리자 페이지
                </Button>
              )}
          </div>
        )}
      </div>
      {!compact && (
        <button
          type="button"
          aria-label="알림 삭제"
          onClick={() => remove.mutate(item.notificationId)}
          className="flex size-8 shrink-0 cursor-pointer items-center justify-center rounded-full text-text-sub hover:bg-bg hover:text-text"
        >
          ×
        </button>
      )}
    </li>
  )
}
