import type { PinnedNotice } from '../types'

// GET /api/teams/{teamId}/notices 항목 (NoticeListItemResponse)
export interface NoticeListItemData {
  noticeId: number
  title: string
  // 내용 첫 줄, 최대 100자. 말줄임표는 화면이 붙인다
  preview: string
  author: PinnedNotice['author']
  createdAt: string
  isPinned: boolean
  isEdited: boolean
  // 내가 쓴 공지는 항상 false
  isUnread: boolean
}

// GET /api/notices/{noticeId}. 고정 배너와 같은 DTO(NoticeDetailResponse)
export type NoticeDetailData = PinnedNotice

// NTC-03 PATCH /api/notices/{noticeId}. 고정은 바꿀 수 없다
export interface NoticeUpdateRequest {
  title: string
  content: string
}

// NTC-02 POST /api/teams/{teamId}/notices
export interface NoticeCreateRequest extends NoticeUpdateRequest {
  pinned: boolean
}
