import { useEffect } from 'react'
import { Link } from 'react-router'
import { Button } from '../components/ui/Button'
import { Toast } from '../components/ui/Toast'
import { TeamCard } from '../features/workspace/components/TeamCard'
import { useMyTeams } from '../features/workspace/hooks'
import type { MyTeam } from '../features/workspace/types'
import { useTimedToast } from '../hooks/useTimedToast'

const GRID = 'grid grid-cols-[repeat(auto-fill,minmax(min(100%,20rem),1fr))] gap-4'

function TeamSection({
  title,
  teams,
  onToast,
}: {
  title: string
  teams: MyTeam[]
  onToast: (message: string) => void
}) {
  return (
    <section aria-label={title}>
      {/* ② 칸 제목 옆에 팀 수 */}
      <h2 className="text-heading font-bold text-text">
        {title} <span className="text-primary">{teams.length}</span>
      </h2>
      <div className={`mt-4 ${GRID}`}>
        {teams.map((team) => (
          <TeamCard key={team.teamId} team={team} onToast={onToast} />
        ))}
      </div>
    </section>
  )
}

// WS-08 워크스페이스 팀 목록 (/workspaces). 로그인 확인은 라우터의 RequireLogin이 한다 (①)
export default function WorkspaceListPage() {
  const toast = useTimedToast()
  const { data, isLoading, isError } = useMyTeams()

  useEffect(() => {
    document.title = '워크스페이스 | 공방'
  }, [])

  const teams = data ?? []
  // ② 서버가 진행 중 → 종료, 각각 최근 활동순으로 보낸다
  const ongoing = teams.filter((team) => !team.isEnded)
  const ended = teams.filter((team) => team.isEnded)

  return (
    <div className="mx-auto w-full max-w-[1680px] px-4 py-10 sm:px-6 lg:px-12 xl:px-[120px]">
      <h1 className="text-title font-bold text-text">워크스페이스</h1>

      {isLoading ? (
        <div className={`mt-8 ${GRID}`}>
          {Array.from({ length: 3 }, (_, i) => (
            <div key={i} className="h-52 animate-pulse rounded-card bg-surface-sub" />
          ))}
        </div>
      ) : isError ? (
        // TODO(확인 필요): 목록을 못 불러올 때 문구가 기능정의서에 없어 다른 목록 화면과 같은 형식으로 둔다
        <p className="mt-10 text-center text-small text-text-sub">
          팀 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
      ) : teams.length === 0 ? (
        // ⑤ 참여한 팀이 없을 때
        <div className="mt-20 flex flex-col items-center gap-4 text-center">
          <p className="text-body text-text-sub">아직 참여한 팀이 없어요</p>
          <Link to="/posts">
            <Button variant="primary">팀원 모집 보러 가기</Button>
          </Link>
        </div>
      ) : (
        <div className="mt-8 flex flex-col gap-12">
          {/* TODO(확인 필요): 한쪽 칸이 0개일 때 칸을 보일지 문서에 없어 팀이 있는 칸만 보인다 */}
          {ongoing.length > 0 && (
            <TeamSection title="진행 중" teams={ongoing} onToast={toast.show} />
          )}
          {ended.length > 0 && <TeamSection title="종료" teams={ended} onToast={toast.show} />}
        </div>
      )}

      <Toast message={toast.message} />
    </div>
  )
}
