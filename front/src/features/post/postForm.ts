import type { PostCreateRequest, PostDetail, PostUpdateRequest } from './types'

// PST-002 ① 입력 범위 (서버 PostCommandService와 같은 값)
export const TITLE_MAX = 50
export const CONTENT_MAX = 2000
export const EXTERNAL_TITLE_MAX = 50
export const URL_MAX = 500
export const TARGET_COUNT_MIN = 2
export const TARGET_COUNT_MAX = 8

// 화면에서 고른 서비스 공모전 (검색 결과 카드에서 필요한 값만)
export interface SelectedContest {
  contestId: number
  title: string
  categoryName: string
  deadline: string // YYYY-MM-DD
}

export interface PostFormValues {
  contestMode: 'service' | 'external'
  contest: SelectedContest | null
  externalTitle: string
  externalCategoryCode: string
  externalDeadline: string
  externalUrl: string
  title: string
  targetCount: number
  recruitStartDate: string
  recruitEndDate: string
  relatedUrl: string
  content: string
}

export type PostFormField =
  | 'contest'
  | 'externalTitle'
  | 'externalCategoryCode'
  | 'externalDeadline'
  | 'externalUrl'
  | 'title'
  | 'targetCount'
  | 'period'
  | 'relatedUrl'
  | 'content'

export type PostFormErrors = Partial<Record<PostFormField, string>>

// PST-002 ⑥ 기능정의서 문구
export const MESSAGES = {
  contestRequired: '공모전을 선택해 주세요',
  contestEnded: '이미 마감된 공모전이에요',
  endBeforeStart: '모집 종료일은 시작일보다 앞설 수 없어요',
  endAfterDeadline: '모집 종료일은 공모전 마감일보다 뒤일 수 없어요',
  invalidPeriod: '모집 기간을 다시 확인해 주세요',
  // 아래는 기능정의서에 문구가 없어 정한 것 (제목은 Figma 02-3 문구)
  titleRequired: '제목을 입력해 주세요',
  contentRequired: '모집 내용을 입력해 주세요',
  periodRequired: '모집 기간을 입력해 주세요',
  externalTitleRequired: '공모전명을 입력해 주세요',
  externalCategoryRequired: '분야를 선택해 주세요',
  externalDeadlineRequired: '접수 마감일을 입력해 주세요',
  urlInvalid: 'http:// 또는 https://로 시작하는 주소를 입력해 주세요',
  // PST-003 ④
  targetBelowCurrent: '현재 확정된 팀원 수보다 적게 설정할 수 없습니다',
  targetEqualsCurrent:
    '현재 확정된 팀원 수와 같게 설정할 수 없습니다. 모집을 마치려면 「모집 완료」를 눌러 주세요',
} as const

export const EMPTY_POST_FORM: PostFormValues = {
  contestMode: 'service',
  contest: null,
  externalTitle: '',
  externalCategoryCode: '',
  externalDeadline: '',
  externalUrl: '',
  title: '',
  // Figma 02-1 처음 값
  targetCount: 4,
  recruitStartDate: '',
  recruitEndDate: '',
  relatedUrl: '',
  content: '',
}

// 사용자 기기 날짜(한국 시간 전제)를 YYYY-MM-DD로
export function todayString(now: Date = new Date()) {
  return [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-')
}

// PST-002 ④, AGENTS.md 9: http:// 또는 https://로 시작하고 주소(호스트)가 있어야 한다
export function isHttpUrl(value: string) {
  const url = value.trim()
  if (!/^https?:\/\//i.test(url)) return false
  try {
    return new URL(url).hostname.length > 0
  } catch {
    return false
  }
}

function contestDeadline(values: PostFormValues) {
  return values.contestMode === 'service' ? values.contest?.deadline : values.externalDeadline
}

// PST-002 ①~④ 화면 검사. 서버도 같은 규칙으로 다시 검사한다.
export function validatePostForm(values: PostFormValues, today: string = todayString()) {
  const errors: PostFormErrors = {}

  if (values.contestMode === 'service') {
    if (!values.contest) errors.contest = MESSAGES.contestRequired
    else if (values.contest.deadline < today) errors.contest = MESSAGES.contestEnded
  } else {
    if (!values.externalTitle.trim()) errors.externalTitle = MESSAGES.externalTitleRequired
    if (!values.externalCategoryCode)
      errors.externalCategoryCode = MESSAGES.externalCategoryRequired
    if (!values.externalDeadline) errors.externalDeadline = MESSAGES.externalDeadlineRequired
    else if (values.externalDeadline < today) errors.externalDeadline = MESSAGES.contestEnded
    if (values.externalUrl.trim() && !isHttpUrl(values.externalUrl))
      errors.externalUrl = MESSAGES.urlInvalid
  }

  return { ...errors, ...validatePostFields(values, contestDeadline(values)) }
}

// 작성·수정이 함께 쓰는 칸 검사: 제목, ③ 시작일 ≤ 종료일 ≤ 공모전 마감일, ④ URL, 내용
function validatePostFields(values: PostFormValues, deadline: string | undefined) {
  const errors: PostFormErrors = {}

  if (!values.title.trim()) errors.title = MESSAGES.titleRequired

  if (!values.recruitStartDate || !values.recruitEndDate) errors.period = MESSAGES.periodRequired
  else if (values.recruitEndDate < values.recruitStartDate) errors.period = MESSAGES.endBeforeStart
  else if (deadline && values.recruitEndDate > deadline) errors.period = MESSAGES.endAfterDeadline

  if (values.relatedUrl.trim() && !isHttpUrl(values.relatedUrl))
    errors.relatedUrl = MESSAGES.urlInvalid
  if (!values.content.trim()) errors.content = MESSAGES.contentRequired

  return errors
}

// PST-003 ④⑦ 수정 화면 검사. 공모전은 바꿀 수 없어 보지 않고, 목표 인원을 확정 인원과 비교한다.
export function validatePostEditForm(values: PostFormValues, post: PostDetail) {
  const errors = validatePostFields(values, post.contest.deadline)
  if (values.targetCount < post.currentCount) errors.targetCount = MESSAGES.targetBelowCurrent
  // 모집완료 글은 같아도 된다
  else if (post.status === 'RECRUITING' && values.targetCount === post.currentCount)
    errors.targetCount = MESSAGES.targetEqualsCurrent
  return errors
}

// 검사를 통과한 값을 API 요청 모양으로 바꾼다. 비어 있는 선택값은 보내지 않는다.
export function toCreateRequest(values: PostFormValues): PostCreateRequest {
  const relatedUrl = values.relatedUrl.trim()
  const base = {
    title: values.title.trim(),
    targetCount: values.targetCount,
    recruitStartDate: values.recruitStartDate,
    recruitEndDate: values.recruitEndDate,
    relatedUrl: relatedUrl || undefined,
    content: values.content.trim(),
  }
  if (values.contestMode === 'service') {
    return { ...base, contestId: values.contest?.contestId }
  }
  const externalUrl = values.externalUrl.trim()
  return {
    ...base,
    external: {
      title: values.externalTitle.trim(),
      categoryCode: values.externalCategoryCode,
      deadline: values.externalDeadline,
      url: externalUrl || undefined,
    },
  }
}

// PST-003 ① 기존 값을 채운 수정 폼. 공모전 칸은 잠겨 있어 화면에만 쓴다.
export function toEditFormValues(post: PostDetail): PostFormValues {
  return {
    ...EMPTY_POST_FORM,
    title: post.title,
    targetCount: post.targetCount,
    recruitStartDate: post.recruitStartDate,
    recruitEndDate: post.recruitEndDate,
    relatedUrl: post.relatedUrl ?? '',
    content: post.content,
  }
}

// PST-003: 바꾼 칸만 보낸다. 서버는 보내지 않은 칸을 그대로 두고, 기간을 보낼 때만 기간을 다시 검사한다.
// 관련 URL을 비우면 빈 문자열을 보내 없는 값으로 만든다 (⑦).
export function toUpdateRequest(
  values: PostFormValues,
  initial: PostFormValues,
): PostUpdateRequest {
  const request: PostUpdateRequest = {}
  const title = values.title.trim()
  const content = values.content.trim()
  const relatedUrl = values.relatedUrl.trim()
  if (title !== initial.title) request.title = title
  if (values.targetCount !== initial.targetCount) request.targetCount = values.targetCount
  if (
    values.recruitStartDate !== initial.recruitStartDate ||
    values.recruitEndDate !== initial.recruitEndDate
  ) {
    request.recruitStartDate = values.recruitStartDate
    request.recruitEndDate = values.recruitEndDate
  }
  if (relatedUrl !== initial.relatedUrl) request.relatedUrl = relatedUrl
  if (content !== initial.content) request.content = content
  return request
}
