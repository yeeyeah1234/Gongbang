import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useEffectEvent, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { getApiError } from '../api/errors'
import { Toast } from '../components/ui/Toast'
import { useWorkspace } from '../features/workspace/context'
import { workspaceKeys } from '../features/workspace/hooks'
import { NoticeConfirmModal } from '../features/workspace/notice/components/NoticeConfirmModal'
import { NoticeDetail } from '../features/workspace/notice/components/NoticeDetail'
import { NoticeFormModal } from '../features/workspace/notice/components/NoticeFormModal'
import { NoticeList } from '../features/workspace/notice/components/NoticeList'
import {
  NoticeEmpty,
  NoticeError,
  NoticeSkeleton,
} from '../features/workspace/notice/components/NoticeStates'
import {
  uniqueNotices,
  useForgetNotice,
  useNoticeDetail,
  useNoticeList,
  useSyncNoticeRead,
} from '../features/workspace/notice/hooks'
import { useNoticeActions } from '../features/workspace/notice/useNoticeActions'
import { GONE_MESSAGE, useNoticeDialog } from '../features/workspace/notice/useNoticeDialog'
import { useNoticeEditor } from '../features/workspace/notice/useNoticeEditor'
import { useTimedToast } from '../hooks/useTimedToast'

interface WorkspaceNoticePageProps {
  // 개발용 미리보기만 넘긴다. 기본은 /workspaces/:teamId/notice
  basePath?: string
}

// NTC-01~04 공지 탭: /workspaces/:teamId/notice/:noticeId?
// 쓰기 버튼은 팀장에게만 보인다 (권한은 서버가 다시 본다)
export default function WorkspaceNoticePage({ basePath }: WorkspaceNoticePageProps) {
  const { teamId } = useWorkspace()
  // 팀이 바뀌면 토스트·스크롤까지 처음부터
  return <NoticeTab key={teamId} basePath={basePath ?? `/workspaces/${teamId}/notice`} />
}

function parseNoticeId(param: string | undefined) {
  const id = Number(param)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

function NoticeTab({ basePath }: { basePath: string }) {
  const { teamId, summary } = useWorkspace()
  const { noticeId: param } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useTimedToast()
  const forgetNotice = useForgetNotice(teamId)

  const selectedId = parseNoticeId(param)
  const list = useNoticeList(teamId)
  const dialog = useNoticeDialog({ basePath, selectedId, showToast: toast.show })
  const editor = useNoticeEditor({ dialog, basePath, selectedId })
  const actions = useNoticeActions({ dialog, list, basePath, selectedId })
  const detail = useNoticeDetail(teamId, selectedId, !actions.detailPaused)
  const items = uniqueNotices(list.data)
  // 모달이 열린 동안에는 자동으로 옮기지 않는다 (작성 중인 내용이 사라지지 않게)
  const { open: modalOpen } = dialog

  // 상세 API에는 teamId가 없어 다른 팀 공지도 열리므로 화면에서 없는 공지와 같게 다룬다
  const notice = detail.data?.teamId === teamId ? detail.data : undefined
  const detailCode = detail.isError ? getApiError(detail.error).code : null
  const notMember = detailCode === 'NOT_TEAM_MEMBER'
  const gone =
    param !== undefined &&
    (selectedId === null ||
      detailCode === 'RESOURCE_NOT_FOUND' ||
      notMember ||
      (detail.data !== undefined && !notice))

  useSyncNoticeRead(teamId, gone ? undefined : notice?.noticeId, detail.dataUpdatedAt, list.data)

  const leaveGoneNotice = useEffectEvent(() => {
    toast.show(GONE_MESSAGE)
    navigate(basePath, { replace: true })
    forgetNotice(selectedId)
  })

  // 팀원인지 확인하지 못한 공지의 주소
  const [uncheckedParam, setUncheckedParam] = useState<string>()
  const unchecked = notMember && uncheckedParam === param

  useEffect(() => {
    if (!gone || unchecked || modalOpen) return
    let active = true
    const leave = async () => {
      if (notMember) {
        // 팀에서 빠진 것이면 레이아웃이 권한 없음 화면으로 바꾸고, 팀원이면 다른 팀 공지다
        const queryKey = workspaceKeys.summary(teamId)
        await queryClient.invalidateQueries({ queryKey })
        const state = queryClient.getQueryState(queryKey)
        if (state?.status === 'error') {
          // 그 밖의 실패는 레이아웃이 화면을 바꾸지 않으므로 여기서 다시 시도하게 한다
          const { code } = getApiError(state.error)
          if (active && code !== 'NOT_TEAM_MEMBER') setUncheckedParam(param)
          return
        }
      }
      if (active) leaveGoneNotice()
    }
    void leave()
    return () => {
      active = false
    }
  }, [gone, unchecked, modalOpen, notMember, param, teamId, queryClient])

  const listNotMember = list.isError && getApiError(list.error).code === 'NOT_TEAM_MEMBER'
  useEffect(() => {
    if (listNotMember)
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.summary(teamId) })
  }, [listNotMember, teamId, queryClient])

  // /notice → 첫 공지(서버가 고정 → 최신순). 낡은 캐시로 가지 않게 목록 조회가 끝난 뒤 옮긴다.
  const noId = param === undefined
  const empty = list.data !== undefined && items.length === 0
  const listFailed = list.isError && !list.data
  const { refetch } = list
  useEffect(() => {
    if (!noId || empty || listFailed || modalOpen) return
    let active = true
    // cancelRefetch false: 이미 나간 조회가 있으면 그 결과를 같이 기다린다
    void refetch({ cancelRefetch: false }).then(({ data }) => {
      const first = data?.pages[0]?.items[0]
      if (active && first) navigate(`${basePath}/${first.noticeId}`, { replace: true })
    })
    return () => {
      active = false
    }
  }, [noId, empty, listFailed, modalOpen, refetch, navigate, basePath])

  const onCreate = summary.isLeader ? editor.openCreate : undefined

  const renderDetail = () => {
    if (unchecked) return <NoticeError onRetry={() => setUncheckedParam(undefined)} />
    if (gone) return <NoticeSkeleton />
    // 다시 조회가 실패해도 이미 받은 내용은 그대로 둔다
    if (notice) {
      const detailActions = summary.isLeader
        ? {
            busy: dialog.busy,
            pinning: dialog.busy && !modalOpen,
            onTogglePin: () => actions.togglePin(notice),
            onEdit: () => editor.openEdit(notice),
            onDelete: () => actions.requestDelete(notice),
          }
        : undefined
      return <NoticeDetail notice={notice} actions={detailActions} />
    }
    if (detail.isError) return <NoticeError onRetry={() => detail.refetch()} />
    return <NoticeSkeleton />
  }

  // 확인 모달과 폼 중 하나만 그린다 (공통 Modal은 겹치면 Esc에 둘 다 닫히고 id도 겹친다)
  const renderModal = () => {
    const { showing, confirm, form, busy } = dialog
    if (showing === 'discard') {
      return (
        <NoticeConfirmModal
          title="작성 중인 공지"
          question="작성 중인 공지가 사라져요. 닫을까요?"
          confirmLabel="닫기"
          onConfirm={dialog.discard}
          onCancel={dialog.stay}
        />
      )
    }
    if (showing === 'pinReplace' && confirm?.kind === 'pinReplace') {
      const { noticeId, title } = confirm
      return (
        <NoticeConfirmModal
          title="고정 공지 변경"
          question={`‘${title}’ 공지의 고정이 해제돼요. 계속할까요?`}
          confirmLabel="계속"
          busy={busy}
          onConfirm={() =>
            noticeId === null ? editor.confirmReplace() : actions.confirmReplace(noticeId)
          }
          onCancel={() => dialog.setConfirm(null)}
        />
      )
    }
    if (showing === 'delete' && confirm?.kind === 'delete') {
      const { noticeId, title } = confirm
      return (
        <NoticeConfirmModal
          title="공지 삭제"
          question={`‘${title}’ 공지를 삭제할까요?`}
          note="삭제하면 되돌릴 수 없어요"
          confirmLabel="삭제"
          danger
          busy={busy}
          onConfirm={() => void actions.confirmDelete(noticeId)}
          onCancel={() => dialog.setConfirm(null)}
        />
      )
    }
    if (showing === 'form' && form) {
      return (
        <NoticeFormModal
          editing={form.noticeId !== null}
          values={form.values}
          touched={form.touched}
          busy={busy}
          onChange={editor.change}
          onSubmit={editor.submit}
          onClose={dialog.requestClose}
        />
      )
    }
    return null
  }

  return (
    <>
      {/* inert: 공통 Modal에 포커스 가두기가 없어 Tab으로 뒤쪽 버튼을 누르지 못하게 */}
      <div inert={modalOpen} className="flex flex-1 flex-col lg:min-h-0">
        {noId && empty ? (
          <NoticeEmpty onCreate={onCreate} />
        ) : noId && listFailed ? (
          <section className="flex flex-1 items-center justify-center rounded-card border border-line bg-surface">
            <NoticeError onRetry={() => refetch()} />
          </section>
        ) : (
          <section className="flex flex-1 flex-col rounded-card border border-line bg-surface lg:min-h-0 lg:flex-row lg:overflow-hidden">
            <NoticeList
              query={list}
              items={items}
              basePath={basePath}
              selectedId={selectedId}
              busy={dialog.busy}
              onCreate={onCreate}
            />
            {/* key: 다른 공지를 열면 본문 스크롤을 맨 위로 */}
            <div key={param} className="min-w-0 flex-1 lg:overflow-y-auto">
              {renderDetail()}
            </div>
          </section>
        )}
      </div>
      {renderModal()}
      {/* Modal과 z-index가 같아 뒤에 그려야 위에 보인다 */}
      <Toast message={toast.message} />
    </>
  )
}
