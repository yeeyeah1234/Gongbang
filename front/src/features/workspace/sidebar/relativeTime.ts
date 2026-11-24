import { useEffect, useState } from 'react'

// 서버는 KST 시각을 시간대 없이 「YYYY-MM-DDTHH:mm:ss」로 보낸다 (AGENTS 2-4)
const KST_OFFSET_MS = 9 * 60 * 60 * 1000
const MINUTE_MS = 60 * 1000
const HOUR_MS = 60 * MINUTE_MS
const DAY_MS = 24 * HOUR_MS

function parseKstDateTime(dateTime: string): number {
  const hasZone = /(?:Z|[+-]\d{2}:\d{2})$/.test(dateTime)
  return Date.parse(hasZone ? dateTime : `${dateTime}+09:00`)
}

function kstDateString(ms: number): string {
  return new Date(ms + KST_OFFSET_MS).toISOString().slice(0, 10)
}

/**
 * DOC-01·WB-01 상대 시각:
 * 1분 미만 「방금 전」, 1시간 미만 「N분 전」, 24시간 미만 「N시간 전」,
 * 어제 「어제」, 그 이전 「MM월 DD일」
 */
export function formatRelativeTime(dateTime: string, now = new Date()): string {
  const updatedMs = parseKstDateTime(dateTime)
  if (!Number.isFinite(updatedMs)) {
    const month = dateTime.slice(5, 7)
    const day = dateTime.slice(8, 10)
    return month && day ? `${month}월 ${day}일` : dateTime
  }

  const diffMs = now.getTime() - updatedMs
  if (diffMs < MINUTE_MS) return '방금 전'
  if (diffMs < HOUR_MS) return `${Math.floor(diffMs / MINUTE_MS)}분 전`
  if (diffMs < DAY_MS) return `${Math.floor(diffMs / HOUR_MS)}시간 전`

  const targetDate = kstDateString(updatedMs)
  const yesterdayDate = kstDateString(now.getTime() - DAY_MS)
  if (targetDate === yesterdayDate) return '어제'

  return `${targetDate.slice(5, 7)}월 ${targetDate.slice(8, 10)}일`
}

// 지금(KST) LocalDateTime 문자열 (낙관적 생성 항목 등에 사용)
export function nowKstDateTime(now = new Date()): string {
  return new Date(now.getTime() + KST_OFFSET_MS).toISOString().slice(0, 19)
}

// 화면이 열려 있는 동안 상대 시각(방금 전 → N분 전)을 1분마다 갱신한다
export function useMinuteTick(): Date {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), MINUTE_MS)
    return () => window.clearInterval(timer)
  }, [])
  return now
}
