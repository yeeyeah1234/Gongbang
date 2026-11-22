import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { apiClient } from '../../api/client'
import type { NoticeCreateRequest, NoticeDetailData } from '../../features/workspace/notice/types'

// 개발 전용: /dev/notice-preview가 떠 있는 동안만 공지·팀 요약 API를 메모리 데이터로 답한다.
export type PreviewView =
  'leader' | 'member' | 'empty' | 'single' | 'conflict' | 'forbidden' | 'error'

export const PREVIEW_TEAM_ID = 0
const LEADER_ID = 1
const MEMBER_ID = 2
const PINNED_ID = 8
const DELETED_ID = 22 // 목록에는 있지만 열면 404
const GONE_ON_WRITE_ID = 21 // 열리지만 수정·삭제·고정하면 404
const OTHER_TEAM_ID = 999 // 주소로 열면 다른 팀 공지
const DELAY_MS = 300

// 서버 ErrorCode 문구 그대로
const MESSAGES: Record<string, string> = {
  INTERNAL_SERVER_ERROR: '서버 오류(미리보기)',
  NOT_TEAM_LEADER: '팀장만 접근할 수 있습니다.',
  NOTICE_PIN_CONFLICT: '다른 공지가 방금 고정됐어요. 새로고침 후 다시 시도해 주세요',
  RESOURCE_NOT_FOUND: '없거나 삭제된 항목이에요',
}

// 오늘(KST) 기준 n일 전
function daysAgo(days: number, time: string) {
  const date = new Date(Date.now() + (9 - days * 24) * 60 * 60 * 1000)
  return `${date.toISOString().slice(0, 10)}T${time}:00`
}

// 지금(KST)
function nowKst() {
  return new Date(Date.now() + 9 * 60 * 60 * 1000).toISOString().slice(0, 19)
}

const LONG_CONTENT = [
  '이번 주 할 일을 공유해요. 첫 줄이 길면 목록에서는 한 줄로 잘리고 말줄임표가 붙어야 해요.',
  '',
  '자료: https://example.com/docs?q=1&lang=ko에서 확인',
  '괄호: (https://example.com/a_(b)). 문장 끝: https://example.com/end.',
  '링크가 되면 안 됨: javascript:alert(1) <img src=x onerror=alert(1)>',
  '',
  ...Array.from({ length: 20 }, (_, i) => `${i + 1}. 긴 본문 스크롤 확인`),
].join('\n')

function createNotices(): NoticeDetailData[] {
  return Array.from({ length: 25 }, (_, i) => {
    const id = 25 - i
    let author: NoticeDetailData['author'] = { memberId: LEADER_ID, nickname: '김하늘' }
    if (i % 4 === 1) author = { memberId: 4, nickname: '최노을' }
    if (i === 4) author = { memberId: null, nickname: '탈퇴한 사용자' }

    let title = `미리보기 공지 ${id}`
    if (id === DELETED_ID) title = '누르면 404 (삭제된 공지)'
    if (id === GONE_ON_WRITE_ID) title = '수정·삭제·고정하면 404'
    if (i === 1)
      title = '제목이아주길고띄어쓰기가없어도한줄로잘리고상세에서는줄바꿈되어야하는공지제목입니다'

    return {
      noticeId: id,
      teamId: PREVIEW_TEAM_ID,
      title,
      content:
        i < 2 || id === PINNED_ID ? LONG_CONTENT : `공지 ${id}의 내용입니다.\n둘째 줄입니다.`,
      author,
      createdAt: daysAgo(i < 2 ? 0 : i, i === 0 ? '21:10' : '09:05'),
      editedAt: i % 6 === 1 ? daysAgo(0, '10:00') : null,
      isPinned: id === PINNED_ID,
      isEdited: i % 6 === 1,
    }
  })
}

type Result = { status: number; data?: unknown; code?: string }
const OK: Result = { status: 200 }

function initialNotices(view: PreviewView) {
  if (view === 'empty') return []
  const notices = createNotices()
  return view === 'single' ? notices.filter((notice) => notice.noticeId === PINNED_ID) : notices
}

function createHandler(view: PreviewView) {
  let notices = initialNotices(view)
  let nextId = 101
  let isLeader = view !== 'member'
  let conflictLeft = view === 'conflict'
  const me = view === 'member' ? MEMBER_ID : LEADER_ID
  const read = new Set<number>()
  const isUnread = (notice: NoticeDetailData) =>
    notice.author.memberId !== me && !read.has(notice.noticeId)

  const pinOnly = (id: number | null) => {
    notices = notices.map((notice) => ({ ...notice, isPinned: notice.noticeId === id }))
  }

  const write = (method: string, path: string, body: Partial<NoticeCreateRequest>): Result => {
    if (view === 'forbidden') isLeader = false // 첫 쓰기부터 403
    if (!isLeader) return { status: 403, code: 'NOT_TEAM_LEADER' }

    const isPinPath = path.endsWith('/pin')
    const id = method === 'post' ? null : Number(path.split('/')[3])
    if (id === GONE_ON_WRITE_ID) notices = notices.filter((notice) => notice.noticeId !== id)
    const notice = notices.find((item) => item.noticeId === id)
    if (id !== null && !notice) return { status: 404, code: 'RESOURCE_NOT_FOUND' }

    const pins = (method === 'put' && isPinPath) || (method === 'post' && body.pinned)
    if (pins && conflictLeft) {
      // 그사이 다른 사람이 고정한 상태로 바뀐다. 다시 누르면 성공
      conflictLeft = false
      pinOnly(id === 5 ? 6 : 5)
      return { status: 409, code: 'NOTICE_PIN_CONFLICT' }
    }

    const { title = '', content = '' } = body
    if (!notice) {
      const noticeId = nextId++
      notices.unshift({
        noticeId,
        teamId: PREVIEW_TEAM_ID,
        title,
        content,
        author: { memberId: me, nickname: '김하늘' },
        createdAt: nowKst(),
        editedAt: null,
        isPinned: false,
        isEdited: false,
      })
      if (pins) pinOnly(noticeId)
      return { status: 200, data: { noticeId } }
    }

    if (method === 'put') pinOnly(id)
    else if (isPinPath) pinOnly(null)
    else if (method === 'delete') notices = notices.filter((item) => item !== notice)
    else if (notice.title !== title || notice.content !== content)
      Object.assign(notice, { title, content, isEdited: true, editedAt: nowKst() })
    return OK
  }

  return ({ url = '', method = 'get', params, data }: InternalAxiosRequestConfig): Result => {
    if (url === `/api/teams/${PREVIEW_TEAM_ID}`) {
      const summary = { teamId: PREVIEW_TEAM_ID, teamName: '공방팀', isLeader, myMemberId: me }
      return { status: 200, data: { ...summary, contestTitle: null, deadline: null } }
    }
    if (view === 'error') return { status: 500, code: 'INTERNAL_SERVER_ERROR' }
    if (method !== 'get') return write(method, url, data ? JSON.parse(data) : {})

    if (url.endsWith('/unread-count'))
      return { status: 200, data: { count: notices.filter(isUnread).length } }
    if (url.endsWith('/pinned'))
      return { status: 200, data: notices.find((notice) => notice.isPinned) ?? null }

    if (url.startsWith('/api/teams/')) {
      const { page = 0, size = 10 } = params ?? {}
      const sorted = [...notices].sort(
        (a, b) => Number(b.isPinned) - Number(a.isPinned) || b.noticeId - a.noticeId,
      )
      const items = sorted.slice(page * size, (page + 1) * size).map((notice) => ({
        noticeId: notice.noticeId,
        title: notice.title,
        preview: Array.from(notice.content.split('\n')[0]).slice(0, 100).join(''),
        author: notice.author,
        createdAt: notice.createdAt,
        isPinned: notice.isPinned,
        isEdited: notice.isEdited,
        isUnread: isUnread(notice),
      }))
      const hasNext = (page + 1) * size < notices.length
      return { status: 200, data: { items, page, size, totalCount: notices.length, hasNext } }
    }

    const id = Number(url.split('/').pop())
    if (id === OTHER_TEAM_ID && notices[0])
      return { status: 200, data: { ...notices[0], noticeId: id, teamId: 7 } }
    if (id === DELETED_ID) notices = notices.filter((notice) => notice.noticeId !== id)
    const notice = notices.find((item) => item.noticeId === id)
    if (!notice) return { status: 404, code: 'RESOURCE_NOT_FOUND' }
    read.add(id)
    return { status: 200, data: { ...notice } }
  }
}

// 돌려주는 함수를 부르면 해제된다
export function installPreviewApi(view: PreviewView) {
  const handle = createHandler(view)

  const adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    await new Promise((resolve) => setTimeout(resolve, DELAY_MS))
    const { status, data = null, code } = handle(config)
    const error = code ? { code, message: MESSAGES[code] ?? code } : null
    const response: AxiosResponse = {
      status,
      statusText: String(status),
      headers: {},
      config,
      data: { success: !error, data, error },
    }
    if (error) throw new AxiosError(code, code, config, null, response)
    return response
  }

  const id = apiClient.interceptors.request.use((config) => {
    const isPreviewApi = /^\/api\/(teams\/0($|\/notices)|notices\/)/.test(config.url ?? '')
    return isPreviewApi ? Object.assign(config, { adapter }) : config
  })
  return () => apiClient.interceptors.request.eject(id)
}
