import { useEffect, type ReactNode } from 'react'
import { bootstrapSession } from './session'
import { useAuthStore } from './store'
import { fetchMyProfile } from '../member/api'
import { useQuery } from '@tanstack/react-query'

// 앱을 켤 때 한 번 로그인 상태를 되살린다 (main.tsx에서 감싼다)
let started = false

export function AuthBootstrap({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.accessToken)
  const memberId = useAuthStore((s) => s.member?.memberId)
  const status = useQuery({
    queryKey: ['account-status', memberId],
    queryFn: fetchMyProfile,
    enabled: !!token && !!memberId,
    retry: false,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  })
  useEffect(() => {
    const current = useAuthStore.getState().member
    const profile = status.data
    if (!token || !current || !profile || current.memberId !== profile.memberId) return
    if (
      current.role !== profile.role ||
      current.nickname !== profile.nickname ||
      current.profileImageUrl !== profile.profileImageUrl
    ) {
      useAuthStore.getState().setMember({
        ...current,
        role: profile.role,
        nickname: profile.nickname,
        profileImageUrl: profile.profileImageUrl,
      })
    }
  }, [token, status.data])
  useEffect(() => {
    if (
      token &&
      memberId &&
      status.data?.memberId === memberId &&
      status.data.nicknameChangeRequired &&
      window.location.pathname !== '/mypage/nickname'
    )
      window.location.replace('/mypage/nickname')
  }, [token, memberId, status.data])
  useEffect(() => {
    if (started) return
    started = true
    void bootstrapSession()
  }, [])
  return <>{children}</>
}
