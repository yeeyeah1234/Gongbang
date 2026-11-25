import { onlineManager, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useLocation } from 'react-router'
import { getApiError } from '../../../api/errors'
import { useWorkspace } from '../context'
import {
  noticeKeys,
  uniqueNotices,
  useDeleteNotice,
  useSetNoticePinned,
  type useNoticeList,
} from './hooks'
import type { NoticeDetailData } from './types'
import type { NoticeDialog } from './useNoticeDialog'

interface NoticeActionsOptions {
  dialog: NoticeDialog
  list: ReturnType<typeof useNoticeList>
  basePath: string
  selectedId: number | null
  isWide: boolean
}

// NTC-03 삭제, NTC-04 고정·해제
export function useNoticeActions({
  dialog,
  list,
  basePath,
  selectedId,
  isWide,
}: NoticeActionsOptions) {
  const { teamId } = useWorkspace()
  const queryClient = useQueryClient()
  const { key } = useLocation()
  const remove = useDeleteNotice()
  const setPinned = useSetNoticePinned()
  const [deleting, setDeleting] = useState<{ noticeId: number; key: string } | null>(null)

  // 지우는 동안과 이동이 끝나기 전까지 그 상세 쿼리를 끈다
  const detailPaused = deleting?.noticeId === selectedId && deleting?.key === key

  useEffect(() => {
    if (!deleting || deleting.noticeId === selectedId) return
    queryClient.removeQueries({ queryKey: noticeKeys.detail(teamId, deleting.noticeId) })
  }, [deleting, selectedId, teamId, queryClient])

  const writePinned = (noticeId: number, pinned: boolean) =>
    setPinned.mutate(
      { noticeId, pinned },
      {
        onSuccess: () => {
          dialog.end()
          dialog.setConfirm(null)
        },
        onError: (error) => dialog.fail(error, noticeId),
      },
    )

  const togglePin = ({ noticeId, isPinned }: NoticeDetailData) => {
    if (!dialog.begin()) return
    if (isPinned) writePinned(noticeId, false)
    else void dialog.checkPinned(noticeId, () => writePinned(noticeId, true))
  }

  const confirmReplace = (noticeId: number) => {
    if (dialog.begin()) writePinned(noticeId, true)
  }

  const requestDelete = ({ noticeId, title }: NoticeDetailData) =>
    dialog.setConfirm({ kind: 'delete', noticeId, title })

  // 다음 → 이전 → 없음(/notice). 다음 쪽을 못 받으면 이전 공지로 간다
  const findNeighbor = async (noticeId: number) => {
    let items = uniqueNotices(list.data)
    let index = items.findIndex((item) => item.noticeId === noticeId)
    if (index < 0) return null
    // 오프라인이면 다음 쪽 요청이 끝나지 않으므로 건너뛴다
    if (index === items.length - 1 && list.hasNextPage && onlineManager.isOnline()) {
      items = uniqueNotices((await list.fetchNextPage()).data)
      index = items.findIndex((item) => item.noticeId === noticeId)
      if (index < 0) return null
    }
    return (items[index + 1] ?? items[index - 1])?.noticeId ?? null
  }

  const confirmDelete = async (noticeId: number) => {
    if (!dialog.begin()) return
    const neighbor = isWide ? await findNeighbor(noticeId) : null
    if (!dialog.isStillHere(noticeId)) return dialog.end()

    setDeleting({ noticeId, key })
    await queryClient.cancelQueries({ queryKey: noticeKeys.detail(teamId, noticeId) })
    remove.mutate(noticeId, {
      onSuccess: () => dialog.leave(neighbor ? `${basePath}/${neighbor}` : basePath, true),
      onError: (error) => {
        // 404면 어차피 떠나므로 다시 켜지 않는다
        if (getApiError(error).code !== 'RESOURCE_NOT_FOUND') setDeleting(null)
        dialog.fail(error, noticeId)
      },
    })
  }

  return { detailPaused, togglePin, confirmReplace, requestDelete, confirmDelete }
}
