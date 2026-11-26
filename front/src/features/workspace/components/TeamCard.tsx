import { useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { getErrorMessage } from '../../../api/errors'
import { CrownIcon } from '../../../components/icons/Icons'
import { Avatar } from '../../../components/ui/Avatar'
import { Badge } from '../../../components/ui/Badge'
import { DEADLINE_DANGER_DAYS } from '../deadline'
import { useRenameTeam } from '../hooks'
import type { MyTeam } from '../types'
import { PencilIcon } from './WorkspaceIcons'

// 아바타는 최대 5개, 나머지는 「+N」 (WS-02 헤더와 같음)
const MAX_AVATARS = 5
// WS-08 ⑦ 팀 이름 1~20자 (서버 TEAM_NAME_INVALID와 같은 기준, 글자 수는 code point로 센다)
const TEAM_NAME_MAX_LENGTH = 20
// 서버 ErrorCode.TEAM_NAME_INVALID 문구 (화면에서 먼저 막을 때도 같은 문구)
const TEAM_NAME_INVALID_MESSAGE = '팀 이름은 1~20자로 입력해 주세요'

function isValidTeamName(name: string) {
  const length = Array.from(name).length
  return length >= 1 && length <= TEAM_NAME_MAX_LENGTH
}

// WS-08 ③ 「공모전 마감 D-N」 배지. D-7부터 danger-soft, 마감일이 지나면 「종료」, 마감일이 없으면 없음
// TODO(확인 필요): 마감 당일 표기는 WS-02 헤더(deadline.ts)와 같이 D-0으로 둔다
function TeamDeadlineBadge({ team }: { team: MyTeam }) {
  if (team.isEnded) return <Badge variant="surface-sub">종료</Badge>
  if (team.dDay === null) return null
  return (
    <Badge variant={team.dDay <= DEADLINE_DANGER_DAYS ? 'danger-soft' : 'surface-sub'}>
      공모전 마감 D-{team.dDay}
    </Badge>
  )
}

interface TeamCardProps {
  team: MyTeam
  // 이름 변경 실패 문구를 토스트로 띄우는 함수 (토스트는 페이지가 띄운다)
  onToast: (message: string) => void
  // WS-09 ① 카드 오른쪽 아래 「팀 나가기」 클릭
  onRequestLeave?: (team: MyTeam) => void
}

// WS-08 팀 카드. 카드 전체가 그 팀 워크스페이스로 가는 링크이고(④), 펜·입력칸은 링크 위에 따로 둔다(⑧)
export function TeamCard({ team, onToast, onRequestLeave }: TeamCardProps) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(team.name)
  const [error, setError] = useState<string | null>(null)
  // Esc로 취소한 뒤 따라오는 blur에서 저장하지 않게 한다
  const skipBlurSave = useRef(false)
  const rename = useRenameTeam()

  const shown = team.members.slice(0, MAX_AVATARS)
  const hiddenCount = team.memberCount - shown.length

  const startEdit = () => {
    setDraft(team.name)
    setError(null)
    skipBlurSave.current = false
    setEditing(true)
  }

  const cancelEdit = () => {
    skipBlurSave.current = true
    setEditing(false)
    setError(null)
  }

  // ⑥ Enter나 바깥 클릭으로 저장. ⑦ 공백만이거나 20자를 넘으면 저장을 막는다
  const save = () => {
    if (rename.isPending) return
    const name = draft.trim()
    if (!isValidTeamName(name)) {
      setError(TEAM_NAME_INVALID_MESSAGE)
      return
    }
    if (name === team.name) {
      setEditing(false)
      return
    }
    rename.mutate(
      { teamId: team.teamId, name },
      {
        onSuccess: () => setEditing(false),
        onError: (e) => onToast(getErrorMessage(e)),
      },
    )
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      save()
    } else if (e.key === 'Escape') {
      e.preventDefault()
      cancelEdit()
    }
  }

  const onBlur = () => {
    if (skipBlurSave.current) {
      skipBlurSave.current = false
      return
    }
    save()
  }

  return (
    <article
      className={[
        'relative flex flex-col rounded-card border border-line bg-surface p-5 transition-colors hover:border-line-strong',
        team.isEnded ? 'opacity-50' : '',
      ].join(' ')}
    >
      {/* ④ 카드를 누르면 채팅 탭으로 (/workspaces/:teamId 첫 화면이 채팅) */}
      <Link
        to={`/workspaces/${team.teamId}`}
        aria-label={`${team.name} 워크스페이스로 이동`}
        className="absolute inset-0 rounded-card focus-visible:outline-2 focus-visible:outline-primary"
      />

      <div className="flex min-h-6 items-start justify-between gap-2">
        <TeamDeadlineBadge team={team} />
      </div>

      <div className="mt-4 flex min-w-0 items-center gap-2">
        {team.isLeader && (
          <span className="shrink-0 text-warning" role="img" aria-label="내가 팀장인 팀">
            <CrownIcon className="h-5 w-5" />
          </span>
        )}
        {editing ? (
          <input
            // 펜을 눌렀을 때 바로 입력할 수 있게 한다
            autoFocus
            value={draft}
            onChange={(e) => {
              setDraft(e.target.value)
              setError(null)
            }}
            onKeyDown={onKeyDown}
            onBlur={onBlur}
            disabled={rename.isPending}
            aria-label="팀 이름"
            aria-invalid={error !== null}
            className={[
              'relative z-10 h-10 min-w-0 flex-1 rounded-input border bg-surface px-3 text-subheading font-bold text-text outline-none',
              error
                ? 'border-2 border-danger'
                : 'border-line-input focus:border-2 focus:border-primary',
            ].join(' ')}
          />
        ) : (
          <>
            <h3 className="truncate text-subheading font-bold text-text" title={team.name}>
              {team.name}
            </h3>
            {/* ⑥ 팀장에게만 펜. 다른 팀원에게는 보이지 않는다 (서버도 403) */}
            {team.isLeader && (
              <button
                type="button"
                onClick={startEdit}
                aria-label="팀 이름 수정"
                title="팀 이름 수정"
                className="relative z-10 flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-full text-text-sub transition-colors hover:bg-bg hover:text-text"
              >
                <PencilIcon />
              </button>
            )}
          </>
        )}
      </div>
      {editing && error && <p className="mt-2 text-small text-danger">{error}</p>}

      {/* ⑦ 공모전 이름은 바꿀 수 없다 */}
      <p className="mt-1 truncate text-small text-text-sub" title={team.contestTitle ?? undefined}>
        {team.contestTitle ?? ''}
      </p>

      <div className="mt-6 flex items-center justify-between gap-3 border-t border-divider pt-4">
        <ul className="flex items-center gap-1" aria-label="팀원">
          {shown.map((member) => (
            <li key={member.memberId}>
              <Avatar name={member.nickname} colorIndex={member.colorIndex} size="sm" />
            </li>
          ))}
          {hiddenCount > 0 && (
            <li
              className="flex h-8 min-w-8 items-center justify-center rounded-full bg-surface-sub px-2 text-caption text-text-sub"
              aria-label={`외 ${hiddenCount}명`}
            >
              +{hiddenCount}
            </li>
          )}
        </ul>
        <span className="shrink-0 text-small text-text-sub">팀원 {team.memberCount}명</span>
      </div>

      {/* WS-09 ① 진행 중·종료 모든 카드 오른쪽 아래 「팀 나가기」 (링크 위에 두어 카드 입장과 분리) */}
      {onRequestLeave && (
        <div className="mt-auto flex justify-end pt-4">
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation()
              onRequestLeave(team)
            }}
            className="relative z-10 inline-flex h-10 cursor-pointer items-center justify-center rounded-full border border-line-strong bg-surface px-4 text-small font-medium text-text-sub transition-colors hover:border-danger-line hover:bg-bg hover:text-danger"
          >
            팀 나가기
          </button>
        </div>
      )}
    </article>
  )
}
