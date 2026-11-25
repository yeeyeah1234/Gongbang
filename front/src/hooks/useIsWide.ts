import { useSyncExternalStore } from 'react'
const query = '(min-width: 1024px)'
function subscribe(listener: () => void) {
  const media = window.matchMedia(query)
  media.addEventListener('change', listener)
  return () => media.removeEventListener('change', listener)
}
/** STYLE_GUIDE 8: 좁은 화면의 숨은 상세는 렌더링하지 않는다. */
export function useIsWide() {
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(query).matches,
    () => false,
  )
}
