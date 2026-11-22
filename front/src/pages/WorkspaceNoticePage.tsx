import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useEffectEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { getApiError } from '../api/errors'
import { Toast } from '../components/ui/Toast'
import { useWorkspace } from '../features/workspace/context'
import { workspaceKeys } from '../features/workspace/hooks'
import { NoticeDetail } from '../features/workspace/notice/components/NoticeDetail'
import { NoticeList } from '../features/workspace/notice/components/NoticeList'
import {
  NoticeEmpty,
  NoticeError,
  NoticeSkeleton,
} from '../features/workspace/notice/components/NoticeStates'
import {
  useForgetNotice,
  useNoticeDetail,
  useNoticeList,
  useSyncNoticeRead,
} from '../features/workspace/notice/hooks'
import { useTimedToast } from '../hooks/useTimedToast'

interface WorkspaceNoticePageProps {
  // 개발용 미리보기만 넘긴다. 기본은 /workspaces/:teamId/notice
  basePath?: string
}

// NTC-01 공지 탭: /workspaces/:teamId/notice/:noticeId?
// TODO(NTC-02~04 쓰기 카드): summary.isLeader일 때 + 공지 작성·안내 문구(목록, 빈 화면), 고정·수정·삭제(상세) 표시
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
  const { teamId } = useWorkspace()
  const { noticeId: param } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useTimedToast()
  const forgetNotice = useForgetNotice(teamId)

  const selectedId = parseNoticeId(param)
  const list = useNoticeList(teamId)
  const detail = useNoticeDetail(teamId, selectedId)

  // page 방식이라 그사이 새 글이 끼면 다음 쪽에 같은 공지가 또 온다
  const items = [
    ...new Map(
      list.data?.pages.flatMap((page) => page.items).map((item) => [item.noticeId, item]),
    ).values(),
  ]

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
    toast.show('이 공지는 삭제되었어요')
    navigate(basePath, { replace: true })
    forgetNotice(selectedId)
  })

  useEffect(() => {
    if (!gone) return
    let active = true
    const leave = async () => {
      if (notMember) {
        // 팀에서 빠진 것이면 레이아웃이 권한 없음 화면으로 바꾸고, 팀원이면 다른 팀 공지다
        const queryKey = workspaceKeys.summary(teamId)
        await queryClient.invalidateQueries({ queryKey })
        if (queryClient.getQueryState(queryKey)?.status === 'error') return
      }
      if (active) leaveGoneNotice()
    }
    void leave()
    return () => {
      active = false
    }
  }, [gone, notMember, param, teamId, queryClient])

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
    if (!noId || empty || listFailed) return
    let active = true
    // cancelRefetch false: 이미 나간 조회가 있으면 그 결과를 같이 기다린다
    void refetch({ cancelRefetch: false }).then(({ data }) => {
      const first = data?.pages[0]?.items[0]
      if (active && first) navigate(`${basePath}/${first.noticeId}`, { replace: true })
    })
    return () => {
      active = false
    }
  }, [noId, empty, listFailed, refetch, navigate, basePath])

  const renderDetail = () => {
    if (gone) return <NoticeSkeleton />
    // 다시 조회가 실패해도 이미 받은 내용은 그대로 둔다
    if (notice) return <NoticeDetail notice={notice} />
    if (detail.isError) return <NoticeError onRetry={() => detail.refetch()} />
    return <NoticeSkeleton />
  }

  return (
    <>
      {noId && empty ? (
        <NoticeEmpty />
      ) : noId && listFailed ? (
        <section className="flex flex-1 items-center justify-center rounded-card border border-line bg-surface">
          <NoticeError onRetry={() => refetch()} />
        </section>
      ) : (
        <section className="flex flex-1 flex-col rounded-card border border-line bg-surface lg:min-h-0 lg:flex-row lg:overflow-hidden">
          <NoticeList query={list} items={items} basePath={basePath} selectedId={selectedId} />
          {/* key: 다른 공지를 열면 본문 스크롤을 맨 위로 */}
          <div key={param} className="min-w-0 flex-1 lg:overflow-y-auto">
            {renderDetail()}
          </div>
        </section>
      )}
      <Toast message={toast.message} />
    </>
  )
}
