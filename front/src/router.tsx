import { createBrowserRouter, Navigate, type RouteObject } from 'react-router'
import { SiteLayout } from './components/layout/SiteLayout'
import ContestDetailPage from './pages/ContestDetailPage'
import HomePage from './pages/HomePage'
import SearchPage from './pages/SearchPage'
import WorkspaceCalendarRoute from './pages/WorkspaceCalendarRoute'
import WorkspaceChatPage from './pages/WorkspaceChatPage'
import WorkspaceDocumentPage from './pages/WorkspaceDocumentPage'
import WorkspaceListPage from './pages/WorkspaceListPage'
import WorkspaceNoticePage from './pages/WorkspaceNoticePage'
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
import BoardCreatePage from './pages/board/BoardCreatePage'
import BoardDetailPage from './pages/board/BoardDetailPage'
import BoardEditPage from './pages/board/BoardEditPage'
import BoardListPage from './pages/board/BoardListPage'
import ServiceNoticePage from './pages/ServiceNoticePage'
import { WorkspaceLayout } from './features/workspace/components/WorkspaceLayout'
import { WorkspacePlaceholder } from './features/workspace/components/WorkspacePlaceholder'
import AdminVerificationPage from './pages/admin/AdminVerificationPage'
import { AdminLayout } from './features/admin/AdminLayout'
import AdminMembersPage from './pages/admin/AdminMembersPage'
import AdminNoticeManagementPage from './pages/admin/AdminNoticeManagementPage'
import AdminReportManagementPage from './pages/admin/AdminReportManagementPage'
import AdminMemberManagementPage from './pages/admin/AdminMemberManagementPage'
import AdminPostManagementPage from './pages/admin/AdminPostManagementPage'
import AdminContestManagementPage from './pages/admin/AdminContestManagementPage'

// 개발 서버에서만 쓰는 화면. 배포 빌드에서는 import.meta.env.DEV가 false라 import 자체가 빠진다
const devRoutes: RouteObject[] = import.meta.env.DEV
  ? [
      {
        path: '/dev/admin-preview/:tab?',
        lazy: async () => ({ Component: (await import('./pages/dev/AdminPreviewPage')).default }),
      },
      {
        // WS-02 헤더·탭·배너를 백엔드·로그인 없이 본다
        path: '/dev/workspace-preview/:tab?',
        lazy: async () => ({
          Component: (await import('./pages/dev/WorkspacePreviewPage')).default,
        }),
      },
      {
        path: '/dev/notice-preview',
        element: <Navigate to="/dev/notice-preview/leader" replace />,
      },
      {
        // NTC-01~04 공지 조회·쓰기를 백엔드·로그인 없이 본다: {leader|member|empty|single|conflict|forbidden|error}
        path: '/dev/notice-preview/:view',
        lazy: async () => ({
          Component: (await import('./pages/dev/NoticePreviewPage')).default,
        }),
        children: [
          {
            path: 'notice/:noticeId?',
            lazy: async () => ({
              Component: (await import('./pages/dev/NoticePreviewPage')).NoticePreviewTab,
            }),
          },
          {
            // 첫 진입은 공지 탭으로 ('*'는 뒤 조각이 없으면 안 잡힌다)
            index: true,
            lazy: async () => ({
              Component: (await import('./pages/dev/NoticePreviewPage')).NoticePreviewRedirect,
            }),
          },
          {
            // 다른 탭도 공지 탭으로
            path: '*',
            lazy: async () => ({
              Component: (await import('./pages/dev/NoticePreviewPage')).NoticePreviewRedirect,
            }),
          },
        ],
      },
      {
        path: '/dev/calendar-preview',
        element: <Navigate to="/dev/calendar-preview/leader" replace />,
      },
      {
        // CAL-01·03·04·05·08 캘린더를 백엔드·로그인 없이 본다: {leader|member|empty|error}
        path: '/dev/calendar-preview/:view',
        lazy: async () => ({
          Component: (await import('./pages/dev/CalendarPreviewPage')).default,
        }),
        children: [
          {
            path: 'calendar',
            lazy: async () => ({
              Component: (await import('./pages/WorkspaceCalendarPage')).default,
            }),
          },
          {
            // 첫 진입과 다른 탭은 캘린더 탭으로
            index: true,
            lazy: async () => ({
              Component: (await import('./pages/dev/CalendarPreviewPage')).CalendarPreviewRedirect,
            }),
          },
          {
            path: '*',
            lazy: async () => ({
              Component: (await import('./pages/dev/CalendarPreviewPage')).CalendarPreviewRedirect,
            }),
          },
        ],
      },
      {
        path: '/dev/document-preview',
        element: <Navigate to="/dev/document-preview/normal" replace />,
      },
      {
        // DOC-01~04 문서 사이드바를 백엔드·로그인 없이 본다: {normal|empty|error|member}
        path: '/dev/document-preview/:view',
        lazy: async () => ({
          Component: (await import('./pages/dev/DocumentPreviewPage')).default,
        }),
        children: [
          {
            path: 'docs/:documentId?',
            lazy: async () => ({
              Component: (await import('./pages/dev/DocumentPreviewPage')).DocumentPreviewTab,
            }),
          },
          {
            // 첫 진입과 다른 탭은 문서 탭으로
            index: true,
            lazy: async () => ({
              Component: (await import('./pages/dev/DocumentPreviewPage')).DocumentPreviewRedirect,
            }),
          },
          {
            path: '*',
            lazy: async () => ({
              Component: (await import('./pages/dev/DocumentPreviewPage')).DocumentPreviewRedirect,
            }),
          },
        ],
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
      // BRD-001·004 커뮤니티 게시판 목록·상세 (비회원도 본다. /board/new는 아래 로그인 그룹이 먼저 받는다)
      { path: '/board', element: <BoardListPage /> },
      { path: '/board/:boardPostId', element: <BoardDetailPage /> },
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
          // BRD-002·003 게시글 작성·수정
          { path: '/board/new', element: <BoardCreatePage /> },
          { path: '/board/:boardPostId/edit', element: <BoardEditPage /> },
          { path: '/service-notices/:serviceNoticeId', element: <ServiceNoticePage /> },
          // WS-08 워크스페이스 팀 목록 (GNB 「워크스페이스」). 팀을 고르면 아래 /workspaces/:teamId로
          { path: '/workspaces', element: <WorkspaceListPage /> },
        ],
      },
    ],
  },
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
          { path: 'chat', element: <WorkspaceChatPage /> },
          // DOC-01~04 문서 사이드바: documentId가 없으면 마지막으로 연 문서 또는 첫 문서로 옮긴다
          { path: 'docs/:documentId?', element: <WorkspaceDocumentPage /> },
          { path: 'board', element: <WorkspacePlaceholder tab="board" /> },
          // CAL-01·03·04·05·08 월간 캘린더 (WorkspaceCalendarRoute가 lazy로 불러온다)
          { path: 'calendar', element: <WorkspaceCalendarRoute /> },
          // NTC-01: noticeId가 없으면 목록의 첫 공지로 옮긴다
          { path: 'notice/:noticeId?', element: <WorkspaceNoticePage /> },
        ],
      },
    ],
  },
  {
    element: <RequireLogin />,
    children: [
      {
        path: '/admin',
        element: <AdminLayout />,
        children: [
          { index: true, element: <Navigate to="verifications" replace /> },
          { path: 'verifications', element: <AdminVerificationPage /> },
          { path: 'notices', element: <AdminNoticeManagementPage /> },
          { path: 'reports', element: <AdminReportManagementPage /> },
          { path: 'members', element: <AdminMemberManagementPage /> },
          { path: 'posts', element: <AdminPostManagementPage /> },
          { path: 'contests', element: <AdminContestManagementPage /> },
          { path: 'permissions', element: <AdminMembersPage /> },
        ],
      },
    ],
  },
  ...devRoutes,
])
