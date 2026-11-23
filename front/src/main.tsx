import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router/dom'
import { AuthBootstrap } from './features/auth/AuthBootstrap'
import { queryClient } from './lib/queryClient'
import { router } from './router'
import './styles/theme.css'
import './styles/vendor-overrides.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      {/* AUTH-12: 켤 때 refresh 쿠키로 로그인 상태를 되살린다 */}
      <AuthBootstrap>
        <RouterProvider router={router} />
      </AuthBootstrap>
    </QueryClientProvider>
  </StrictMode>,
)
