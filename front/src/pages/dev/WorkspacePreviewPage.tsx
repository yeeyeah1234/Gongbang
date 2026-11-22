import { useState } from 'react'
import { useParams } from 'react-router'
import { usePinnedCollapsed } from '../../features/workspace/pinnedCollapse'
import { PinnedNoticeBanner } from '../../features/workspace/components/PinnedNoticeBanner'
import { WorkspaceHeader } from '../../features/workspace/components/WorkspaceHeader'
import { WorkspaceNoAccess } from '../../features/workspace/components/WorkspaceNoAccess'
import { WorkspacePlaceholder } from '../../features/workspace/components/WorkspacePlaceholder'
import { WorkspaceTabs } from '../../features/workspace/components/WorkspaceTabs'
import {
  WORKSPACE_TABS,
  type PinnedNotice,
  type WorkspaceOnlineMember,
  type WorkspaceTabKey,
} from '../../features/workspace/types'

// 개발 전용 (npm run dev에서만 router.tsx가 lazy로 불러온다. 배포 빌드에는 들어가지 않음)
// 백엔드·로그인 없이 WS-02 헤더·탭·고정 공지 배너·권한 없음 화면을 고정 데이터로 본다.

const BASE_PATH = '/dev/workspace-preview'
const NAMES = ['김하늘', '이바다', '박산들', '최노을', '정새벽', '한여름', '윤가을']

const MEMBERS: WorkspaceOnlineMember[] = NAMES.map((nickname, i) => ({
  memberId: i + 1,
  nickname,
  colorIndex: (i + 1) as WorkspaceOnlineMember['colorIndex'],
  isLeader: i === 0,
}))

// 헤더 맨 앞의 "나" (실제 화면은 로그인 상태 + 팀 요약에서 만든다). 팀장인 나 / 팀원인 나
const ME_LEADER: WorkspaceOnlineMember = MEMBERS[0]
const ME_MEMBER: WorkspaceOnlineMember = MEMBERS[1]

const NOTICE: PinnedNotice = {
  noticeId: 1,
  teamId: 0,
  title: '12/1(월) 오후 9시까지 기획서 초안을 문서 탭에 올려 주세요',
  content: '',
  author: { memberId: 1, nickname: '김하늘' },
  createdAt: '2026-11-20T10:00:00',
  editedAt: null,
  isPinned: true,
  isEdited: false,
}

// 오늘 기준 n일 뒤 'YYYY-MM-DD'
function daysFromToday(n: number) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  const pad = (v: number) => String(v).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function Label({ children }: { children: string }) {
  return <p className="bg-surface-sub px-6 py-2 text-caption font-bold text-text-sub">{children}</p>
}

export default function WorkspacePreviewPage() {
  const tabParam = useParams().tab
  const tab: WorkspaceTabKey = WORKSPACE_TABS.some((t) => t.key === tabParam)
    ? (tabParam as WorkspaceTabKey)
    : 'chat'
  const noop = () => {}
  // 첫 예시는 실제와 같게 localStorage에 저장한다 (teamId 0). 접힘 예시는 화면 안에서만 기억하고 접힌 채로 시작한다
  const [pinnedCollapsed, setPinnedCollapsed] = usePinnedCollapsed(0, NOTICE.noticeId)
  const [sampleCollapsed, setSampleCollapsed] = useState(true)

  return (
    <div className="flex min-h-screen flex-col gap-6 bg-bg pb-12">
      <section>
        <Label>
          기본: D-10 (보통), 나(팀장 김하늘) 맨 앞 + 목록 7명(나 포함, 중복 제거) → 접속 7명, 아바타
          5개 + 「+2」, 내 툴팁 「(나) [팀장] 김하늘」, 채팅 3·공지 12 배지
        </Label>
        <WorkspaceHeader
          teamName="공방팀"
          contestTitle="2026 공공데이터 활용 아이디어 공모전"
          deadline={daysFromToday(10)}
          self={ME_LEADER}
          onlineMembers={MEMBERS}
          onBack={noop}
        />
        <WorkspaceTabs
          basePath={BASE_PATH}
          chatUnreadCount={3}
          noticeUnreadCount={12}
          onExpandPinned={pinnedCollapsed ? () => setPinnedCollapsed(false) : undefined}
        />
        <PinnedNoticeBanner
          notice={NOTICE}
          collapsed={pinnedCollapsed}
          onCollapse={() => setPinnedCollapsed(true)}
        />
        <div className="flex min-h-[200px] flex-col px-6 py-6">
          <WorkspacePlaceholder tab={tab} />
        </div>
      </section>

      <section>
        <Label>
          고정 공지 접힘: 배너 줄 없음(높이 0), 탭 바 오른쪽 끝 「고정 공지 펼치기」 버튼 → 누르면
          펼침. 접속 3명(나 포함)
        </Label>
        <WorkspaceHeader
          teamName="공방팀"
          contestTitle="2026 공공데이터 활용 아이디어 공모전"
          deadline={daysFromToday(10)}
          self={ME_LEADER}
          onlineMembers={MEMBERS.slice(0, 3)}
          onBack={noop}
        />
        <WorkspaceTabs
          basePath={BASE_PATH}
          chatUnreadCount={0}
          noticeUnreadCount={1}
          onExpandPinned={sampleCollapsed ? () => setSampleCollapsed(false) : undefined}
        />
        <PinnedNoticeBanner
          notice={NOTICE}
          collapsed={sampleCollapsed}
          onCollapse={() => setSampleCollapsed(true)}
        />
        <div className="flex min-h-[120px] flex-col px-6 py-6">
          <WorkspacePlaceholder tab={tab} />
        </div>
      </section>

      <section>
        <Label>
          D-3 (danger 강조), 팀원인 나(이바다, 왕관 없음, 툴팁 「(나) 이바다」) 맨 앞 + 팀장·팀원
          1명 → 접속 3명, 배지 0이면 숨김, 고정 공지 없음 → 배너 숨김
        </Label>
        <WorkspaceHeader
          teamName="디자인팀"
          contestTitle="UX 디자인 공모전"
          deadline={daysFromToday(3)}
          self={ME_MEMBER}
          onlineMembers={[MEMBERS[0], MEMBERS[2]]}
          onBack={noop}
        />
        <WorkspaceTabs basePath={BASE_PATH} chatUnreadCount={0} noticeUnreadCount={0} />
        <PinnedNoticeBanner notice={null} collapsed={false} onCollapse={noop} />
      </section>

      <section>
        <Label>
          마감 당일 D-0 / 공모전명 없음 → 팀명만, 접속자 목록 없음 → 나만 「접속 중 1명」 (지금 실제
          화면)
        </Label>
        <WorkspaceHeader
          teamName="모집글 삭제된 팀"
          contestTitle={null}
          deadline={daysFromToday(0)}
          self={ME_LEADER}
          onlineMembers={[]}
          onBack={noop}
        />
      </section>

      <section>
        <Label>마감 지남 → 「종료」, 목록에 나만 있음 → 중복 제거로 접속 1명</Label>
        <WorkspaceHeader
          teamName="끝난팀"
          contestTitle="지난 공모전"
          deadline={daysFromToday(-1)}
          self={ME_LEADER}
          onlineMembers={MEMBERS.slice(0, 1)}
          onBack={noop}
        />
      </section>

      <section>
        <Label>403 권한 없음 (WS-01 ②)</Label>
        <WorkspaceNoAccess onGoList={noop} />
      </section>
    </div>
  )
}
