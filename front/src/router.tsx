import { createBrowserRouter } from 'react-router'
import HomePage from './pages/HomePage'
import WorkspaceChatPage from './pages/WorkspaceChatPage'

export const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  { path: '/teams/:teamId/chat', element: <WorkspaceChatPage /> },
])
