import type { MemberColorIndex } from '../../components/ui/Avatar'
import type { WorkspaceOnlineMember } from './types'

// 접속자 목록에 색 번호가 없을 때 쓰는 기본 색 (memberId로 고정, 1~8 순환)
// 임시: 명세 WS-03 ③은 팀마다 입장 순서대로 8색 배정. WS-03에서 서버 값으로 교체
export function memberColorIndex(memberId: number): MemberColorIndex {
  const n = Math.abs(Math.trunc(memberId)) || 1
  return (((n - 1) % 8) + 1) as MemberColorIndex
}

// 헤더 「접속 중」 순서: 나 맨 앞 → 나머지. memberId가 겹치면 처음 것만 남긴다.
// 목록에 내가 있으면 그 colorIndex를 쓴다 (닉네임·팀장 여부는 self 값).
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
  const me = selfFromList ? { ...self, colorIndex: selfFromList.colorIndex } : self
  return [me, ...rest]
}
