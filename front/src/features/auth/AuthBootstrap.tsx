import { useEffect, type ReactNode } from 'react'
import { bootstrapSession } from './session'

// 앱을 켤 때 한 번 로그인 상태를 되살린다 (main.tsx에서 감싼다)
let started = false

export function AuthBootstrap({ children }: { children: ReactNode }) {
  useEffect(() => {
    if (started) return
    started = true
    void bootstrapSession()
  }, [])
  return <>{children}</>
}
