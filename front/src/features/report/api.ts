import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { CreateReportRequest, CreateReportResponse } from './types'

// NET-02 신고 접수 (POST /api/reports)
export async function createReport(payload: CreateReportRequest): Promise<CreateReportResponse> {
  return (await apiClient.post<ApiResponse<CreateReportResponse>>('/api/reports', payload)).data
    .data
}
