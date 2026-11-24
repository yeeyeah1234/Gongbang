import { CONTENT_MAX, TITLE_MAX, URL_MAX, isHttpUrl } from '../post/postForm'
import type { SelectedContest } from '../post/postForm'
import type { BoardCategory, BoardDetail, BoardPostRequest } from './types'

// BRD-002 ① 글자 수는 모집글과 같다
export { CONTENT_MAX, TITLE_MAX, URL_MAX }

// BRD-002 ④ 문구
export const BOARD_MESSAGES = {
  categoryRequired: '말머리를 선택해 주세요',
  contestRequired: '공모전을 선택해 주세요',
  titleRequired: '제목을 입력해 주세요',
  contentRequired: '내용을 입력해 주세요',
  invalidUrl: 'http:// 또는 https://로 시작하는 주소를 입력해 주세요',
  // BRD-003 ③ 결과물 자랑에서 다른 말머리로 바꿀 때
  showcaseCleared: '결과물 링크와 사진이 지워져요',
}

export interface BoardFormValues {
  category: BoardCategory | null
  contest: SelectedContest | null
  title: string
  content: string
  linkUrl: string
}

export type BoardFormErrors = Partial<
  Record<'category' | 'contest' | 'title' | 'content' | 'linkUrl', string>
>

export const EMPTY_BOARD_FORM: BoardFormValues = {
  category: null,
  contest: null,
  title: '',
  content: '',
  linkUrl: '',
}

export function validateBoardForm(values: BoardFormValues): BoardFormErrors {
  const errors: BoardFormErrors = {}
  if (!values.category) errors.category = BOARD_MESSAGES.categoryRequired
  // ① 공모전 이야기만 공모전 필수
  if (values.category === 'CONTEST' && !values.contest) {
    errors.contest = BOARD_MESSAGES.contestRequired
  }
  if (!values.title.trim()) errors.title = BOARD_MESSAGES.titleRequired
  if (!values.content.trim()) errors.content = BOARD_MESSAGES.contentRequired
  const link = values.linkUrl.trim()
  if (values.category === 'SHOWCASE' && link && !isHttpUrl(link)) {
    errors.linkUrl = BOARD_MESSAGES.invalidUrl
  }
  return errors
}

export function toBoardRequest(values: BoardFormValues): BoardPostRequest {
  const link = values.linkUrl.trim()
  return {
    category: values.category as BoardCategory,
    contestId: values.contest?.contestId ?? null,
    title: values.title.trim(),
    content: values.content,
    // ② 결과물 링크는 결과물 자랑만. 다른 말머리면 보내지 않는다 (서버도 지운다, BRD-003 ③)
    linkUrl: values.category === 'SHOWCASE' && link ? link : null,
  }
}

export function toBoardFormValues(post: BoardDetail): BoardFormValues {
  return {
    category: post.category,
    contest: post.contest
      ? {
          contestId: post.contest.contestId,
          title: post.contest.title,
          categoryName: post.contest.categoryName,
          deadline: post.contest.deadline,
        }
      : null,
    title: post.title,
    content: post.content,
    linkUrl: post.linkUrl ?? '',
  }
}

// 서버가 막은 이유를 해당 칸 아래에 보여 준다 (BRD-002 ④)
export function boardServerErrorToField(
  code: string | null,
  message: string | null,
): BoardFormErrors | null {
  switch (code) {
    case 'CONTEST_REQUIRED':
      return { contest: BOARD_MESSAGES.contestRequired }
    case 'CONTEST_NOT_FOUND':
      return { contest: message ?? BOARD_MESSAGES.contestRequired }
    default:
      return null
  }
}
