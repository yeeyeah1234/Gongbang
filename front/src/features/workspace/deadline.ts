// 「마감 D-N」 배지 (WS-02 헤더). D-7부터 danger 강조, 마감일이 지나면 「종료」.
// TODO(확인 필요): 마감 당일 표기(D-0 / D-DAY)는 정하지 않아 D-0으로 둔다. 날짜 기준은 브라우저 날짜.
export const DEADLINE_DANGER_DAYS = 7

export type DeadlineBadge =
  { label: string; tone: 'danger' | 'default' } | { label: '종료'; tone: 'ended' }

const DAY_MS = 24 * 60 * 60 * 1000

// 'YYYY-MM-DD' → 그 날짜의 UTC 자정 (시간대와 서머타임 영향 없이 날짜 차이만 센다)
function toUtcDay(date: string) {
  const [y, m, d] = date.split('-').map(Number)
  return Date.UTC(y, m - 1, d)
}

export function getDeadlineBadge(
  deadline: string | null,
  today = new Date(),
): DeadlineBadge | null {
  if (!deadline) return null
  const todayUtc = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())
  const days = Math.round((toUtcDay(deadline) - todayUtc) / DAY_MS)
  if (Number.isNaN(days)) return null
  if (days < 0) return { label: '종료', tone: 'ended' }
  return { label: `마감 D-${days}`, tone: days <= DEADLINE_DANGER_DAYS ? 'danger' : 'default' }
}
