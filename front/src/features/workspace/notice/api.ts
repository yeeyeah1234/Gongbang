import { apiClient } from '../../../api/client'
import type { ApiResponse, PageResponse } from '../../../api/types'
import type {
  NoticeCreateRequest,
  NoticeDetailData,
  NoticeListItemData,
  NoticeUpdateRequest,
} from './types'

const PAGE_SIZE = 10

// NTC-01 목록: 고정 맨 위 → 최신순
export async function fetchNotices(teamId: number, page: number) {
  return (
    await apiClient.get<ApiResponse<PageResponse<NoticeListItemData>>>(
      `/api/teams/${teamId}/notices`,
      { params: { page, size: PAGE_SIZE } },
    )
  ).data.data
}

// NTC-01 ⑥ 상세. 이 호출이 읽음 기록을 남기므로 미리 불러오기(prefetch)에 쓰지 않는다
export async function fetchNotice(noticeId: number) {
  return (await apiClient.get<ApiResponse<NoticeDetailData>>(`/api/notices/${noticeId}`)).data.data
}

// NTC-02 작성. 새 공지 ID를 돌려준다
export async function createNotice(teamId: number, body: NoticeCreateRequest) {
  return (
    await apiClient.post<ApiResponse<{ noticeId: number }>>(`/api/teams/${teamId}/notices`, body)
  ).data.data.noticeId
}

// NTC-03 수정
export async function updateNotice(noticeId: number, body: NoticeUpdateRequest) {
  await apiClient.patch(`/api/notices/${noticeId}`, body)
}

// NTC-03 삭제
export async function deleteNotice(noticeId: number) {
  await apiClient.delete(`/api/notices/${noticeId}`)
}

// NTC-04 고정. 다른 고정 공지는 서버가 푼다
export async function pinNotice(noticeId: number) {
  await apiClient.put(`/api/notices/${noticeId}/pin`)
}

// NTC-04 고정 해제
export async function unpinNotice(noticeId: number) {
  await apiClient.delete(`/api/notices/${noticeId}/pin`)
}
