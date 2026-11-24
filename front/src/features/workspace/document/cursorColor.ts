import type { MemberColorIndex } from '../../../components/ui/Avatar'
import { memberColorIndex } from '../onlineMembers'

export interface CursorColorInput {
  memberId: number
  /**
   * TODO(확인 필요): WS-03 입장 순서 색이 붙으면 서버/접속 목록에서 받은 1~8 번호를 여기로 넘긴다.
   * 비어 있으면 현재는 memberId 기반 8색(memberColorIndex)을 사용한다.
   */
  colorIndex?: MemberColorIndex | null
}

export interface CursorColor {
  colorIndex: MemberColorIndex
  /** theme.css 디자인 토큰 변수 (예: var(--color-member-1)) */
  cssVar: string
}

const VALID_COLOR_INDICES = new Set<number>([1, 2, 3, 4, 5, 6, 7, 8])

export function isValidMemberColorIndex(value: unknown): value is MemberColorIndex {
  return typeof value === 'number' && VALID_COLOR_INDICES.has(value)
}

/**
 * DOC-09 커서·선택 영역에 쓸 팀원 색을 한 곳에서 결정한다.
 * 나중에 WS-03 입장 순서 색으로 바꿀 때 colorIndex만 전달하거나 이 함수만 교체하면 된다.
 */
export function resolveCursorColor({ memberId, colorIndex }: CursorColorInput): CursorColor {
  const resolved = isValidMemberColorIndex(colorIndex) ? colorIndex : memberColorIndex(memberId)
  return {
    colorIndex: resolved,
    cssVar: `var(--color-member-${resolved})`,
  }
}
