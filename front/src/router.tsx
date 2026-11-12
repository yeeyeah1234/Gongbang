import { createBrowserRouter } from 'react-router'
import ContestDetailPage from './pages/ContestDetailPage'
import HomePage from './pages/HomePage'
import WorkspaceChatPage from './pages/WorkspaceChatPage'

export const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  { path: '/contests/:contestId', element: <ContestDetailPage /> },
  { path: '/teams/:teamId/chat', element: <WorkspaceChatPage /> },
])
