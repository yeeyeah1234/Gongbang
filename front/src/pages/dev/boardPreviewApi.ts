import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { apiClient } from '../../api/client'
import type { TeamMemberApiItem } from '../../features/chat/types'
import type {
  WhiteboardListItem,
  WhiteboardRenameRequest,
} from '../../features/workspace/board/types'
import { strip, textLength } from '../../features/workspace/notice/validation'
import { nowKstDateTime } from '../../features/workspace/sidebar/relativeTime'

// 개발 전용: /dev/board-preview가 떠 있는 동안만 팀 요약·팀원·화이트보드 API를 메모리 데이터로 답한다 (documentPreviewApi와 같은 방식).
export type PreviewView = 'normal' | 'empty' | 'error' | 'member'

export const PREVIEW_TEAM_ID = 0
const LEADER_ID = 1
const MEMBER_ID = 2
const OTHER_ID = 3
const LEFT_ID = 4 // 팀을 나간 회원 (팀원 목록에 없음)
const NICKNAMES: Record<number, string> = { 1: '김하늘', 2: '이바다', 3: '박산들' }
const STALE_PERMISSION_ID = 90 // 삭제 버튼이 보이지만 삭제 시 403 NOT_WHITEBOARD_AUTHOR
const GONE_ON_WRITE_ID = 91 // 이름 바꾸기·삭제 시 404 RESOURCE_NOT_FOUND
const MAX_BOARDS = 30
const TITLE_MAX = 50
const DELAY_MS = 300

const MESSAGES: Record<string, string> = {
  INTERNAL_SERVER_ERROR: '서버 오류(미리보기)',
  INVALID_INPUT: '잘못된 입력값입니다.',
  NOT_WHITEBOARD_AUTHOR: '작성자나 팀장만 지울 수 있어요',
  RESOURCE_NOT_FOUND: '없거나 삭제된 항목이에요',
  WHITEBOARD_LIMIT: '보드는 30개까지 만들 수 있어요',
}

function kstAgo(ms: number) {
  return nowKstDateTime(new Date(Date.now() - ms))
}

function editor(memberId: number) {
  return { memberId, nickname: NICKNAMES[memberId] ?? '탈퇴한 사용자' }
}

function createInitialBoards(view: PreviewView): WhiteboardListItem[] {
  const MIN = 60 * 1000
  const HOUR = 60 * MIN
  const DAY = 24 * HOUR
  // member 화면에서 「삭제」가 보이지만 서버는 403을 주는 상황: 만든 사람을 나로 잘못 알고 있던 경우
  const staleCreator = view === 'member' ? MEMBER_ID : OTHER_ID
  return [
    {
      whiteboardId: 10,
      title: '아이디어 브레인스토밍',
      creatorId: LEADER_ID,
      lastEditor: editor(MEMBER_ID),
      updatedAt: kstAgo(20 * 1000),
    },
    {
      whiteboardId: 9,
      title: '서비스 흐름도',
      creatorId: MEMBER_ID,
      lastEditor: editor(MEMBER_ID),
      updatedAt: kstAgo(12 * MIN),
    },
    {
      // 아직 아무도 그리지 않은 보드: lastEditor null → 만든 사람 닉네임으로 표시
      whiteboardId: 8,
      title: '제목 없는 보드',
      creatorId: OTHER_ID,
      lastEditor: null,
      updatedAt: kstAgo(3 * HOUR),
    },
    {
      whiteboardId: 7,
      title: '제목이아주길어서사이드바에서한줄로잘리고말줄임표가나와야하는보드제목테스트입니다',
      creatorId: LEADER_ID,
      lastEditor: editor(LEADER_ID),
      updatedAt: kstAgo(26 * HOUR),
    },
    {
      // 만든 사람이 팀을 나갔고 아무도 그리지 않은 보드 → 「-」
      whiteboardId: 6,
      title: '나간 팀원이 만든 보드',
      creatorId: LEFT_ID,
      lastEditor: null,
      updatedAt: kstAgo(7 * DAY),
    },
    {
      whiteboardId: STALE_PERMISSION_ID,
      title: '권한이 바뀐 보드 (삭제 시 403)',
      creatorId: staleCreator,
      lastEditor: editor(OTHER_ID),
      updatedAt: kstAgo(9 * DAY),
    },
    {
      whiteboardId: GONE_ON_WRITE_ID,
      title: '이미 지워진 보드 (이름 변경·삭제 시 404)',
      creatorId: MEMBER_ID,
      lastEditor: editor(MEMBER_ID),
      updatedAt: kstAgo(11 * DAY),
    },
  ]
}

type Result = { status: number; data?: unknown; code?: string }

export interface BoardPreviewController {
  cleanup: () => void
  fillToLimit: () => void
  removeExternally: (boardId: number) => boolean
}

export function installBoardPreviewApi(view: PreviewView): BoardPreviewController {
  let boards = view === 'empty' ? [] : createInitialBoards(view)
  let nextId = 101
  const isLeader = view !== 'member'
  const me = view === 'member' ? MEMBER_ID : LEADER_ID

  const members: TeamMemberApiItem[] = [LEADER_ID, MEMBER_ID, OTHER_ID].map((memberId) => ({
    memberId,
    nickname: NICKNAMES[memberId],
    profileImageUrl: null,
    role: memberId === LEADER_ID ? 'LEADER' : 'MEMBER',
    colorIndex: memberId as TeamMemberApiItem['colorIndex'],
    desiredRoles: [],
  }))

  const handle = ({ url = '', method = 'get', data }: InternalAxiosRequestConfig): Result => {
    if (url === `/api/teams/${PREVIEW_TEAM_ID}`) {
      return {
        status: 200,
        data: {
          teamId: PREVIEW_TEAM_ID,
          teamName: '공방팀',
          contestId: 1,
          contestTitle: '미리보기 공모전',
          deadline: null,
          isLeader,
          myMemberId: me,
        },
      }
    }

    if (view === 'error') return { status: 500, code: 'INTERNAL_SERVER_ERROR' }

    if (method === 'get' && url === `/api/teams/${PREVIEW_TEAM_ID}/members`) {
      return { status: 200, data: members }
    }

    if (method === 'get' && url === `/api/teams/${PREVIEW_TEAM_ID}/whiteboards`) {
      // 캐시가 이 배열을 그대로 들고 있지 않게 복사해서 준다 (실제 서버처럼 매번 새 JSON)
      return { status: 200, data: structuredClone(boards) }
    }

    if (method === 'post' && url === `/api/teams/${PREVIEW_TEAM_ID}/whiteboards`) {
      if (boards.length >= MAX_BOARDS) return { status: 409, code: 'WHITEBOARD_LIMIT' }
      const id = nextId++
      boards.unshift({
        whiteboardId: id,
        title: '제목 없는 보드',
        creatorId: me,
        lastEditor: null,
        updatedAt: nowKstDateTime(),
      })
      return { status: 200, data: { whiteboardId: id, title: '제목 없는 보드' } }
    }

    const id = Number(url.split('/').pop())
    if (id === GONE_ON_WRITE_ID) {
      boards = boards.filter((item) => item.whiteboardId !== id)
    }
    const board = boards.find((item) => item.whiteboardId === id)
    if (!board) return { status: 404, code: 'RESOURCE_NOT_FOUND' }

    if (method === 'patch') {
      const body: Partial<WhiteboardRenameRequest> = data ? JSON.parse(data) : {}
      const trimmed = strip(body.title ?? '')
      if (textLength(trimmed) > TITLE_MAX) return { status: 400, code: 'INVALID_INPUT' }
      const nextTitle = trimmed.length > 0 ? trimmed : '제목 없는 보드'
      if (board.title !== nextTitle) {
        // 제목 변경은 lastEditor를 바꾸지 않는다 (DB 명세서: 동기화 서버 저장 때만 채움)
        board.title = nextTitle
        board.updatedAt = nowKstDateTime()
        boards = [board, ...boards.filter((item) => item.whiteboardId !== board.whiteboardId)]
      }
      return { status: 200, data: { whiteboardId: board.whiteboardId, title: board.title } }
    }

    if (method === 'delete') {
      if (id === STALE_PERMISSION_ID && !isLeader) {
        // 권한이 바뀐 상황 재현: 403 뒤 다음 목록 조회에서는 다른 사람이 만든 보드로 보인다
        board.creatorId = OTHER_ID
        return { status: 403, code: 'NOT_WHITEBOARD_AUTHOR' }
      }
      if (!isLeader && board.creatorId !== me) {
        return { status: 403, code: 'NOT_WHITEBOARD_AUTHOR' }
      }
      boards = boards.filter((item) => item.whiteboardId !== id)
      return { status: 200 }
    }

    return { status: 404, code: 'RESOURCE_NOT_FOUND' }
  }

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

  const interceptorId = apiClient.interceptors.request.use((config) => {
    const isPreviewApi = /^\/api\/(teams\/0($|\/whiteboards|\/members)|whiteboards\/)/.test(
      config.url ?? '',
    )
    return isPreviewApi ? Object.assign(config, { adapter }) : config
  })

  return {
    cleanup: () => apiClient.interceptors.request.eject(interceptorId),
    fillToLimit: () => {
      while (boards.length < MAX_BOARDS) {
        const id = nextId++
        boards.push({
          whiteboardId: id,
          title: `채워진 보드 ${boards.length + 1}`,
          creatorId: LEADER_ID,
          lastEditor: editor(LEADER_ID),
          updatedAt: kstAgo(15 * 24 * 60 * 60 * 1000),
        })
      }
    },
    removeExternally: (boardId: number) => {
      const before = boards.length
      boards = boards.filter((item) => item.whiteboardId !== boardId)
      return boards.length < before
    },
  }
}
