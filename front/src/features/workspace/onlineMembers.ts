import type { MemberColorIndex } from '../../components/ui/Avatar'
import { WORKSPACE_TABS, type WorkspaceOnlineMember, type WorkspaceTabKey } from './types'

const VALID_COLOR_INDICES = new Set<number>([1, 2, 3, 4, 5, 6, 7, 8])
const TAB_LABELS = new Map<WorkspaceTabKey, string>(WORKSPACE_TABS.map((t) => [t.key, t.label]))

// 접속자 목록에 색 번호가 없을 때 쓰는 기본 색 (memberId로 고정, 1~8 순환)
export function memberColorIndex(memberId: number): MemberColorIndex {
  const n = Math.abs(Math.trunc(memberId)) || 1
  return (((n - 1) % 8) + 1) as MemberColorIndex
}

export function toMemberColorIndex(value: unknown, fallbackMemberId: number): MemberColorIndex {
  if (typeof value === 'number' && VALID_COLOR_INDICES.has(value)) {
    return value as MemberColorIndex
  }
  return memberColorIndex(fallbackMemberId)
}

function toTabKey(value: unknown): WorkspaceTabKey | null {
  if (typeof value === 'string' && TAB_LABELS.has(value as WorkspaceTabKey)) {
    return value as WorkspaceTabKey
  }
  return null
}

function toDocTitle(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

interface AwarenessCandidate {
  member: WorkspaceOnlineMember
  updatedAt: number
}

/**
 * WS-03: Hocuspocus awareness 상태 맵에서 접속 중인 팀원 목록을 뽑고 memberId 기준으로 중복을 제거한다.
 * 같은 사람이 탭을 여러 개 열어도 아바타는 1개만 표시하며, 가장 최근에 탭·문서를 바꾼 상태를 대표로 쓴다.
 */
export function extractOnlineMembers(
  states: Map<number, Record<string, unknown>>,
): WorkspaceOnlineMember[] {
  const byMemberId = new Map<number, AwarenessCandidate>()

  states.forEach((raw) => {
    if (!raw || typeof raw !== 'object') return
    const memberId = raw.memberId
    const nickname = raw.nickname
    if (
      typeof memberId !== 'number' ||
      !Number.isSafeInteger(memberId) ||
      memberId <= 0 ||
      typeof nickname !== 'string' ||
      nickname.trim().length === 0
    ) {
      return
    }

    const tab = toTabKey(raw.tab)
    const docTitle = tab === 'docs' ? toDocTitle(raw.docTitle) : null
    const updatedAt =
      typeof raw.updatedAt === 'number' && Number.isFinite(raw.updatedAt) ? raw.updatedAt : 0

    const candidate: AwarenessCandidate = {
      member: {
        memberId,
        nickname,
        colorIndex: toMemberColorIndex(raw.colorIndex, memberId),
        isLeader: raw.leader === true,
        tab,
        docTitle,
      },
      updatedAt,
    }

    const existing = byMemberId.get(memberId)
    if (!existing) {
      byMemberId.set(memberId, candidate)
      return
    }

    const shouldReplace =
      candidate.updatedAt > existing.updatedAt ||
      (candidate.updatedAt === existing.updatedAt &&
        candidate.member.docTitle !== null &&
        existing.member.docTitle === null)
    if (shouldReplace) {
      byMemberId.set(memberId, candidate)
    }
  })

  return Array.from(byMemberId.values(), (entry) => entry.member)
}

/**
 * 헤더 「접속 중」 순서: 나 맨 앞 → 나머지. memberId가 겹치면 처음 것만 남긴다.
 * 목록에 내가 있으면 colorIndex와 빠진 위치 정보를 합친다 (닉네임·팀장 여부는 self 값 우선).
 */
export function withSelfFirst(
  self: WorkspaceOnlineMember | null,
  others: WorkspaceOnlineMember[],
): WorkspaceOnlineMember[] {
  const seen = new Set<number>()
  const rest: WorkspaceOnlineMember[] = []
  let selfFromList: WorkspaceOnlineMember | undefined

  for (const member of others) {
    if (self && member.memberId === self.memberId) {
      selfFromList ??= member
      continue
    }
    if (seen.has(member.memberId)) continue
    seen.add(member.memberId)
    rest.push(member)
  }

  if (!self) return rest
  const me: WorkspaceOnlineMember = selfFromList
    ? {
        ...self,
        colorIndex: selfFromList.colorIndex,
        tab: self.tab !== undefined ? self.tab : selfFromList.tab,
        docTitle: self.docTitle !== undefined ? self.docTitle : selfFromList.docTitle,
      }
    : self
  return [me, ...rest]
}

/** WS-03 ④ 보고 있는 탭·문서 이름 포맷 (예: "문서 > 기획서", "채팅"). */
export function formatPresenceLocation(
  tab?: WorkspaceTabKey | null,
  docTitle?: string | null,
): string | null {
  if (!tab) return null
  const label = TAB_LABELS.get(tab)
  if (!label) return null
  const trimmedTitle = docTitle?.trim()
  if (tab === 'docs' && trimmedTitle) {
    return `${label} > ${trimmedTitle}`
  }
  return label
}

/**
 * WS-03 ④ 아바타 툴팁 문구:
 * - 일반 팀원: "최규선 · 문서 > 기획서"
 * - 팀장: "[팀장] 김하늘 · 문서 > 기획서"
 * - 나: "(나) 이바다 · 채팅" / "(나) [팀장] 김하늘 · 문서 > 기획서"
 */
export function formatPresenceTooltip(member: WorkspaceOnlineMember, isSelf = false): string {
  const baseName = member.isLeader ? `[팀장] ${member.nickname}` : member.nickname
  const namePart = isSelf ? `(나) ${baseName}` : baseName
  const location = formatPresenceLocation(member.tab, member.docTitle)
  return location ? `${namePart} · ${location}` : namePart
}
