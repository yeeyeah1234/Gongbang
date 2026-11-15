import { createBrowserRouter, Navigate } from 'react-router'
import { SiteLayout } from './components/layout/SiteLayout'
import ContestDetailPage from './pages/ContestDetailPage'
import HomePage from './pages/HomePage'
import SearchPage from './pages/SearchPage'
import WorkspaceChatPage from './pages/WorkspaceChatPage'
import { RequireLogin } from './features/auth/RequireLogin'
import FindEmailPage from './pages/auth/FindEmailPage'
import FindPasswordPage from './pages/auth/FindPasswordPage'
import LoginPage from './pages/auth/LoginPage'
import RestorePage from './pages/auth/RestorePage'
import SignupPage from './pages/auth/SignupPage'
import AccountSettingsPage from './pages/mypage/AccountSettingsPage'
import MyBookmarksPage from './pages/mypage/MyBookmarksPage'
import MyPostsPage from './pages/mypage/MyPostsPage'
import MyProfilePage from './pages/mypage/MyProfilePage'
import PublicProfilePage from './pages/mypage/PublicProfilePage'
import NotificationsPage from './pages/NotificationsPage'
import ServiceNoticePage from './pages/ServiceNoticePage'

export const router = createBrowserRouter([
  {
    // GNB가 붙는 화면
    element: <SiteLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/contests/:contestId', element: <ContestDetailPage /> },
      { path: '/search', element: <SearchPage /> },
      // 정진우 AUTH (로그인 화면 경로 /login, 돌아갈 주소는 ?redirect=)
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      { path: '/find-email', element: <FindEmailPage /> },
      { path: '/find-password', element: <FindPasswordPage /> },
      { path: '/restore', element: <RestorePage /> },
      // MY-05 공개 프로필
      { path: '/members/:memberId', element: <PublicProfilePage /> },
      {
        // AUTH-12 ②: 로그인이 필요한 화면 (마이페이지, 알림, 운영 공지)
        element: <RequireLogin />,
        children: [
          { path: '/mypage', element: <Navigate to="/mypage/profile" replace /> },
          { path: '/mypage/profile', element: <MyProfilePage /> },
          { path: '/mypage/posts', element: <MyPostsPage /> },
          { path: '/mypage/bookmarks', element: <MyBookmarksPage /> },
          { path: '/mypage/settings', element: <AccountSettingsPage /> },
          { path: '/notifications', element: <NotificationsPage /> },
          { path: '/service-notices/:serviceNoticeId', element: <ServiceNoticePage /> },
        ],
      },
    ],
  },
  // 워크스페이스는 사이트 GNB 없이 자기 헤더를 쓴다 (WS-02 ②)
  { path: '/teams/:teamId/chat', element: <WorkspaceChatPage /> },
])
