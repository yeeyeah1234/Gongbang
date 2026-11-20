import { Navigate, Outlet } from 'react-router'
import { useAuthStore } from './store'

export function RequireGuest() {
  const initialized = useAuthStore((state) => state.initialized)
  const loggedIn = useAuthStore((state) => state.accessToken !== null)
  if (!initialized) return null
  return loggedIn ? <Navigate to="/" replace /> : <Outlet />
}
