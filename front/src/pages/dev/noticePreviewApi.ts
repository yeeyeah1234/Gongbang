import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { apiClient } from '../../api/client'
import type { NoticeDetailData } from '../../features/workspace/notice/types'

// 개발 전용: /dev/notice-preview가 떠 있는 동안만 공지 API를 고정 데이터로 답한다.
export type PreviewView = 'leader' | 'member' | 'empty' | 'error'

export const PREVIEW_TEAM_ID = 0
export const PREVIEW_LEADER_ID = 1
export const PREVIEW_MEMBER_ID = 2
const DELETED_ID = 22 // 목록에는 있지만 열면 404
const OTHER_TEAM_ID = 999 // 주소로 열면 다른 팀 공지
const DELAY_MS = 300

// 오늘(KST) 기준 n일 전
function daysAgo(days: number, time: string) {
  const date = new Date(Date.now() + (9 - days * 24) * 60 * 60 * 1000)
  return `${date.toISOString().slice(0, 10)}T${time}:00`
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
    let author: NoticeDetailData['author'] = { memberId: PREVIEW_LEADER_ID, nickname: '김하늘' }
    if (i % 4 === 1) author = { memberId: 4, nickname: '최노을' }
    if (i === 4) author = { memberId: null, nickname: '탈퇴한 사용자' }

    let title = `미리보기 공지 ${id}`
    if (id === DELETED_ID) title = '누르면 404 (삭제된 공지)'
    if (i === 1)
      title = '제목이아주길고띄어쓰기가없어도한줄로잘리고상세에서는줄바꿈되어야하는공지제목입니다'

    return {
      noticeId: id,
      teamId: PREVIEW_TEAM_ID,
      title,
      content: i < 2 || id === 8 ? LONG_CONTENT : `공지 ${id}의 내용입니다.\n둘째 줄입니다.`,
      author,
      createdAt: daysAgo(i < 2 ? 0 : i, i === 0 ? '21:10' : '09:05'),
      editedAt: i % 6 === 1 ? daysAgo(0, '10:00') : null,
      isPinned: id === 8,
      isEdited: i % 6 === 1,
    }
  }).sort((a, b) => Number(b.isPinned) - Number(a.isPinned))
}

type Result = { status: number; data?: unknown; code?: string }

function createHandler(view: PreviewView) {
  let notices = view === 'empty' ? [] : createNotices()
  const me = view === 'member' ? PREVIEW_MEMBER_ID : PREVIEW_LEADER_ID
  const read = new Set<number>()
  const isUnread = (notice: NoticeDetailData) =>
    notice.author.memberId !== me && !read.has(notice.noticeId)

  return (path: string, params: { page?: number; size?: number } = {}): Result => {
    if (view === 'error') return { status: 500, code: 'INTERNAL_SERVER_ERROR' }
    if (path.endsWith('/unread-count'))
      return { status: 200, data: { count: notices.filter(isUnread).length } }
    if (path.endsWith('/pinned'))
      return { status: 200, data: notices.find((notice) => notice.isPinned) ?? null }

    if (path.startsWith('/api/teams/')) {
      const { page = 0, size = 10 } = params
      const items = notices.slice(page * size, (page + 1) * size).map((notice) => ({
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

    const id = Number(path.split('/').pop())
    if (id === OTHER_TEAM_ID && notices[0])
      return { status: 200, data: { ...notices[0], noticeId: id, teamId: 7 } }
    if (id === DELETED_ID) notices = notices.filter((notice) => notice.noticeId !== id)
    const notice = notices.find((item) => item.noticeId === id)
    if (!notice) return { status: 404, code: 'RESOURCE_NOT_FOUND' }
    read.add(id)
    return { status: 200, data: notice }
  }
}

// 돌려주는 함수를 부르면 해제된다
export function installPreviewApi(view: PreviewView) {
  const handle = createHandler(view)

  const adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    await new Promise((resolve) => setTimeout(resolve, DELAY_MS))
    const { status, data = null, code } = handle(config.url ?? '', config.params)
    const error = code ? { code, message: code } : null
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
    const isNoticeApi = /^\/api\/(teams\/0\/notices|notices\/)/.test(config.url ?? '')
    return isNoticeApi ? Object.assign(config, { adapter }) : config
  })
  return () => apiClient.interceptors.request.eject(id)
}
