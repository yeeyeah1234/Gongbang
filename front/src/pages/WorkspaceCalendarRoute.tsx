import { lazy, Suspense } from 'react'
import { NoticeSkeleton } from '../features/workspace/notice/components/NoticeStates'

// FullCalendar가 커서 캘린더 탭을 처음 열 때만 내려받는다 (router.tsx는 컴포넌트만 내보내는 파일이 아니라 여기 둔다)
const WorkspaceCalendarPage = lazy(() => import('./WorkspaceCalendarPage'))

export default function WorkspaceCalendarRoute() {
  return (
    // 받는 동안은 공지 탭과 같은 로딩 표시 (1초 넘으면 스켈레톤)
    <Suspense fallback={<NoticeSkeleton />}>
      <WorkspaceCalendarPage />
    </Suspense>
  )
}
