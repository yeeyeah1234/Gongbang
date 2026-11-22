// 서버는 한국 시각을 시간대 없이 「YYYY-MM-DDTHH:mm:ss」로 보낸다 (AGENTS 2-4).
// TODO(확인 필요): created_at은 DB CURRENT_TIMESTAMP라 운영 DB 세션 시간대가 KST인지 확인
const KST_OFFSET_MS = 9 * 60 * 60 * 1000

function monthDay(dateTime: string) {
  return `${Number(dateTime.slice(5, 7))}월 ${Number(dateTime.slice(8, 10))}일`
}

// 목록: 오늘(KST)이면 「21:10」, 아니면 「10월 20일」
export function formatNoticeDate(dateTime: string, now = new Date()) {
  const today = new Date(now.getTime() + KST_OFFSET_MS).toISOString().slice(0, 10)
  return dateTime.slice(0, 10) === today ? dateTime.slice(11, 16) : monthDay(dateTime)
}

// 상세: 「2026년 10월 20일 21:10」
export function formatNoticeDateTime(dateTime: string) {
  return `${dateTime.slice(0, 4)}년 ${monthDay(dateTime)} ${dateTime.slice(11, 16)}`
}
