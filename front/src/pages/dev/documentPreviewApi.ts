import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { apiClient } from '../../api/client'
import type {
  DocumentListItem,
  DocumentRenameRequest,
} from '../../features/workspace/document/types'
import { strip, textLength } from '../../features/workspace/notice/validation'
import { nowKstDateTime } from '../../features/workspace/sidebar/relativeTime'

// 개발 전용: /dev/document-preview가 떠 있는 동안만 팀 요약·문서 API를 메모리 데이터로 답한다.
export type PreviewView = 'normal' | 'empty' | 'error' | 'member'

export const PREVIEW_TEAM_ID = 0
const LEADER_ID = 1
const MEMBER_ID = 2
const OTHER_ID = 3
const NICKNAMES: Record<number, string> = { 1: '김하늘', 2: '이바다', 3: '박산들' }
const STALE_PERMISSION_ID = 90 // 삭제 버튼이 보이지만 삭제 시 403 NOT_DOCUMENT_AUTHOR 후 권한 갱신
const GONE_ON_WRITE_ID = 91 // 이름 바꾸기·삭제 시 404 RESOURCE_NOT_FOUND
const MAX_DOCUMENTS = 50
const TITLE_MAX = 50
const DELAY_MS = 300

const MESSAGES: Record<string, string> = {
  INTERNAL_SERVER_ERROR: '서버 오류(미리보기)',
  INVALID_INPUT: '잘못된 입력값입니다.',
  NOT_DOCUMENT_AUTHOR: '작성자나 팀장만 지울 수 있어요',
  RESOURCE_NOT_FOUND: '없거나 삭제된 항목이에요',
  DOCUMENT_LIMIT: '문서 개수 제한을 초과했습니다.',
}

interface StoredDocument {
  id: number
  title: string
  updatedByNickname: string
  updatedAt: string
  createdBy: number | null
  staleDeletePerm?: boolean
}

function kstAgo(ms: number) {
  return nowKstDateTime(new Date(Date.now() - ms))
}

function createInitialDocuments(): StoredDocument[] {
  const MIN = 60 * 1000
  const HOUR = 60 * MIN
  const DAY = 24 * HOUR
  return [
    {
      id: 10,
      title: '공모전 기획서 초안',
      updatedByNickname: NICKNAMES[LEADER_ID],
      updatedAt: kstAgo(20 * 1000),
      createdBy: LEADER_ID,
    },
    {
      id: 9,
      title: '1주차 회의록',
      updatedByNickname: NICKNAMES[MEMBER_ID],
      updatedAt: kstAgo(12 * MIN),
      createdBy: MEMBER_ID,
    },
    {
      id: 8,
      title: '역할 분담 및 일정 정리',
      updatedByNickname: NICKNAMES[OTHER_ID],
      updatedAt: kstAgo(3 * HOUR),
      createdBy: OTHER_ID,
    },
    {
      id: 7,
      title: '제목이아주길어서사이드바에서한줄로잘리고말줄임표가나와야하는문서제목테스트입니다',
      updatedByNickname: NICKNAMES[LEADER_ID],
      // 어제(KST)면서 24시간 이상 지난 시각
      updatedAt: kstAgo(26 * HOUR),
      createdBy: LEADER_ID,
    },
    {
      id: 6,
      title: '참고 자료 모음',
      updatedByNickname: '탈퇴한 사용자',
      updatedAt: kstAgo(7 * DAY),
      createdBy: null,
    },
    {
      id: STALE_PERMISSION_ID,
      title: '권한이 바뀐 문서 (삭제 시 403)',
      updatedByNickname: NICKNAMES[OTHER_ID],
      updatedAt: kstAgo(9 * DAY),
      createdBy: OTHER_ID,
      staleDeletePerm: true,
    },
    {
      id: GONE_ON_WRITE_ID,
      title: '이미 지워진 문서 (이름 변경·삭제 시 404)',
      updatedByNickname: NICKNAMES[MEMBER_ID],
      updatedAt: kstAgo(11 * DAY),
      createdBy: MEMBER_ID,
    },
  ]
}

type Result = { status: number; data?: unknown; code?: string }

export interface DocumentPreviewController {
  cleanup: () => void
  fillToLimit: () => void
  removeExternally: (documentId: number) => boolean
}

export function installDocumentPreviewApi(view: PreviewView): DocumentPreviewController {
  let docs = view === 'empty' ? [] : createInitialDocuments()
  let nextId = 101
  const isLeader = view !== 'member'
  const me = view === 'member' ? MEMBER_ID : LEADER_ID
  const myNickname = NICKNAMES[me]

  const toResponse = (doc: StoredDocument): DocumentListItem => ({
    id: doc.id,
    title: doc.title,
    updatedByNickname: doc.updatedByNickname,
    updatedAt: doc.updatedAt,
    createdBy: doc.createdBy,
    canDelete: Boolean(doc.staleDeletePerm) || isLeader || doc.createdBy === me,
  })

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

    if (method === 'get' && url === `/api/teams/${PREVIEW_TEAM_ID}/documents`) {
      return { status: 200, data: docs.map(toResponse) }
    }

    if (method === 'post' && url === `/api/teams/${PREVIEW_TEAM_ID}/documents`) {
      if (docs.length >= MAX_DOCUMENTS) return { status: 409, code: 'DOCUMENT_LIMIT' }
      const id = nextId++
      docs.unshift({
        id,
        title: '제목 없는 문서',
        updatedByNickname: myNickname,
        updatedAt: nowKstDateTime(),
        createdBy: me,
      })
      return { status: 200, data: { documentId: id } }
    }

    const id = Number(url.split('/').pop())
    if (id === GONE_ON_WRITE_ID) {
      docs = docs.filter((item) => item.id !== id)
    }
    const doc = docs.find((item) => item.id === id)
    if (!doc) return { status: 404, code: 'RESOURCE_NOT_FOUND' }

    if (method === 'patch') {
      const body: Partial<DocumentRenameRequest> = data ? JSON.parse(data) : {}
      const trimmed = strip(body.title ?? '')
      if (textLength(trimmed) > TITLE_MAX) return { status: 400, code: 'INVALID_INPUT' }
      const nextTitle = trimmed.length > 0 ? trimmed : '제목 없는 문서'
      if (doc.title !== nextTitle) {
        doc.title = nextTitle
        doc.updatedByNickname = myNickname
        doc.updatedAt = nowKstDateTime()
        docs = [doc, ...docs.filter((item) => item.id !== doc.id)]
      }
      return { status: 200, data: { documentId: doc.id } }
    }

    if (method === 'delete') {
      if (doc.staleDeletePerm) {
        // 권한이 바뀐 상황 재현: 403 응답 후 다음 목록 조회에서는 삭제 메뉴가 사라진다
        doc.staleDeletePerm = false
        return { status: 403, code: 'NOT_DOCUMENT_AUTHOR' }
      }
      if (!isLeader && doc.createdBy !== me) {
        return { status: 403, code: 'NOT_DOCUMENT_AUTHOR' }
      }
      docs = docs.filter((item) => item.id !== id)
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
    const isPreviewApi = /^\/api\/(teams\/0($|\/documents)|documents\/)/.test(config.url ?? '')
    return isPreviewApi ? Object.assign(config, { adapter }) : config
  })

  return {
    cleanup: () => apiClient.interceptors.request.eject(interceptorId),
    fillToLimit: () => {
      while (docs.length < MAX_DOCUMENTS) {
        const id = nextId++
        docs.push({
          id,
          title: `채워진 문서 ${docs.length + 1}`,
          updatedByNickname: NICKNAMES[LEADER_ID],
          updatedAt: kstAgo(15 * 24 * 60 * 60 * 1000),
          createdBy: LEADER_ID,
        })
      }
    },
    removeExternally: (documentId: number) => {
      const before = docs.length
      docs = docs.filter((item) => item.id !== documentId)
      return docs.length < before
    },
  }
}
