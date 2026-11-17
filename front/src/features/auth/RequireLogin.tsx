import { Navigate, Outlet, useLocation } from 'react-router'
import { loginPathWithRedirect } from './redirect'
import { useAuthStore } from './store'

// AUTH-12 ②③: 마이페이지·알림처럼 로그인이 필요한 화면. 비로그인이면 지금 주소를 기억하고 로그인 화면으로
export function RequireLogin() {
  const location = useLocation()
  const initialized = useAuthStore((s) => s.initialized)
  const loggedIn = useAuthStore((s) => s.accessToken !== null)

  if (!initialized) return null
  if (!loggedIn) {
    return (
      <Navigate
        to={loginPathWithRedirect(location.pathname + location.search)}
        replace
        state={{ loginRequired: true }}
      />
    )
  }
  return <Outlet />
}
