import { apiClient } from '../../../api/client'
import type { ApiResponse, PageResponse } from '../../../api/types'
import type { NoticeDetailData, NoticeListItemData } from './types'

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
