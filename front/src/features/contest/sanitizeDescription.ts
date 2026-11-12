import DOMPurify from 'dompurify'

// 크롤링한 공모전 본문(외부 사이트 HTML)을 화면에 넣기 전에 정리한다.
// - 스크립트·이벤트 속성 등 위험한 것은 DOMPurify가 지운다
// - 원본의 인라인 style은 우리 화면 스타일을 깨뜨려서 지운다
// - 위비티 상대 경로(/upload/...) 이미지·링크는 원본 사이트 주소로 바꾼다
// - 링크는 새 탭으로 열고 opener를 넘기지 않는다
export function sanitizeDescription(html: string, sourceUrl: string) {
  const origin = new URL(sourceUrl).origin

  const toAbsolute = (node: Element, attr: string) => {
    const value = node.getAttribute(attr)
    if (value?.startsWith('/') && !value.startsWith('//')) {
      node.setAttribute(attr, origin + value)
    }
  }

  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'IMG') toAbsolute(node, 'src')
    if (node.tagName === 'A') {
      toAbsolute(node, 'href')
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer')
    }
  })
  try {
    return DOMPurify.sanitize(html, { FORBID_ATTR: ['style'], ADD_ATTR: ['target'] })
  } finally {
    DOMPurify.removeHook('afterSanitizeAttributes')
  }
}
