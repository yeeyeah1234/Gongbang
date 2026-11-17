import type { NotificationCategory } from './types'

// 아이콘 자리의 짧은 글자 (Figma 12-1·14: 댓글 / 마감 / 결과 / 공지)
export const CATEGORY_LABEL: Record<NotificationCategory, string> = {
  COMMENT: '댓글',
  TEAM_BUILDING: '팀',
  WORKSPACE: '팀방',
  DEADLINE: '마감',
  NOTICE: '공지',
}

// NOTI-01 ② 종류 필터 이름
export const CATEGORY_FILTER: { value: NotificationCategory | undefined; label: string }[] = [
  { value: undefined, label: '전체' },
  { value: 'COMMENT', label: '댓글' },
  { value: 'TEAM_BUILDING', label: '팀 빌딩' },
  { value: 'WORKSPACE', label: '워크스페이스' },
  { value: 'DEADLINE', label: '마감 임박' },
  { value: 'NOTICE', label: '공지' },
]

// NOTI-02 ① 몇 분 전
export function timeAgo(value: string, now = new Date()) {
  const date = new Date(value)
  const minutes = Math.floor((now.getTime() - date.getTime()) / 60000)
  if (minutes < 1) return '방금 전'
  if (minutes < 60) return `${minutes}분 전`
  const hours = Math.floor(minutes / 60)
  if (hours < 24 && date.getDate() === now.getDate()) return `${hours}시간 전`
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  const hm = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  if (date.toDateString() === yesterday.toDateString()) return `어제 ${hm}`
  return `${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

// NOTI-01 ③ 날짜별 묶음: 오늘 / 어제 / 이번 주 / 이전
export function dateGroup(value: string, now = new Date()) {
  const date = new Date(value)
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const diffDays = Math.floor(
    (startOfToday.getTime() -
      new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()) /
      86400000,
  )
  if (diffDays <= 0) return '오늘'
  if (diffDays === 1) return '어제'
  if (diffDays < 7) return '이번 주'
  return '이전'
}
