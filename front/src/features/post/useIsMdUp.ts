import { useSyncExternalStore } from 'react'

// STYLE_GUIDE 8-1 md(768px). 이보다 좁으면 휴대폰 화면으로 본다
const MD_QUERY = '(min-width: 768px)'

function subscribe(onChange: () => void) {
  const query = window.matchMedia(MD_QUERY)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

/**
 * 화면 너비가 768px 이상인지. 휴대폰에서 버튼을 화면 아래 고정 줄로 옮길 때 쓴다.
 * 버튼이 여는 모달까지 두 번 그려지지 않도록 CSS로 숨기지 않고 한 곳에만 그린다 (STYLE_GUIDE 8-4)
 */
export function useIsMdUp() {
  return useSyncExternalStore(subscribe, () => window.matchMedia(MD_QUERY).matches)
}
