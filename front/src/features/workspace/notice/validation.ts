// 서버(NoticeService.requireText)와 같게: Java strip() 뒤 코드포인트로 센다
export const TITLE_MAX = 50
export const CONTENT_MAX = 2000

// Java Character.isWhitespace. JS trim()은 NBSP까지 지워 서버와 어긋난다
function isWhitespace(char: string) {
  const code = char.charCodeAt(0)
  if ((code >= 0x09 && code <= 0x0d) || (code >= 0x1c && code <= 0x1f)) return true
  return /\p{Z}/u.test(char) && !'   '.includes(char)
}

// 정규식(/\s+$/)은 긴 공백을 붙여 넣으면 느려져서 양끝에서 훑는다
export function strip(value: string) {
  let start = 0
  let end = value.length
  while (start < end && isWhitespace(value[start])) start++
  while (end > start && isWhitespace(value[end - 1])) end--
  return value.slice(start, end)
}

export function textLength(value: string) {
  return Array.from(strip(value)).length
}

export function isValidLength(value: string, max: number) {
  const length = textLength(value)
  return length >= 1 && length <= max
}
