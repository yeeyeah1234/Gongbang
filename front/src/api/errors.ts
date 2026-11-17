import axios from 'axios'
import type { ApiResponse } from './types'

// 서버 공통 응답의 error.code / error.message를 꺼낸다 (AGENTS 5: 문구는 서버가 기능정의서 그대로 보낸다)
export function getApiError(error: unknown): { code: string | null; message: string | null } {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    const body = error.response?.data
    if (body && body.error) return { code: body.error.code, message: body.error.message }
  }
  return { code: null, message: null }
}

export function getErrorMessage(error: unknown, fallback = '잠시 후 다시 시도해 주세요') {
  return getApiError(error).message ?? fallback
}
