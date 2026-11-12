import { createBrowserRouter } from 'react-router'
import { SiteLayout } from './components/layout/SiteLayout'
import ContestDetailPage from './pages/ContestDetailPage'
import HomePage from './pages/HomePage'
import WorkspaceChatPage from './pages/WorkspaceChatPage'

export const router = createBrowserRouter([
  {
    // GNB가 붙는 화면
    element: <SiteLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/contests/:contestId', element: <ContestDetailPage /> },
    ],
  },
  // 워크스페이스는 사이트 GNB 없이 자기 헤더를 쓴다 (WS-02 ②)
  { path: '/teams/:teamId/chat', element: <WorkspaceChatPage /> },
])
