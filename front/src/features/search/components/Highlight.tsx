// SRCH-02 ③ 검색 하이라이팅: 글 안의 검색어를 굵게·primary 색으로 강조한다 (대소문자 무시).
// 공모전 카드와 (나중에) 모집글 검색 결과에서 같이 쓴다.
function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

interface HighlightProps {
  text: string | null
  keyword?: string
}

export function Highlight({ text, keyword }: HighlightProps) {
  if (!text) return null
  const trimmed = keyword?.trim()
  if (!trimmed) return <>{text}</>

  // 캡처 그룹으로 나누면 짝수 칸은 일반 글자, 홀수 칸은 검색어와 일치한 부분이다
  const parts = text.split(new RegExp(`(${escapeRegExp(trimmed)})`, 'gi'))
  return (
    <>
      {parts.map((part, index) =>
        index % 2 === 1 ? (
          <mark key={index} className="bg-transparent font-bold text-primary">
            {part}
          </mark>
        ) : (
          part
        ),
      )}
    </>
  )
}
