/**
 * AGENTS.md 9 / DOC-06: 사용자 입력 링크는 http:// 또는 https://만 허용하고
 * javascript:, data: 등 위험한 프로토콜을 차단한다.
 */
export function isSafeHttpUrl(raw: string): boolean {
  const trimmed = raw.trim()
  if (!/^https?:\/\//i.test(trimmed)) return false
  try {
    const parsed = new URL(trimmed)
    return (
      (parsed.protocol === 'http:' || parsed.protocol === 'https:') && parsed.hostname.length > 0
    )
  } catch {
    return false
  }
}

export function normalizeSafeHttpUrl(raw: string): string | null {
  const trimmed = raw.trim()
  if (!isSafeHttpUrl(trimmed)) return null
  return trimmed
}
