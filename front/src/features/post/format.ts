// PST-001 ② 작성일: 오늘이면 HH:mm, 아니면 MM.DD
// 서버는 한국 시간을 시간대 없이 「YYYY-MM-DDTHH:mm:ss」로 보낸다. 사용자 기기도 한국 시간이라는 전제로 오늘 날짜와 비교한다.
export function formatPostDate(createdAt: string, now: Date = new Date()) {
  const date = createdAt.slice(0, 10) // YYYY-MM-DD
  const today = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-')
  if (date === today) return createdAt.slice(11, 16) // HH:mm
  return `${date.slice(5, 7)}.${date.slice(8, 10)}` // MM.DD
}
