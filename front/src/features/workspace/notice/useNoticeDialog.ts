import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useBlocker, useLocation, useNavigate, type Blocker } from 'react-router'
import { getApiError, getErrorMessage } from '../../../api/errors'
import { useWorkspace } from '../context'
import { workspaceKeys } from '../hooks'
import { useFetchLatestPinned, useForgetNotice } from './hooks'
import type { NoticeCreateRequest } from './types'

export const GONE_MESSAGE = '이 공지는 삭제되었어요'

export interface NoticeForm {
  // null이면 작성
  noticeId: number | null
  initial: NoticeCreateRequest
  values: NoticeCreateRequest
  touched: { title: boolean; content: boolean }
}

type NoticeConfirm =
  | { kind: 'discard' }
  // noticeId null: 작성하면서 고정
  | { kind: 'pinReplace'; noticeId: number | null; title: string }
  | { kind: 'delete'; noticeId: number; title: string }

interface NoticeDialogOptions {
  basePath: string
  selectedId: number | null
  showToast: (message: string) => void
}

// NTC-02~04 모달 상태(한 번에 하나만 그린다), 연타 잠금, 나가기 확인, 쓰기 오류 처리
export function useNoticeDialog({ basePath, selectedId, showToast }: NoticeDialogOptions) {
  const { teamId, refreshNoticeIndicators } = useWorkspace()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { key } = useLocation()
  const fetchLatestPinned = useFetchLatestPinned(teamId)
  const forgetNotice = useForgetNotice(teamId)

  const [form, setForm] = useState<NoticeForm | null>(null)
  const [confirm, setConfirm] = useState<NoticeConfirm | null>(null)
  // leaving: 요청은 끝났고 주소가 바뀌기를 기다림. 그사이 다시 누르지 못하게 잠가 둔다
  const [status, setStatus] = useState<'idle' | 'pending' | 'leaving'>('idle')
  const [lastKey, setLastKey] = useState(key)

  // 주소가 바뀌는 렌더에서 닫는다. 먼저 닫으면 옛 공지의 404 처리·자동 이동이 끼어든다
  if (key !== lastKey) {
    setLastKey(key)
    setForm(null)
    setConfirm(null)
    if (status === 'leaving') setStatus('idle')
  }

  const busy = status !== 'idle'
  const open = form !== null || confirm !== null
  const dirty =
    form !== null &&
    (form.values.title !== form.initial.title ||
      form.values.content !== form.initial.content ||
      form.values.pinned !== form.initial.pinned)

  const lock = useRef(false)
  const mounted = useRef(true)
  const selected = useRef(selectedId)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])
  useEffect(() => {
    selected.current = selectedId
  }, [selectedId])
  useEffect(() => {
    if (status === 'idle') lock.current = false
  }, [status])

  const blocker = useBlocker(({ historyAction, nextLocation }) => {
    // 성공·404 이동(leave)은 막지 않는다. 앞·뒤로 가기에는 표식이 남아 있어도 인정하지 않는다
    if (historyAction !== 'POP' && nextLocation.state?.noticeLeave) return false
    return open && (busy || dirty)
  })
  const askLeave = blocker.state === 'blocked' && dirty && !busy
  // 라우터 상태는 늦게 그려진다. 그사이 같은 blocked에 두 번 답하면(연타) 라우터가 예외를 던진다
  const answered = useRef<Blocker>(null)
  const answerBlocker = useCallback(
    (answer: 'reset' | 'proceed') => {
      if (blocker.state !== 'blocked' || answered.current === blocker) return
      answered.current = blocker
      blocker[answer]()
    },
    [blocker],
  )
  useEffect(() => {
    // 요청 중에는 묻지 않고 막기만 한다
    if (blocker.state === 'blocked' && !askLeave) answerBlocker('reset')
  }, [blocker, askLeave, answerBlocker])

  const begin = () => {
    if (lock.current) return false
    lock.current = true
    setStatus('pending')
    return true
  }

  const end = () => {
    lock.current = false
    setStatus('idle')
  }

  const leave = (to: string, replace: boolean) => {
    setStatus('leaving')
    void navigate(to, { replace, state: { noticeLeave: true } })
  }

  // 기다리는 동안 화면이나 그 공지를 떠났으면 쓰기를 시작하지 않는다
  const isStillHere = (noticeId: number | null) =>
    mounted.current && (noticeId === null || selected.current === noticeId)

  const fail = (error: unknown, noticeId: number | null = null) => {
    const { code } = getApiError(error)
    if (code === 'RESOURCE_NOT_FOUND' && noticeId !== null) {
      showToast(GONE_MESSAGE)
      if (selected.current === noticeId) leave(basePath, true)
      else end()
      forgetNotice(noticeId)
      return
    }
    end()
    showToast(getErrorMessage(error))
    if (code === 'NOT_TEAM_LEADER' || code === 'NOT_TEAM_MEMBER') {
      setConfirm(null)
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
    } else if (code === 'NOTICE_PIN_CONFLICT') {
      setConfirm(null)
      void refreshNoticeIndicators()
    }
  }

  // NTC-04: 다른 고정 공지가 있으면 묻고, 없으면 write. begin() 뒤에 부른다
  const checkPinned = async (noticeId: number | null, write: () => void) => {
    try {
      const pinned = await fetchLatestPinned()
      if (!isStillHere(noticeId)) return end()
      if (pinned && pinned.noticeId !== noticeId) {
        setConfirm({ kind: 'pinReplace', noticeId, title: pinned.title })
        return end()
      }
    } catch (error) {
      if (mounted.current) fail(error)
      return
    }
    write()
  }

  const close = () => {
    setForm(null)
    setConfirm(null)
  }

  const requestClose = () => {
    if (busy) return
    if (dirty) setConfirm({ kind: 'discard' })
    else close()
  }

  const stay = () => {
    answerBlocker('reset')
    if (confirm?.kind === 'discard') setConfirm(null)
  }

  const discard = () => {
    // 이동이면 주소가 바뀔 때 닫힌다
    if (blocker.state === 'blocked') answerBlocker('proceed')
    else close()
  }

  const showing =
    blocker.state === 'proceeding'
      ? null
      : askLeave
        ? 'discard'
        : (confirm?.kind ?? (form ? 'form' : null))

  return {
    form,
    setForm,
    confirm,
    setConfirm,
    showing,
    open,
    busy,
    begin,
    end,
    leave,
    isStillHere,
    fail,
    checkPinned,
    close,
    requestClose,
    stay,
    discard,
  }
}

export type NoticeDialog = ReturnType<typeof useNoticeDialog>
