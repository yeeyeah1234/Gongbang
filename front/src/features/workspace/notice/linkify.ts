// http(s) 주소만 링크로 만든다 (AGENTS 9). 한글 앞에서 끊어 「https://a.com에서」의 조사가 붙지 않게 한다
// (한글이 그대로 든 주소는 그 앞까지만 링크가 된다).
const URL_PATTERN = /https?:\/\/[^\s<>"'`‘’“”\u3000-\u303f\p{Script=Hangul}]+/giu
const OPENING: Record<string, string> = { ')': '(', ']': '[', '}': '{' }

// 끝 문장부호와 짝 없는 닫는 괄호는 주소에서 뺀다
function trimUrlEnd(url: string) {
  let result = url
  while (result) {
    const last = result[result.length - 1]
    const opening = OPENING[last]
    const unmatched =
      opening !== undefined && result.split(opening).length < result.split(last).length
    if (!unmatched && !'.,;:!?…'.includes(last)) break
    result = result.slice(0, -1)
  }
  return result
}

export interface TextPart {
  text: string
  href?: string
}

export function linkify(content: string): TextPart[] {
  const parts: TextPart[] = []
  let end = 0
  for (const match of content.matchAll(URL_PATTERN)) {
    const url = trimUrlEnd(match[0])
    if (!/^https?:\/\/[^/?#]/i.test(url)) continue
    if (match.index > end) parts.push({ text: content.slice(end, match.index) })
    parts.push({ text: url, href: url })
    end = match.index + url.length
  }
  if (end < content.length) parts.push({ text: content.slice(end) })
  return parts
}
