import { createBrowserRouter, Navigate, type RouteObject } from 'react-router'
import { SiteLayout } from './components/layout/SiteLayout'
import ContestDetailPage from './pages/ContestDetailPage'
import HomePage from './pages/HomePage'
import SearchPage from './pages/SearchPage'
import WorkspaceChatPage from './pages/WorkspaceChatPage'
import { RequireLogin } from './features/auth/RequireLogin'
import { RequireGuest } from './features/auth/RequireGuest'
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
import PostEditPage from './pages/posts/PostEditPage'
import PostListPage from './pages/posts/PostListPage'
import ServiceNoticePage from './pages/ServiceNoticePage'
import { WorkspaceLayout } from './features/workspace/components/WorkspaceLayout'
import { WorkspacePlaceholder } from './features/workspace/components/WorkspacePlaceholder'

// 개발 서버에서만 쓰는 화면. 배포 빌드에서는 import.meta.env.DEV가 false라 import 자체가 빠진다
const devRoutes: RouteObject[] = import.meta.env.DEV
  ? [
      {
        // WS-02 헤더·탭·배너를 백엔드·로그인 없이 본다
        path: '/dev/workspace-preview/:tab?',
        lazy: async () => ({
          Component: (await import('./pages/dev/WorkspacePreviewPage')).default,
        }),
      },
    ]
  : []

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
      {
        element: <RequireGuest />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/signup', element: <SignupPage /> },
          { path: '/signup/social', element: <SocialSignupPage /> },
        ],
      },
      { path: '/find-email', element: <FindEmailPage /> },
      { path: '/find-password', element: <FindPasswordPage /> },
      { path: '/restore', element: <RestorePage /> },
      // AUTH-10 간편 로그인: 백엔드가 돌려보내는 곳, 처음 온 계정의 추가 정보 입력
      { path: '/oauth/callback', element: <OAuthCallbackPage /> },
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
          // PST-003 모집글 수정 (작성자가 아니면 화면이 막고, 서버도 403)
          { path: '/posts/:postId/edit', element: <PostEditPage /> },
          { path: '/service-notices/:serviceNoticeId', element: <ServiceNoticePage /> },
        ],
      },
    ],
  },
  // 워크스페이스는 사이트 GNB 없이 자기 헤더를 쓴다 (WS-02 ②)
  { path: '/teams/:teamId/chat', element: <WorkspaceChatPage /> },
  {
    // WS-02 워크스페이스 공통 틀 (GNB 없음). 탭은 URL로 정한다: /workspaces/:teamId/{chat|docs|board|calendar|notice}
    // 각 탭 담당자는 아래 element를 자기 화면으로 바꾼다 (WorkspaceLayout.tsx 맨 위 주석 참고)
    element: <RequireLogin />,
    children: [
      {
        path: '/workspaces/:teamId',
        element: <WorkspaceLayout />,
        children: [
          // 첫 입장은 채팅 탭
          { index: true, element: <Navigate to="chat" replace /> },
          // TODO(확인 필요): 채팅(김건우)은 지금 /teams/:teamId/chat에 따로 있다. 합칠 때 여기로 옮긴다
          { path: 'chat', element: <WorkspacePlaceholder tab="chat" /> },
          { path: 'docs', element: <WorkspacePlaceholder tab="docs" /> },
          { path: 'board', element: <WorkspacePlaceholder tab="board" /> },
          { path: 'calendar', element: <WorkspacePlaceholder tab="calendar" /> },
          { path: 'notice', element: <WorkspacePlaceholder tab="notice" /> },
        ],
      },
    ],
  },
  ...devRoutes,
])
