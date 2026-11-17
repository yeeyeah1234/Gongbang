import { create } from 'zustand'
import type { LoginMemberInfo } from './types'

// 로그인 사용자 전역 상태 (AGENTS 2-1: Zustand는 로그인 사용자 등 꼭 필요한 것만)
// access 토큰은 메모리에만 둔다. 새로 고침하면 refresh 쿠키로 다시 받는다 (AuthBootstrap).
interface AuthState {
  accessToken: string | null
  member: LoginMemberInfo | null
  // 처음 켤 때 refresh 확인을 끝냈는지 (끝나기 전에는 로그인 필요 화면이 로그인으로 튕기지 않게)
  initialized: boolean
  setLogin: (accessToken: string, member?: LoginMemberInfo | null) => void
  setMember: (member: LoginMemberInfo) => void
  clear: () => void
  setInitialized: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  member: null,
  initialized: false,
  setLogin: (accessToken, member) =>
    set((state) => ({ accessToken, member: member === undefined ? state.member : member })),
  setMember: (member) => set({ member }),
  clear: () => set({ accessToken: null, member: null }),
  setInitialized: () => set({ initialized: true }),
}))

// AUTH-12 로그인 확인. 다른 담당 화면도 이걸 쓴다
export function useIsLoggedIn() {
  return useAuthStore((state) => state.accessToken !== null)
}

export function useLoginMember() {
  return useAuthStore((state) => state.member)
}
