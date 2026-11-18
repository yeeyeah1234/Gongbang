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
import OAuthCallbackPage from './pages/auth/OAuthCallbackPage'
import SocialSignupPage from './pages/auth/SocialSignupPage'
import RestorePage from './pages/auth/RestorePage'
import SignupPage from './pages/auth/SignupPage'
import AccountSettingsPage from './pages/mypage/AccountSettingsPage'
import MyBookmarksPage from './pages/mypage/MyBookmarksPage'
import MyPostsPage from './pages/mypage/MyPostsPage'
import MyProfilePage from './pages/mypage/MyProfilePage'
import PublicProfilePage from './pages/mypage/PublicProfilePage'
import NotificationsPage from './pages/NotificationsPage'
import PostCreatePage from './pages/posts/PostCreatePage'
import PostDetailPage from './pages/posts/PostDetailPage'
import PostListPage from './pages/posts/PostListPage'
import ServiceNoticePage from './pages/ServiceNoticePage'

export const router = createBrowserRouter([
  {
    // GNB가 붙는 화면
    element: <SiteLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/contests/:contestId', element: <ContestDetailPage /> },
      { path: '/search', element: <SearchPage /> },
      // PST-001 커뮤니티 모집글 목록 (비회원도 본다)
      { path: '/posts', element: <PostListPage /> },
      // PST-005 모집글 상세 (비회원도 본다. /posts/new는 아래 로그인 그룹이 먼저 받는다)
      { path: '/posts/:postId', element: <PostDetailPage /> },
      // 정진우 AUTH (로그인 화면 경로 /login, 돌아갈 주소는 ?redirect=)
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      { path: '/find-email', element: <FindEmailPage /> },
      { path: '/find-password', element: <FindPasswordPage /> },
      { path: '/restore', element: <RestorePage /> },
      // AUTH-10 간편 로그인: 백엔드가 돌려보내는 곳, 처음 온 계정의 추가 정보 입력
      { path: '/oauth/callback', element: <OAuthCallbackPage /> },
      { path: '/signup/social', element: <SocialSignupPage /> },
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
          // PST-002 모집글 작성 (비회원은 목록의 「글쓰기」에서 로그인 모달을 먼저 본다)
          { path: '/posts/new', element: <PostCreatePage /> },
          { path: '/service-notices/:serviceNoticeId', element: <ServiceNoticePage /> },
        ],
      },
    ],
  },
  // 워크스페이스는 사이트 GNB 없이 자기 헤더를 쓴다 (WS-02 ②)
  { path: '/teams/:teamId/chat', element: <WorkspaceChatPage /> },
])
