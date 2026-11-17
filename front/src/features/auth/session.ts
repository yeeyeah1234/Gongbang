import { apiClient, refreshAccessToken } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import { queryClient } from '../../lib/queryClient'
import { logout as logoutApi } from './api'
import { useAuthStore } from './store'
import type { LoginMemberInfo } from './types'

// 로그인 직후·새로 고침 때 GNB에 쓸 내 정보 (API 명세서 MY-01: GNB도 /api/members/me 사용)
export async function loadLoginMember() {
  const res = await apiClient.get<ApiResponse<LoginMemberInfo>>('/api/members/me')
  const { memberId, nickname, profileImageUrl, role } = res.data.data
  useAuthStore.getState().setMember({ memberId, nickname, profileImageUrl, role })
}

// 앱을 켤 때 refresh 쿠키가 살아 있으면 로그인 상태를 되살린다
export async function bootstrapSession() {
  try {
    const token = await refreshAccessToken()
    if (token) await loadLoginMember()
  } catch {
    useAuthStore.getState().clear()
  } finally {
    useAuthStore.getState().setInitialized()
  }
}

// AUTH-11 ②③: 토큰을 지우고 비로그인 메인으로. 채팅·워크스페이스 실시간 연결은 각 화면이 store 변화를 보고 끊는다
export async function signOut() {
  try {
    await logoutApi()
  } finally {
    useAuthStore.getState().clear()
    queryClient.clear()
  }
}
